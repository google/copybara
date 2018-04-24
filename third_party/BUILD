licenses(["notice"])  # Apache 2.0

package_group(
    name = "copybara_pkgs",
    packages = [
        "//java/com/google/copybara/...",
        "//javatests/com/google/copybara/...",
    ],
)

package(
    default_visibility = [":copybara_pkgs"],
)

java_library(
    name = "guava",
    exports = [
        "@guava//jar",
    ],
)

java_library(
    name = "guava_testlib",
    exports = [
        "@guava_testlib//jar",
    ],
)

java_library(
    name = "autocommon",
    exports = [
        "@autocommon//jar",
    ],
)

java_plugin(
    name = "autovalue_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@autovalue//jar",
    ],
)

java_library(
    name = "autovalue",
    exported_plugins = [
        ":autovalue_plugin",
    ],
    # TODO(matvore): What to export to give access to @AutoValue annotation
    # without depending on the processor at runtime?
    exports = [
        "@autovalue//jar",
    ],
)

java_library(
    name = "jcommander",
    exports = [
        "@jcommander//jar",
    ],
)

java_library(
    name = "jimfs",
    exports = [
        "@jimfs//jar",
    ],
)

java_library(
    name = "junit",
    exports = [
        "@junit//jar",
    ],
)

java_library(
    name = "re2j",
    exports = [
        "@re2j//jar",
    ],
)

java_library(
    name = "shell",
    exports = [
        "//third_party/bazel:shell",
    ],
)

java_library(
    name = "truth",
    testonly = 1,
    exports = [
        "@truth//jar",
    ],
)

java_library(
    name = "google_http_client",
    exports = [
        "@google_http_client//jar",
        "@google_http_client_gson//jar",
        "@gson//jar",
    ],
)

java_library(
    name = "google_http_client_test",
    testonly = 1,
    exports = [
        "@google_http_client_test//jar",
    ],
)

java_library(
    name = "mockito",
    testonly = 1,
    exports = [
        "@mockito//jar",
    ],
    runtime_deps = [
        "@objenesis//jar",
    ],
)

java_library(
    name = "jsr305",
    exports = [
        "@jsr305//jar",
    ],
)

java_library(
    name = "skylark-lang",
    exports = [
        "@io_bazel//src/main/java/com/google/devtools/build/lib:skylark-lang",
    ],
)

java_library(
    name = "flogger",
    exports = [
        "@flogger//jar",
    ],
)
