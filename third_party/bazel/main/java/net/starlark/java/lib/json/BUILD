load("@rules_java//java:defs.bzl", "java_library")

package(default_applicable_licenses = ["//third_party/bazel:license"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//third_party/bazel/main/java/net/starlark/java/lib:__pkg__"],
)

# Starlark json module
java_library(
    name = "json",
    srcs = ["Json.java"],
    visibility = ["//third_party/bazel/main/java/net/starlark/java:clients"],
    deps = [
        "//third_party:guava",
        "//third_party/bazel/main/java/net/starlark/java/annot",
        "//third_party/bazel/main/java/net/starlark/java/eval",
        "//third_party/bazel/main/java/net/starlark/java/lib",
    ],
)
