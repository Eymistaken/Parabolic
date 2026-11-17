"""Bridge between the Swift app and yt-dlp.

iOS does not allow an app to start subprocesses, so yt-dlp runs inside the
app's own embedded CPython interpreter. Swift talks to this module through a
single entry point, ``handle(request_json) -> response_json``, which is called
from background threads (the C shim takes the GIL for every call).

Progress and log lines travel the other way through ``_parabolic.emit``, a
built-in module the C shim registers before the interpreter starts.

ffmpeg cannot run on iOS either. Instead of letting yt-dlp merge or convert
anything, every selected format is downloaded as a separate file and the app
muxes, trims and tags them with AVFoundation afterwards.
"""

import io
import json
import os
import re
import shutil
import ssl
import sys
import threading
import time
import traceback
import urllib.request
import zipfile

import _parabolic

DATA_DIR = os.environ.get('PARABOLIC_DATA_DIR') or os.path.expanduser('~/Library/Application Support/Engine')
CACHE_DIR = os.environ.get('PARABOLIC_CACHE_DIR') or os.path.expanduser('~/Library/Caches/Engine')
YTDLP_DIR = os.path.join(DATA_DIR, 'yt-dlp')
UPDATED_YTDLP = os.path.join(YTDLP_DIR, 'current')

UPDATE_REPOSITORIES = {
    'stable': 'yt-dlp/yt-dlp',
    'nightly': 'yt-dlp/yt-dlp-nightly-builds',
    'master': 'yt-dlp/yt-dlp-master-builds',
}

_import_lock = threading.Lock()
_ytdlp = None
_ytdlp_source = None

_cancel_lock = threading.Lock()
_cancelled = set()


class Cancelled(Exception):
    pass


# -- yt-dlp loading -----------------------------------------------------------

def _purge_ytdlp_modules():
    for name in list(sys.modules):
        if name in ('yt_dlp', 'yt_dlp_ejs') or name.startswith(('yt_dlp.', 'yt_dlp_ejs.')):
            del sys.modules[name]


def _load_ytdlp():
    """Imports yt-dlp once: the self-updated copy if it works, else the bundled one."""
    global _ytdlp, _ytdlp_source
    with _import_lock:
        if _ytdlp is not None:
            return _ytdlp
        source = 'bundled'
        if os.path.isfile(os.path.join(UPDATED_YTDLP, 'yt_dlp', '__init__.py')):
            sys.path.insert(0, UPDATED_YTDLP)
            try:
                import yt_dlp  # noqa: F401
                source = 'updated'
            except Exception:
                sys.path.remove(UPDATED_YTDLP)
                _purge_ytdlp_modules()
        import yt_dlp

        # Registers the JavaScriptCore challenge solver with yt-dlp.
        import parabolic_jsc  # noqa: F401

        _ytdlp = yt_dlp
        _ytdlp_source = source
        return yt_dlp


def _version_on_disk(root):
    try:
        with open(os.path.join(root, 'yt_dlp', 'version.py'), encoding='utf-8') as f:
            match = re.search(r"^__version__\s*=\s*'([^']+)'", f.read(), re.M)
            return match.group(1) if match else None
    except OSError:
        return None


def _bundled_root():
    for entry in sys.path:
        if entry != UPDATED_YTDLP and os.path.isfile(os.path.join(entry, 'yt_dlp', 'version.py')):
            return entry
    return None


# -- plumbing -------------------------------------------------------------------

def _emit(event):
    try:
        _parabolic.emit(json.dumps(event, ensure_ascii=False))
    except Exception:
        pass


def _check_cancelled(task_id):
    with _cancel_lock:
        if task_id in _cancelled:
            raise Cancelled()


class _Logger:
    """Collects yt-dlp output; forwards the meaningful lines to the app."""

    def __init__(self, task_id):
        self.task_id = task_id
        self.warnings = []
        self.errors = []

    def _line(self, message):
        if self.task_id:
            _emit({'type': 'log', 'id': self.task_id, 'line': message})

    def debug(self, message):
        if message.startswith('[debug] '):
            return
        self._line(message)

    def info(self, message):
        self._line(message)

    def warning(self, message):
        self.warnings.append(message)
        self._line(f'WARNING: {message}')

    def error(self, message):
        self.errors.append(message)
        self._line(message)


def _base_params(logger):
    os.makedirs(CACHE_DIR, exist_ok=True)
    return {
        'logger': logger,
        'quiet': False,
        'no_warnings': False,
        'noprogress': True,
        'cachedir': CACHE_DIR,
        'socket_timeout': 30,
        'retries': 10,
        'fragment_retries': 10,
        'extractor_retries': 3,
        'updatetime': False,
        'fixup': 'never',
        'postprocessors': [],
        'color': {'stdout': 'no_color', 'stderr': 'no_color'},
    }


