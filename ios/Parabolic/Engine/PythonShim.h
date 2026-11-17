#ifndef PARABOLIC_PYTHON_SHIM_H
#define PARABOLIC_PYTHON_SHIM_H

// A deliberately tiny C surface over the embedded CPython interpreter, so the
// Swift side never has to deal with reference counting or the GIL.

#ifdef __cplusplus
extern "C" {
#endif

/// Receives a JSON event (progress, log line) from Python. The string is only
/// valid for the duration of the call.
typedef void (*parabolic_emit_fn)(const char *json);

/// Evaluates a JavaScript program and returns a malloc'd JSON string:
/// {"ok": true, "stdout": "..."} or {"ok": false, "error": "..."}.
typedef char *(*parabolic_eval_js_fn)(const char *source);

/// Starts the interpreter from the app bundle at `resource_path` and imports
/// the bridge module. Returns 0 on success; on failure returns non-zero and
/// stores a malloc'd message in `error_out`. Safe to call only once.
int parabolic_python_start(const char *resource_path,
                           parabolic_emit_fn emit,
                           parabolic_eval_js_fn eval_js,
                           char **error_out);

/// Calls `parabolic_bridge.handle(request_json)` from any thread and returns
/// its malloc'd JSON response (never NULL after a successful start).
char *parabolic_python_call(const char *request_json);

/// Frees a string returned by this shim.
void parabolic_free(char *pointer);

#ifdef __cplusplus
}
#endif

#endif
