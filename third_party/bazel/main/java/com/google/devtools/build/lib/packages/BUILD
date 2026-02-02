load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "provider",
    srcs = [
        "Info.java",
        "NativeInfo.java",
    ],
    visibility = ["//third_party/bazel:__subpackages__"],
    deps = [
        "//third_party:error_prone",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/bazel/main/java/com/google/devtools/build/lib/concurrent:thread_safety",
        "//third_party/bazel/main/java/net/starlark/java/eval",
        "//third_party/bazel/main/java/net/starlark/java/syntax",
    ],
)
