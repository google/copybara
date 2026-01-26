# Copyright 2016 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("//third_party:bazel.bzl", "bazel_sha256", "bazel_version")
load("//third_party:bazel_skylib.bzl", "skylib_sha256", "skylib_version")

def copybara_repositories():
    RULES_JVM_EXTERNAL_TAG = "6.5"

    RULES_JVM_EXTERNAL_SHA = "c5b27928eeb8f0761f0805540587660f0ecc6946e9c04bf0d4c89e2f7d332b2b"

    maybe(
        http_archive,
        name = "platforms",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/platforms/releases/download/1.0.0/platforms-1.0.0.tar.gz",
            "https://github.com/bazelbuild/platforms/releases/download/1.0.0/platforms-1.0.0.tar.gz",
        ],
        sha256 = "3384eb1c30762704fbe38e440204e114154086c8fc8a8c2e3e28441028c019a8",
    )

    maybe(
        http_archive,
        name = "rules_jvm_external",
        sha256 = RULES_JVM_EXTERNAL_SHA,
        strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
        url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
        patch_args = ["-p1"],
        patches = ["//third_party:rules_jvm_external_6.5.patch"],
    )

    # LICENSE: The Apache Software License, Version 2.0
    # Additional bazel rules:
    maybe(
        http_archive,
        name = "bazel_skylib",
        sha256 = skylib_sha256,
        strip_prefix = "bazel-skylib-" + skylib_version,
        url = "https://github.com/bazelbuild/bazel-skylib/archive/" + skylib_version + ".zip",
    )

    # Stuff used by Bazel Starlark syntax package transitively:
    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "com_google_protobuf",
        sha256 = "3feeabd077a112b56af52519bc4ece90e28b4583f4fc2549c95d765985e0fd3c",
        strip_prefix = "protobuf-32.1",
        urls = [
            "https://github.com/protocolbuffers/protobuf/releases/download/v32.1/protobuf-32.1.tar.gz",
        ],
    )

    # Stuff used by Buildifier transitively:
    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "io_bazel_rules_go",
        sha256 = "80a98277ad1311dacd837f9b16db62887702e9f1d1c4c9f796d0121a46c8e184",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.46.0/rules_go-v0.46.0.zip",
            "https://github.com/bazelbuild/rules_go/releases/download/v0.46.0/rules_go-v0.46.0.zip",
        ],
    )

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "rules_pkg",
        sha256 = "d20c951960ed77cb7b341c2a59488534e494d5ad1d30c4818c736d57772a9fef",
        url = "https://mirror.bazel.build/github.com/bazelbuild/rules_pkg/releases/download/1.0.1/rules_pkg-1.0.1.tar.gz",
    )

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "rules_java",
        sha256 = "865b3d334bd0f769587737447410d8042d6a95134cc45be5380805fdbacd7152",
        url = "https://github.com/bazelbuild/rules_java/releases/download/9.0.3/rules_java-9.0.3.tar.gz",
    )

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "rules_python",
        sha256 = "9f9f3b300a9264e4c77999312ce663be5dee9a56e361a1f6fe7ec60e1beef9a3",
        strip_prefix = "rules_python-1.4.1",
        url = "https://github.com/bazelbuild/rules_python/releases/download/1.4.1/rules_python-1.4.1.tar.gz",
    )

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "rules_cc",
        sha256 = "472ddca8cec1e64ad78e4f0cabbec55936a3baddbe7bef072764ca91504bd523",
        strip_prefix = "rules_cc-0.2.13",
        url = "https://github.com/bazelbuild/rules_cc/releases/download/0.2.13/rules_cc-0.2.13.tar.gz",
    )

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "rules_license",
        sha256 = "26d4021f6898e23b82ef953078389dd49ac2b5618ac564ade4ef87cced147b38",
        url = "https://mirror.bazel.build/github.com/bazelbuild/rules_license/releases/download/1.0.0/rules_license-1.0.0.tar.gz",
    )

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "rules_proto",
        sha256 = "14a225870ab4e91869652cfd69ef2028277fc1dc4910d65d353b62d6e0ae21f4",
        strip_prefix = "rules_proto-7.1.0",
        url = "https://github.com/bazelbuild/rules_proto/releases/download/7.1.0/rules_proto-7.1.0.tar.gz",
    )

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "rules_shell",
        sha256 = "d8cd4a3a91fc1dc68d4c7d6b655f09def109f7186437e3f50a9b60ab436a0c53",
        strip_prefix = "rules_shell-0.3.0",
        url = "https://github.com/bazelbuild/rules_shell/releases/download/v0.3.0/rules_shell-v0.3.0.tar.gz",
    )

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "bazel_gazelle",
        sha256 = "ecba0f04f96b4960a5b250c8e8eeec42281035970aa8852dda73098274d14a1d",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.29.0/bazel-gazelle-v0.29.0.tar.gz",
            "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.29.0/bazel-gazelle-v0.29.0.tar.gz",
        ],
    )

    # LICENSE: MIT
    maybe(
        http_archive,
        name = "buildifier_prebuilt",
        sha256 = "8ada9d88e51ebf5a1fdff37d75ed41d51f5e677cdbeafb0a22dda54747d6e07e",
        strip_prefix = "buildifier-prebuilt-6.4.0",
        urls = [
            "http://github.com/keith/buildifier-prebuilt/archive/6.4.0.tar.gz",
        ],
    )

    _non_module_deps(None)