def _clean_error(message):
    text = str(message or '').strip()
    text = re.sub(r'^ERROR:\s*', '', text)
    text = re.sub(r'\s*;\s*please report this issue.*$', '', text, flags=re.S | re.I)
    return text or 'yt-dlp bir hata bildirdi'


def _thumbnail_of(info):
    if not info:
        return None
    if info.get('thumbnail'):
        return info['thumbnail']
    thumbnails = info.get('thumbnails') or []
    for thumb in reversed(thumbnails):
        if thumb.get('url'):
            return thumb['url']
    return None


# -- operations ----------------------------------------------------------------

def op_version(_request):
    loaded = None
    if _ytdlp is not None:
        loaded = _ytdlp.version.__version__
    bundled_root = _bundled_root()
    return {
        'loaded': loaded,
        'source': _ytdlp_source,
        'bundled': _version_on_disk(bundled_root) if bundled_root else None,
        'updated': _version_on_disk(UPDATED_YTDLP),
        'python': sys.version.split()[0],
    }


def op_probe(request):
    yt_dlp = _load_ytdlp()
    url = request['url']
    logger = _Logger(None)
    params = _base_params(logger)
    params.update({
        'skip_download': True,
        'noplaylist': not request.get('playlist', False),
        'extract_flat': 'in_playlist',
        'ignore_no_formats_error': True,
        'format': 'bv*+ba/b',
    })
    with yt_dlp.YoutubeDL(params) as ydl:
        info = ydl.extract_info(url, download=False)
        info = ydl.sanitize_info(info)

    if not info:
        raise RuntimeError(logger.errors[-1] if logger.errors else 'Bu bağlantıdan bilgi alınamadı')

    kind = info.get('_type') or 'video'
    is_playlist = kind in ('playlist', 'multi_video')
    entries = []
    if is_playlist:
        for entry in info.get('entries') or []:
            if not entry:
                continue
            entry_url = entry.get('webpage_url') or entry.get('url')
            if not entry_url:
                continue
            entries.append({
                'url': entry_url,
                'title': entry.get('title'),
                'duration': entry.get('duration'),
                'thumbnail': _thumbnail_of(entry),
            })

    heights = sorted({
        int(f['height']) for f in (info.get('formats') or [])
        if f.get('height') and (f.get('vcodec') or 'none') != 'none'
    }, reverse=True)

    has_video = any((f.get('vcodec') or 'none') != 'none' for f in (info.get('formats') or [])) or (
        info.get('vcodec') not in (None, 'none'))

    title = info.get('title') or info.get('fulltitle')
    if not title and entries:
        title = entries[0].get('title')

    thumbnail = _thumbnail_of(info)
    if not thumbnail and entries:
        thumbnail = entries[0].get('thumbnail')

    return {
        'id': info.get('id'),
        'title': title or 'Başlıksız',
        'uploader': info.get('uploader') or info.get('channel') or info.get('uploader_id'),
        'thumbnail': thumbnail,
        'duration': info.get('duration'),
        'is_playlist': is_playlist,
        'playlist_count': info.get('playlist_count') or len(entries),
        'entries': entries,
        'heights': heights,
        'has_video': bool(has_video) or is_playlist,
        'extractor': info.get('extractor_key') or info.get('extractor'),
        'webpage_url': info.get('webpage_url') or url,
    }


def _role_of(fmt):
    has_video = (fmt.get('vcodec') or 'none') != 'none'
    has_audio = (fmt.get('acodec') or 'none') != 'none'
    if has_video and has_audio:
        return 'muxed'
    if has_video:
        return 'video'
    if has_audio:
        return 'audio'
    # Unknown codecs (common outside YouTube): assume a complete file.
    return 'muxed'


