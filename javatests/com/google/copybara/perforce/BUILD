# Copyright 2026 Google Inc.
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

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

all_tests(
    name = "all_tests",
    tests = [
        "PerforceDestinationTest.java",
        "PerforceOriginTest.java",
    ],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/perforce",
        "//java/com/google/copybara/revision",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/util",
        "//third_party:mockito",
        "//third_party:p4java",
        "//third_party:truth",
    ],
)
