load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("//third_party:bazel.bzl", "bazel_sha256", "bazel_version")
load("//third_party:bazel_buildtools.bzl", "buildtools_sha256", "buildtools_version")
load("//third_party:bazel_skylib.bzl", "skylib_sha256", "skylib_version")

def _non_module_deps(_):
    # http_archive(
    #     name = "io_bazel",
    #     sha256 = bazel_sha256,
    #     strip_prefix = "bazel-" + bazel_version,
    #     url = "https://github.com/bazelbuild/bazel/archive/" + bazel_version + ".zip",
    # )

    http_archive(
        name = "JCommander",
        sha256 = "e7ed3cf09f43d0d0a083f1b3243c6d1b45139a84a61c6356504f9b7aa14554fc",
        urls = [
            "https://github.com/cbeust/jcommander/archive/05254453c0a824f719bd72dac66fa686525752a5.zip",
        ],
        build_file = Label("//external/third_party:jcommander.BUILD"),
    )
    http_archive(
        name = "io_bazel",
        sha256 = bazel_sha256,
        strip_prefix = "bazel-" + bazel_version,
        url = "https://github.com/bazelbuild/bazel/archive/" + bazel_version + ".zip",
    )

non_module_deps = module_extension(implementation = _non_module_deps)