def op_download(request):
    yt_dlp = _load_ytdlp()
    task_id = request['id']
    url = request['url']
    outdir = request['outdir']
    os.makedirs(outdir, exist_ok=True)

    logger = _Logger(task_id)
    current_hook = [None]

    def progress_hook(status):
        _check_cancelled(task_id)
        if current_hook[0] is not None:
            current_hook[0](status)

    params = _base_params(logger)
    params.update({
        'noplaylist': True,
        'format': request.get('format') or 'b',
        'concurrent_fragment_downloads': max(1, min(16, int(request.get('fragments') or 4))),
        'paths': {'home': outdir, 'temp': outdir},
        'outtmpl': {'default': '%(id)s.%(ext)s'},
        'progress_hooks': [progress_hook],
    })

    _check_cancelled(task_id)
    # One YoutubeDL instance for extraction and every download: YouTube ties
    # format URLs to the cookies set while extracting, so a fresh instance
    # would be answered with HTTP 403.
    with yt_dlp.YoutubeDL(params) as ydl:
        # Extract once without processing, then process copies of that raw
        # result: first to learn which formats the selector picks, then once per
        # format to download it. Re-processing an already processed result would
        # carry the previous selection (requested_formats) along and fail.
        raw = ydl.extract_info(url, download=False, process=False)
        if not raw:
            raise RuntimeError(logger.errors[-1] if logger.errors else 'Bu bağlantıdan bilgi alınamadı')
        ydl.format_selector = ydl.build_format_selector(request.get('format') or 'b')
        info = ydl.process_ie_result(dict(raw), download=False)
        if info and info.get('_type') in ('playlist', 'multi_video'):
            entries = [e for e in (info.get('entries') or []) if e]
            if not entries:
                raise RuntimeError('Oynatma listesi boş')
            info = entries[0]
            raw = info
        if not info:
            raise RuntimeError(logger.errors[-1] if logger.errors else 'Bu bağlantıdan bilgi alınamadı')

        selected = info.get('requested_formats') or [info]
        parts = []
        total_parts = len(selected)
        subtitles = []
        subtitle_langs = [s.strip() for s in str(request.get('subtitle_langs') or 'tr,en').split(',') if s.strip()]

        for index, fmt in enumerate(selected):
            _check_cancelled(task_id)
            format_id = fmt.get('format_id')
            role = _role_of(fmt)
            last_emit = [0.0]

            def hook(status, index=index, role=role, last_emit=last_emit):
                state = status.get('status')
                now = time.monotonic()
                if state == 'downloading' and now - last_emit[0] < 0.2:
                    return
                last_emit[0] = now
                _emit({
                    'type': 'progress',
                    'id': task_id,
                    'status': state,
                    'part': index,
                    'parts': total_parts,
                    'role': role,
                    'downloaded': status.get('downloaded_bytes'),
                    'total': status.get('total_bytes') or status.get('total_bytes_estimate'),
                    'speed': status.get('speed'),
                    'eta': status.get('eta'),
                })

            # Each selected format is downloaded on its own; AVFoundation muxes them later.
            ydl.format_selector = ydl.build_format_selector(format_id or 'b')
            ydl.params['outtmpl']['default'] = f'%(id)s.part{index}.%(ext)s'
            wants_subtitles = index == 0 and bool(request.get('subtitles'))
            ydl.params['writesubtitles'] = wants_subtitles
            ydl.params['writeautomaticsub'] = wants_subtitles
            if wants_subtitles:
                ydl.params['subtitleslangs'] = subtitle_langs
                ydl.params['subtitlesformat'] = 'vtt/best'
            current_hook[0] = hook

            before = set(os.listdir(outdir))
            # A shallow copy is enough: processing only adds top-level keys, and
            # deep-copying would choke on lazily generated fragment lists.
            part_info = dict(raw)
            # Never let an earlier selection ride along (multi-video posts hand
            # us an already processed entry).
            part_info.pop('requested_formats', None)
            part_info.pop('requested_downloads', None)
            result = ydl.process_ie_result(part_info, download=True)
            current_hook[0] = None

            path = None
            for download in (result or {}).get('requested_downloads') or []:
                candidate = download.get('filepath') or download.get('_filename')
                if candidate and os.path.isfile(candidate):
                    path = candidate
            produced = sorted(set(os.listdir(outdir)) - before)
            if index == 0:
                for name in produced:
                    if name.endswith(('.vtt', '.srt', '.ass', '.ttml', '.srv3', '.json3')):
                        subtitles.append(os.path.join(outdir, name))
            if not path:
                media = [n for n in produced if f'.part{index}.' in n and not n.endswith(('.part', '.ytdl'))
                         and os.path.join(outdir, n) not in subtitles]
                if media:
                    path = os.path.join(outdir, media[0])
            if not path:
                raise RuntimeError(logger.errors[-1] if logger.errors else 'İndirilen dosya bulunamadı')

            parts.append({
                'path': path,
                'role': role,
                'ext': os.path.splitext(path)[1].lstrip('.').lower(),
                'vcodec': fmt.get('vcodec'),
                'acodec': fmt.get('acodec'),
                'height': fmt.get('height'),
            })

    return {
        'parts': parts,
        'subtitles': subtitles,
        'id': info.get('id'),
        'title': info.get('title') or info.get('fulltitle') or 'Başlıksız',
        'uploader': info.get('uploader') or info.get('channel') or info.get('artist'),
        'album': info.get('album'),
        'extractor': info.get('extractor_key') or info.get('extractor'),
        'thumbnail': _thumbnail_of(info),
        'duration': info.get('duration'),
        'webpage_url': info.get('webpage_url') or url,
        'upload_date': info.get('upload_date'),
        'description': (info.get('description') or '')[:2000] or None,
        'warnings': logger.warnings[-20:],
    }


