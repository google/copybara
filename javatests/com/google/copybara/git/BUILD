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

load("//javatests/com/google/copybara:test.bzl", "all_tests")

# Used for extending the tests internally. Not a public API.
package(default_visibility = ["//visibility:public"])

licenses(["notice"])

all_tests(
    tags = ["local"],
    tests = glob(
        ["**/*.java"],
        exclude = [
            "GitDestinationTest.java",
            "GitTestUtil.java",
        ],
    ),
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/checks",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/config:parser",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git",
        "//java/com/google/copybara/git:core",
        "//java/com/google/copybara/git:environment",
        "//java/com/google/copybara/git/github:api",
        "//java/com/google/copybara/git/github:util",
        "//java/com/google/copybara/git/github/api/testing:abstract_gitapi_test",
        "//java/com/google/copybara/git/testing",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/testing/git",
        "//java/com/google/copybara/testing/profiler",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:google_http_client",
        "//third_party:google_http_client_test",
        "//third_party:guava_testlib",
        "//third_party:jcommander",
        "//third_party:jimfs",
        "//third_party:mockito",
        "//third_party:starlark",
        "//third_party:truth",
    ],
)

all_tests(
    shard_count = 5,
    tags = ["local"],
    tests = ["GitDestinationTest.java"],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/checks",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/config:parser",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git",
        "//java/com/google/copybara/git:core",
        "//java/com/google/copybara/git:environment",
        "//java/com/google/copybara/git/github:api",
        "//java/com/google/copybara/git/github:util",
        "//java/com/google/copybara/git/github/api/testing:abstract_gitapi_test",
        "//java/com/google/copybara/git/testing",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/testing/git",
        "//java/com/google/copybara/testing/profiler",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:google_http_client",
        "//third_party:google_http_client_test",
        "//third_party:guava_testlib",
        "//third_party:jcommander",
        "//third_party:jimfs",
        "//third_party:mockito",
        "//third_party:starlark",
        "//third_party:truth",
    ],
)
