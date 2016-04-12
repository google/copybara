licenses(["notice"])  # Apache 2.0

package(
    default_visibility = [
        "//java/com/google/copybara:__pkg__",
        "//javatests/com/google/copybara:__pkg__",
    ],
)

java_library(
    name = "guava",
    exports = [
        "@guava//jar",
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
    name = "junit4",
    testonly = 1,
    exports = [
        "@bazel_tools//third_party:junit4",
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
