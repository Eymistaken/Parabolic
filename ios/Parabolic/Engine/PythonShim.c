#include "PythonShim.h"

#include <Python/Python.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static parabolic_emit_fn g_emit = NULL;
static parabolic_eval_js_fn g_eval_js = NULL;
static PyObject *g_handle = NULL;

// MARK: - The `_parabolic` built-in module

static PyObject *shim_emit(PyObject *self, PyObject *args) {
    const char *json = NULL;
    if (!PyArg_ParseTuple(args, "s", &json)) {
        return NULL;
    }
    if (g_emit != NULL) {
        char *copy = strdup(json);
        if (copy != NULL) {
            // Swift may hop threads or take locks; never do that while holding the GIL.
            Py_BEGIN_ALLOW_THREADS
            g_emit(copy);
            Py_END_ALLOW_THREADS
            free(copy);
        }
    }
    Py_RETURN_NONE;
}

static PyObject *shim_eval_js(PyObject *self, PyObject *args) {
    const char *source = NULL;
    if (!PyArg_ParseTuple(args, "s", &source)) {
        return NULL;
    }
    if (g_eval_js == NULL) {
        PyErr_SetString(PyExc_RuntimeError, "JavaScript engine is not available");
        return NULL;
    }
    char *copy = strdup(source);
    if (copy == NULL) {
        return PyErr_NoMemory();
    }
    char *result = NULL;
    Py_BEGIN_ALLOW_THREADS
    result = g_eval_js(copy);
    Py_END_ALLOW_THREADS
    free(copy);
    if (result == NULL) {
        PyErr_SetString(PyExc_RuntimeError, "JavaScript evaluation returned nothing");
        return NULL;
    }
    PyObject *value = PyUnicode_DecodeUTF8(result, (Py_ssize_t)strlen(result), "replace");
    free(result);
    return value;
}

static PyMethodDef shim_methods[] = {
    {"emit", shim_emit, METH_VARARGS, "Send a JSON event to the app."},
    {"eval_js", shim_eval_js, METH_VARARGS, "Evaluate JavaScript with JavaScriptCore."},
    {NULL, NULL, 0, NULL},
};

static struct PyModuleDef shim_module = {
    PyModuleDef_HEAD_INIT,
    "_parabolic",
    "Callbacks into the Parabolic app.",
    -1,
    shim_methods,
    NULL,
    NULL,
    NULL,
    NULL,
};

static PyObject *PyInit__parabolic(void) {
    return PyModule_Create(&shim_module);
}

// MARK: - Helpers

static char *copy_string(const char *text) {
    char *copy = strdup(text != NULL ? text : "");
    return copy;
}

/// Turns the pending Python exception into a malloc'd message and clears it.
static char *take_exception_message(const char *fallback) {
    PyObject *type = NULL, *value = NULL, *traceback = NULL;
    PyErr_Fetch(&type, &value, &traceback);
    PyErr_NormalizeException(&type, &value, &traceback);
    char *message = NULL;
    if (value != NULL) {
        PyObject *text = PyObject_Str(value);
        if (text != NULL) {
            const char *utf8 = PyUnicode_AsUTF8(text);
            if (utf8 != NULL) {
                message = copy_string(utf8);
            }
            Py_DECREF(text);
        }
    }
    PyErr_Clear();
    Py_XDECREF(type);
    Py_XDECREF(value);
    Py_XDECREF(traceback);
    return message != NULL ? message : copy_string(fallback);
}

static int insert_path(PyObject *sys_path, const char *resource_path, const char *leaf) {
    size_t length = strlen(resource_path) + strlen(leaf) + 2;
    char *joined = malloc(length);
    if (joined == NULL) {
        return -1;
    }
    snprintf(joined, length, "%s/%s", resource_path, leaf);
    PyObject *item = PyUnicode_DecodeFSDefault(joined);
    free(joined);
    if (item == NULL) {
        return -1;
    }
    int status = PyList_Insert(sys_path, 0, item);
    Py_DECREF(item);
    return status;
}

static int fail_status(PyStatus status, PyConfig *config, char **error_out) {
    if (error_out != NULL) {
        *error_out = copy_string(status.err_msg != NULL ? status.err_msg : "Python could not be configured");
    }
    if (config != NULL) {
        PyConfig_Clear(config);
    }
    return 1;
}

// MARK: - Public API

