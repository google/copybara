# Copyright 2018 Google Inc.
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

# Used for extending the tests internally. Not a public API.
package(default_visibility = ["//visibility:public"])

load("//javatests/com/google/copybara:test.bzl", "all_tests")

licenses(["notice"])  # Apache 2.0

DEPS = [
    "//java/com/google/copybara/git/github:util",
    "//java/com/google/copybara/exception",
    "//third_party:truth",
    "//third_party:mockito",
    "//third_party:guava_testlib",
]

all_tests(
    tests = ["GithubUtilTest.java"],
    deps = DEPS,
)
