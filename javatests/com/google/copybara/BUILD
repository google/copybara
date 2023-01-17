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

load(":test.bzl", "all_tests")
load("@bazel_skylib//:bzl_library.bzl", "bzl_library")

licenses(["notice"])

LOCAL_TESTS = {
    "InfoTest.java": 3,
    "MigrateCmdTest.java": 2,
    "util/DiffUtilTest.java": 5,
    "util/AutoPatchUtilTest.java": 2,
    "WorkflowTest.java": 5,
}

[all_tests(
    shard_count = count,
    tags = ["local"],
    tests = [file],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara:copybara_main",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/checks",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/config:parser",
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/effect",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git",
        "//java/com/google/copybara/git:core",
        "//java/com/google/copybara/hg",
        "//java/com/google/copybara/monitor",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/revision",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/testing/git",
        "//java/com/google/copybara/testing/profiler",
        "//java/com/google/copybara/transform",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util:origin_util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:guava_testlib",
        "//third_party:jcommander",
        "//third_party:jimfs",
        "//third_party:mockito",
        "//third_party:shell",
        "//third_party:starlark",
        "//third_party:truth",
    ],
) for file, count in LOCAL_TESTS.items()]

all_tests(
    tests = glob(
        ["**/*Test.java"],
        exclude = LOCAL_TESTS.keys(),
    ),
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara:copybara_main",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara:starlark_date_time_module",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/checks",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/config:parser",
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/effect",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git",
        "//java/com/google/copybara/git:core",
        "//java/com/google/copybara/hg",
        "//java/com/google/copybara/monitor",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/revision",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/testing/git",
        "//java/com/google/copybara/testing/profiler",
        "//java/com/google/copybara/transform",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util:origin_util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:google_code_gson",
        "//third_party:guava_testlib",
        "//third_party:jcommander",
        "//third_party:jimfs",
        "//third_party:mockito",
        "//third_party:shell",
        "//third_party:starlark",
        "//third_party:testparameterinjector",
        "//third_party:truth",
    ],
)

bzl_library(
    name = "test_bzl",
    srcs = ["test.bzl"],
    visibility = ["//visibility:private"],
)
