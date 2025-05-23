# Copyright 2021 Google Inc.
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

# This is required because golang binaries have the build architecture in their
# path.
genrule(
    name = "normalised_buildifier",
    testonly = 1,
    srcs = ["@buildifier_prebuilt//:buildifier"],
    outs = ["buildifier"],
    cmd = "cp $(SRCS) $@",
)

all_tests(
    name = "all_tests",
    data = [":normalised_buildifier"],
    tags = ["local"],
    tests = glob(
        ["**/*.java"],
        exclude = ["ConfigHeuristicsInputProviderTest.java"],
    ),
    deps = [
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/configgen",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/onboard",
        "//java/com/google/copybara/onboard/core",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/testing/git",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:truth",
    ],
)

all_tests(
    name = "config_heuristic_input_test",
    tags = ["local"],
    tests = ["ConfigHeuristicsInputProviderTest.java"],
    deps = [
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/configgen",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git",
        "//java/com/google/copybara/git:core",
        "//java/com/google/copybara/onboard",
        "//java/com/google/copybara/onboard/core",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/testing/git",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:truth",
    ],
)
