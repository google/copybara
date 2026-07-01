load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

package(
    default_applicable_licenses = ["//third_party/bazel:license"],
    default_visibility = ["//third_party/bazel:__subpackages__"],
)

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//third_party/bazel:__subpackages__"],
)

# A processor for Starlark annotations.
java_plugin(
    name = "processor",
    processor_class = "net.starlark.java.annot.processor.StarlarkMethodProcessor",
    visibility = ["//third_party/bazel/main/java/net/starlark/java:starlark"],
    deps = [":processor_lib"],
)

java_library(
    name = "processor_lib",
    srcs = ["StarlarkMethodProcessor.java"],
    visibility = ["//third_party/bazel/main/java/net/starlark/java:starlark"],
    deps = [
        "//third_party:error_prone",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/bazel/main/java/net/starlark/java/annot:annot_sans_processor",
    ],
)
