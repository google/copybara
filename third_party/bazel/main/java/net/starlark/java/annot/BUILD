load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//third_party/bazel:license"],
    default_visibility = ["//visibility:public"],
)

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//src:__subpackages__"],
)

# Annotations to make Java classes and methods accessible from Starlark.
# Depending on this library adds annotations processing to the build.
java_library(
    name = "annot",
    exported_plugins = ["//third_party/bazel/main/java/net/starlark/java/annot/processor"],
    visibility = ["//third_party/bazel/main/java/net/starlark/java:clients"],
    exports = [":annot_sans_processor"],
)

# Annotations without processor, to avoid a dependency cycle in the processor itself.
java_library(
    name = "annot_sans_processor",
    srcs = glob(["*.java"]),
    visibility = ["//third_party/bazel/main/java/net/starlark/java/annot/processor:__pkg__"],
    deps = [
        "//third_party/bazel/third_party:guava",
        "//third_party/bazel/third_party:jsr305",
    ],
)
