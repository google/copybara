# The spelling checker used by the Starlark interpreter.

load("@rules_java//java:defs.bzl", "java_library")

package(default_applicable_licenses = ["//third_party/bazel:license"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//third_party/bazel/main/java/net/starlark/java:__pkg__"],
)

java_library(
    name = "spelling",
    srcs = ["SpellChecker.java"],
    # The SpellChecker is available to Starlark and Bazel, but not the world.
    visibility = [
        "//third_party/bazel/main/java/net/starlark/java:bazel",
        "//third_party/bazel/main/java/net/starlark/java:starlark",
    ],
    deps = [
        "//third_party:jsr305",
    ],
)
