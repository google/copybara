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

licenses(["notice"])  # Apache 2.0

package(default_visibility = ["//visibility:public"])

load(":docs.bzl", "doc_generator")

export_files = [
    "doc_skylark.sh",
    "docs.bzl",
]

JAVACOPTS = [
    "-Xlint:unchecked",
    "-source",
    "1.8",
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
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
    ],
)

doc_generator(
    name = "docs",
    deps = [":copybara"],
)

BASE_SRCS = [
    "Author.java",
    "Authoring.java",
    "Change.java",
    "ChangeVisitable.java",
    "CheckoutPath.java",
    "Destination.java",
    "EmptyChangeException.java",
    "Metadata.java",
    "LabelFinder.java",
    "NonReversibleValidationException.java",
    "Option.java",
    "Options.java",
    "Origin.java",
    "RepoException.java",
    "Reference.java",
    "Transformation.java",
    "TransformResult.java",
    "TransformWork.java",
    "Changes.java",
    "ValidationException.java",
    "VoidOperationException.java",
    "WorkflowOptions.java",
]

java_library(
    name = "base",
    srcs = BASE_SRCS,
    javacopts = JAVACOPTS,
    deps = [
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:skylark-lang",
    ],
)

java_library(
    name = "copybara_lib",
    srcs = glob(
        ["**/*.java"],
        exclude = ["Main.java"] + BASE_SRCS,
    ),
    javacopts = JAVACOPTS,
    deps = [
        ":base",
        "//java/com/google/copybara/config/base",
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/transform",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:autovalue",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:shell",
        "//third_party:skylark-lang",
    ],
)
