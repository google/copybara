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

licenses(["notice"])  # Apache 2.0

LOCAL_TESTS = {
    "InfoTest.java" : 1,
    "util/DiffUtilTest.java" : 5,
    "WorkflowTest.java" : 5,
}

DEPS = [
    "//java/com/google/copybara/authoring",
    "//java/com/google/copybara:base",
    "//java/com/google/copybara:copybara_main",
    "//java/com/google/copybara/config:base",
    "//java/com/google/copybara/config:parser",
    "//java/com/google/copybara:general_options",
    "//java/com/google/copybara/git",
    "//java/com/google/copybara/git:core",
    "//java/com/google/copybara:copybara_lib",
    "//java/com/google/copybara/doc:annotations",
    "//java/com/google/copybara/checks",
    "//java/com/google/copybara/exception",
    "//java/com/google/copybara/monitor",
    "//java/com/google/copybara/profiler",
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
    "//java/com/google/copybara/hg",
    "//third_party:skylark-lang",
    "//third_party:truth",
]

[all_tests(
    shard_count = count,
    tags = ["local"],
    tests = [file],
    deps = DEPS,
) for file, count in LOCAL_TESTS.items()]

all_tests(
    tests = glob(
        ["**/*Test.java"],
        exclude = LOCAL_TESTS.keys(),
    ),
    deps = DEPS,
)
