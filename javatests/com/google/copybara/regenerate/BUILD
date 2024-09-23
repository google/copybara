# Copyright 2023 Google LLC
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
load("//javatests/com/google/copybara:test.bzl", "all_tests")

licenses(["notice"])

java_library(
    name = "regenerate_cmd_test_lib",
    testonly = 1,
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/revision",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util:consistency",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:junit",
        "//third_party:mockito",
        "//third_party:truth",
    ],
)

all_tests(
    name = "all_tests",
    tags = ["local"],
    tests = glob(["**/*Test.java"]),
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/revision",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util:consistency",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:mockito",
        "//third_party:truth",
    ],
)
