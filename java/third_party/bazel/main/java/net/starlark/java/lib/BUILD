load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//third_party/bazel:license"],
    default_visibility = ["//third_party/bazel:__subpackages__"],
)

filegroup(
    name = "srcs",
    srcs = glob(["*"]) + [
        "//third_party/bazel/main/java/net/starlark/java/lib/json:srcs",
    ],
    visibility = ["//third_party/bazel:__subpackages__"],
)

java_library(
    name = "lib",
    srcs = glob(["*.java"]),
    visibility = ["//third_party/bazel/main/java/net/starlark/java:clients"],
    deps = [
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/bazel/main/java/net/starlark/java/eval",
    ],
)
