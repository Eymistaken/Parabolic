"""YouTube JS challenge solver backed by Apple's JavaScriptCore.

yt-dlp needs a JavaScript runtime to solve YouTube's "n" and signature
challenges. On desktop that is Deno, QuickJS and friends, all started as
subprocesses, which iOS forbids. This provider reuses yt-dlp's own EJS solver
scripts and protocol, but evaluates the program in-process: the C shim hands it
to JavaScriptCore on a large-stack thread and returns what ``console.log``
printed.
"""

import json

import _parabolic

from yt_dlp.extractor.youtube.jsc.provider import (
    JsChallengeProviderError,
    register_preference,
    register_provider,
)

try:
    # Private API, but it carries the solver scripts, their hash checks and the
    # stdin/stdout protocol; re-implementing those would only drift from upstream.
    from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP
except ImportError:  # pragma: no cover - layout changed upstream
    EJSBaseJCP = None


if EJSBaseJCP is not None:

    @register_provider
    class JavaScriptCoreJCP(EJSBaseJCP):
        PROVIDER_VERSION = '1.0.0'
        JS_RUNTIME_NAME = 'javascriptcore'
        BUG_REPORT_LOCATION = 'https://github.com/Eymistaken/Parabolic/issues'

        def is_available(self, /) -> bool:
            # JavaScriptCore ships with iOS; only missing solver scripts can
            # make this provider unusable, and the base class tracks that.
            return getattr(self, '_available', True)

        def _run_js_runtime(self, stdin: str, /) -> str:
            raw = _parabolic.eval_js(stdin)
            try:
                result = json.loads(raw)
            except ValueError as error:
                raise JsChallengeProviderError(f'JavaScriptCore returned invalid output: {error}') from error
            if not result.get('ok'):
                raise JsChallengeProviderError(f"JavaScriptCore error: {result.get('error') or 'unknown'}")
            return result.get('stdout') or ''

    @register_preference(JavaScriptCoreJCP)
    def _javascriptcore_preference(provider, requests) -> int:
        return 900