def op_cancel(request):
    with _cancel_lock:
        _cancelled.add(request['id'])
    return {}


def op_forget(request):
    with _cancel_lock:
        _cancelled.discard(request['id'])
    return {}


def _ssl_context():
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        return ssl.create_default_context()


def _http_get(url, timeout=60):
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Parabolic-iOS',
        'Accept': 'application/vnd.github+json' if 'api.github.com' in url else '*/*',
    })
    with urllib.request.urlopen(req, context=_ssl_context(), timeout=timeout) as response:
        return response.read()


def op_update(request):
    channel = request.get('channel') or 'nightly'
    repository = UPDATE_REPOSITORIES.get(channel, UPDATE_REPOSITORIES['nightly'])
    release = json.loads(_http_get(f'https://api.github.com/repos/{repository}/releases/latest', timeout=30))
    tag = release.get('tag_name')
    if not tag:
        raise RuntimeError('Sürüm bilgisi alınamadı')

    bundled_root = _bundled_root()
    on_disk = _version_on_disk(UPDATED_YTDLP) or (_version_on_disk(bundled_root) if bundled_root else None)
    loaded = _ytdlp.version.__version__ if _ytdlp is not None else None
    if tag == on_disk:
        return {'status': 'current', 'version': tag, 'restart': bool(loaded and loaded != tag)}

    asset = next((a for a in release.get('assets') or [] if a.get('name') == 'yt-dlp'), None)
    if not asset:
        raise RuntimeError('Sürümde yt-dlp dosyası bulunamadı')
    payload = _http_get(asset['browser_download_url'], timeout=120)

    os.makedirs(YTDLP_DIR, exist_ok=True)
    staging = os.path.join(YTDLP_DIR, 'staging')
    shutil.rmtree(staging, ignore_errors=True)
    os.makedirs(staging)
    # The release binary is a zipapp: a shebang line followed by a zip archive.
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        members = [m for m in archive.namelist() if m.startswith(('yt_dlp/', 'yt_dlp_ejs/'))]
        archive.extractall(staging, members)
    if not _version_on_disk(staging):
        shutil.rmtree(staging, ignore_errors=True)
        raise RuntimeError('İndirilen yt-dlp paketi geçersiz')

    previous = UPDATED_YTDLP + '.old'
    shutil.rmtree(previous, ignore_errors=True)
    if os.path.isdir(UPDATED_YTDLP):
        os.rename(UPDATED_YTDLP, previous)
    os.rename(staging, UPDATED_YTDLP)
    shutil.rmtree(previous, ignore_errors=True)

    return {'status': 'updated', 'version': tag, 'restart': _ytdlp is not None}


def op_reset(_request):
    shutil.rmtree(UPDATED_YTDLP, ignore_errors=True)
    return {'restart': _ytdlp is not None and _ytdlp_source == 'updated'}


OPERATIONS = {
    'version': op_version,
    'probe': op_probe,
    'download': op_download,
    'cancel': op_cancel,
    'forget': op_forget,
    'update': op_update,
    'reset': op_reset,
}


def handle(request_json):
    """Entry point called by the C shim. Never raises; always returns JSON."""
    task_id = None
    try:
        request = json.loads(request_json)
        task_id = request.get('id')
        operation = OPERATIONS.get(request.get('op'))
        if operation is None:
            raise RuntimeError(f"Bilinmeyen işlem: {request.get('op')}")
        result = operation(request)
        return json.dumps({'ok': True, 'result': result}, ensure_ascii=False)
    except Cancelled:
        return json.dumps({'ok': False, 'cancelled': True, 'error': 'İptal edildi'})
    except BaseException as error:  # noqa: BLE001 - everything must become JSON
        cancelled = False
        if task_id:
            with _cancel_lock:
                cancelled = task_id in _cancelled
        if cancelled:
            return json.dumps({'ok': False, 'cancelled': True, 'error': 'İptal edildi'})
        message = _clean_error(getattr(error, 'msg', None) or str(error) or type(error).__name__)
        sys.stderr.write(traceback.format_exc())
        return json.dumps({'ok': False, 'error': message}, ensure_ascii=False)