def _non_module_deps(_):
    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        http_archive,
        name = "io_bazel",
        sha256 = bazel_sha256,
        strip_prefix = "bazel-" + bazel_version,
        url = "https://github.com/bazelbuild/bazel/archive/" + bazel_version + ".zip",
        patch_args = ["-p1"],
        patches = ["//third_party:bazel.patch"],
    )

non_module_deps = module_extension(implementation = _non_module_deps)

def _async_profiler_repos(ctx):
    http_file(
        name = "async_profiler",
        downloaded_file_path = "async-profiler.jar",
        # At commit f0ceda6356f05b7ad0a6593670c8c113113bf0b3 (2024-12-09).
        sha256 = "da95a5292fb203966196ecb68a39a8c26ad7276aeef642ec1de872513be1d8b3",
        urls = ["https://mirror.bazel.build/github.com/async-profiler/async-profiler/releases/download/nightly/async-profiler.jar"],
    )

    _ASYNC_PROFILER_BUILD_TEMPLATE = """
load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
copy_file(
    name = "libasyncProfiler",
    src = "libasyncProfiler.{ext}",
    out = "{tag}/libasyncProfiler.so",
    visibility = ["//visibility:public"],
)
"""

    http_archive(
        name = "async_profiler_linux_arm64",
        build_file_content = _ASYNC_PROFILER_BUILD_TEMPLATE.format(
            ext = "so",
            tag = "linux-arm64",
        ),
        sha256 = "7c6243bb91272a2797acb8cc44acf3e406e0b658a94d90d9391ca375fc961857",
        strip_prefix = "async-profiler-3.0-f0ceda6-linux-arm64/lib",
        urls = ["https://mirror.bazel.build/github.com/async-profiler/async-profiler/releases/download/nightly/async-profiler-3.0-f0ceda6-linux-arm64.tar.gz"],
    )

    http_archive(
        name = "async_profiler_linux_x64",
        build_file_content = _ASYNC_PROFILER_BUILD_TEMPLATE.format(
            ext = "so",
            tag = "linux-x64",
        ),
        sha256 = "448a3dc681375860eba2264d6cae7a848bd3f07f81f547a9ce58b742a1541d25",
        strip_prefix = "async-profiler-3.0-f0ceda6-linux-x64/lib",
        urls = ["https://mirror.bazel.build/github.com/async-profiler/async-profiler/releases/download/nightly/async-profiler-3.0-f0ceda6-linux-x64.tar.gz"],
    )

    http_archive(
        name = "async_profiler_macos",
        build_file_content = _ASYNC_PROFILER_BUILD_TEMPLATE.format(
            ext = "dylib",
            tag = "macos",
        ),
        sha256 = "0651004c78d080f67763cddde6e1f58cd0d0c4cb0b57034beef80b450ff5adf2",
        strip_prefix = "async-profiler-3.0-f0ceda6-macos/lib",
        urls = ["https://mirror.bazel.build/github.com/async-profiler/async-profiler/releases/download/nightly/async-profiler-3.0-f0ceda6-macos.zip"],
    )

# This is an extension (instead of use_repo_rule usages) only to create a
# lockfile entry for the distribution repo module extension.
async_profiler_repos = module_extension(_async_profiler_repos)
