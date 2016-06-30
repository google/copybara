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
    name = "joda_time",
    exports = [
        "@joda_time//jar",
    ],
)

java_library(
    name = "snakeyaml",
    exports = [
        "@snakeyaml//jar",
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
    name = "jsr305",
    exports = [
        "@jsr305//jar",
    ],
)