int parabolic_python_start(const char *resource_path,
                           parabolic_emit_fn emit,
                           parabolic_eval_js_fn eval_js,
                           char **error_out) {
    if (g_handle != NULL) {
        return 0;
    }
    g_emit = emit;
    g_eval_js = eval_js;

    if (PyImport_AppendInittab("_parabolic", PyInit__parabolic) == -1) {
        if (error_out != NULL) {
            *error_out = copy_string("Could not register the _parabolic module");
        }
        return 1;
    }

    PyStatus status;
    PyPreConfig preconfig;
    PyConfig config;

    PyPreConfig_InitIsolatedConfig(&preconfig);
    preconfig.utf8_mode = 1;
    status = Py_PreInitialize(&preconfig);
    if (PyStatus_Exception(status)) {
        return fail_status(status, NULL, error_out);
    }

    PyConfig_InitIsolatedConfig(&config);
    config.buffered_stdio = 0;
    // The bundle is read-only once signed.
    config.write_bytecode = 0;
    config.install_signal_handlers = 1;
#ifdef __APPLE__
    config.use_system_logger = 1;
#endif

    size_t home_length = strlen(resource_path) + strlen("/python") + 1;
    char *home = malloc(home_length);
    if (home == NULL) {
        PyConfig_Clear(&config);
        if (error_out != NULL) {
            *error_out = copy_string("Out of memory");
        }
        return 1;
    }
    snprintf(home, home_length, "%s/python", resource_path);
    status = PyConfig_SetBytesString(&config, &config.home, home);
    free(home);
    if (PyStatus_Exception(status)) {
        return fail_status(status, &config, error_out);
    }

    status = PyConfig_Read(&config);
    if (PyStatus_Exception(status)) {
        return fail_status(status, &config, error_out);
    }

    status = Py_InitializeFromConfig(&config);
    PyConfig_Clear(&config);
    if (PyStatus_Exception(status)) {
        return fail_status(status, NULL, error_out);
    }

    // sys.path: <bundle>/app (our bridge), then <bundle>/app_packages (yt-dlp,
    // certifi), then the standard library computed from PYTHONHOME.
    PyObject *sys_path = PySys_GetObject("path");  // borrowed
    if (sys_path == NULL
        || insert_path(sys_path, resource_path, "app_packages") != 0
        || insert_path(sys_path, resource_path, "app") != 0) {
        if (error_out != NULL) {
            *error_out = take_exception_message("Could not set sys.path");
        }
        PyEval_SaveThread();
        return 1;
    }

    PyObject *bridge = PyImport_ImportModule("parabolic_bridge");
    if (bridge == NULL) {
        if (error_out != NULL) {
            *error_out = take_exception_message("Could not import parabolic_bridge");
        }
        PyEval_SaveThread();
        return 1;
    }
    g_handle = PyObject_GetAttrString(bridge, "handle");
    Py_DECREF(bridge);
    if (g_handle == NULL || !PyCallable_Check(g_handle)) {
        if (error_out != NULL) {
            *error_out = take_exception_message("parabolic_bridge.handle is missing");
        }
        Py_CLEAR(g_handle);
        PyEval_SaveThread();
        return 1;
    }

    // Release the GIL so that any thread can take it with PyGILState_Ensure.
    PyEval_SaveThread();
    return 0;
}

char *parabolic_python_call(const char *request_json) {
    if (g_handle == NULL) {
        return copy_string("{\"ok\": false, \"error\": \"Python is not running\"}");
    }
    PyGILState_STATE gil = PyGILState_Ensure();
    char *output = NULL;

    PyObject *argument = PyUnicode_FromString(request_json != NULL ? request_json : "{}");
    PyObject *result = argument != NULL ? PyObject_CallOneArg(g_handle, argument) : NULL;
    if (result != NULL && PyUnicode_Check(result)) {
        const char *utf8 = PyUnicode_AsUTF8(result);
        if (utf8 != NULL) {
            output = copy_string(utf8);
        }
    }
    if (output == NULL) {
        if (PyErr_Occurred()) {
            char *message = take_exception_message("bridge failure");
            free(message);
        }
        output = copy_string("{\"ok\": false, \"error\": \"Python bridge failure\"}");
    }
    Py_XDECREF(argument);
    Py_XDECREF(result);
    PyGILState_Release(gil);
    return output;
}

void parabolic_free(char *pointer) {
    free(pointer);
}
