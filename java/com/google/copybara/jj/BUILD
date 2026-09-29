# Copyright 2026 Google LLC
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

load("@rules_java//java:defs.bzl", "java_library")

licenses(["notice"])

package(default_visibility = ["//visibility:public"])

CORE = [
    "JjRepository.java",
]

JAVACOPTS = [
    "-Xlint:unchecked",
]

java_library(
    name = "jj",
    srcs = glob(
        ["*.java"],
        exclude = CORE,
    ),
    javacopts = JAVACOPTS,
    deps = [
        ":core",
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jcommander",
    ],
)

java_library(
    name = "core",
    srcs = CORE,
    javacopts = JAVACOPTS,
    deps = [
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/util",
        "//third_party:error_prone",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:shell",
    ],
)
