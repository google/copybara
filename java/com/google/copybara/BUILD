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

load(":docs.bzl", "copybara_reference")
load("@bazel_skylib//:bzl_library.bzl", "bzl_library")

licenses(["notice"])

package(default_visibility = ["//visibility:public"])

exports_files(
    [
        "doc_skylark.sh",
        "docs.bzl",
    ],
    visibility = ["//visibility:public"],
)

JAVACOPTS = [
    "-Xlint:unchecked",
]

java_binary(
    name = "copybara",
    javacopts = JAVACOPTS,
    main_class = "com.google.copybara.Main",
    runtime_deps = [
        ":copybara_main",
    ],
)

java_library(
    name = "copybara_main",
    srcs = ["Main.java"],
    javacopts = JAVACOPTS,
    deps = [
        ":base",
        ":copybara_lib",
        ":general_options",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/jcommander:converters",
        "//java/com/google/copybara/onboard",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:flogger",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:starlark",
    ],
)

copybara_reference(
    name = "generate_reference",
    out = "reference.md",
    libraries = ["//java/com/google/copybara:copybara_main"],
)

BASE_SRCS = [
    "BaselinesWithoutLabelVisitor.java",
    "ChangeMessage.java",
    "ChangeVisitable.java",
    "CheckoutPath.java",
    "CheckoutPathAttributes.java",
    "ConfigItemDescription.java",
    "Destination.java",
    "DestinationInfo.java",
    "DestinationReader.java",
    "DestinationStatusVisitor.java",
    "Endpoint.java",
    "EndpointProvider.java",
    "Info.java",
    "LazyResourceLoader.java",
    "LabelFinder.java",
    "Metadata.java",
    "MigrationInfo.java",
    "Option.java",
    "Options.java",
    "Origin.java",
    "SkylarkContext.java",
    "Transformation.java",
    "TransformResult.java",
    "TransformationStatus.java",
    "TransformWork.java",
    "Trigger.java",
    "treestate/TreeState.java",
    "treestate/TreeStateUtil.java",
    "WorkflowOptions.java",
    "WriterContext.java",
]

java_library(
    name = "options",
    srcs = [
        "Option.java",
        "Options.java",
    ],
    javacopts = JAVACOPTS,
    deps = [
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:shell",
    ],
)

java_library(
    name = "moduleset",
    srcs = ["ModuleSet.java"],
    javacopts = JAVACOPTS,
    deps = [
        ":options",
        "//third_party:guava",
    ],
)

java_library(
    name = "base",
    srcs = BASE_SRCS,
    javacopts = JAVACOPTS,
    deps = [
        ":local_parallelizer",
        "//java/com/google/copybara/approval",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/effect",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/jcommander:converters",
        "//java/com/google/copybara/jcommander:validators",
        "//java/com/google/copybara/revision",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:autovalue",
        "//third_party:error_prone",
        "//third_party:flogger",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:starlark",
    ],
)

java_library(
    name = "general_options",
    srcs = ["GeneralOptions.java"],
    javacopts = JAVACOPTS,
    deps = [
        ":base",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/jcommander:converters",
        "//java/com/google/copybara/monitor",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:flogger",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:shell",
    ],
)

java_library(
    name = "local_parallelizer",
    srcs = ["LocalParallelizer.java"],
    javacopts = JAVACOPTS,
    deps = [
        "//java/com/google/copybara/exception",
        "//third_party:guava",
    ],
)

java_library(
    name = "starlark_date_time_module",
    srcs = ["StarlarkDateTimeModule.java"],
    javacopts = JAVACOPTS,
    deps = [
        "//java/com/google/copybara/exception",
        "//third_party:guava",
        "//third_party:starlark",
    ],
)

java_library(
    name = "copybara_lib",
    srcs = glob(
        ["**/*.java"],
        exclude = [
            "Main.java",
            "GeneralOptions.java",
            "StarlarkDateTimeModule.java",
        ] + BASE_SRCS,
    ),
    javacopts = JAVACOPTS,
    deps = [
        ":base",
        ":general_options",
        "//java/com/google/copybara:starlark_date_time_module",
        "//java/com/google/copybara/action",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/buildozer",
        "//java/com/google/copybara/buildozer:buildozer_options",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/config:global_migrations",
        "//java/com/google/copybara/config:parser",
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/effect",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/format",
        "//java/com/google/copybara/git",
        "//java/com/google/copybara/go",
        "//java/com/google/copybara/hg",
        "//java/com/google/copybara/monitor",
        "//java/com/google/copybara/onboard:options",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/re2",
        "//java/com/google/copybara/remotefile",
        "//java/com/google/copybara/revision",
        "//java/com/google/copybara/templatetoken",
        "//java/com/google/copybara/toml",
        "//java/com/google/copybara/transform",
        "//java/com/google/copybara/transform/debug",
        "//java/com/google/copybara/transform/patch",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/version",
        "//java/com/google/copybara/xml",
        "//third_party:autovalue",
        "//third_party:error_prone",
        "//third_party:flogger",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:starlark",
    ],
)

bzl_library(
    name = "docs_bzl",
    srcs = ["docs.bzl"],
    visibility = ["//visibility:private"],
)
