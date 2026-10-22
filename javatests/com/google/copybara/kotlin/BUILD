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

load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_test")
load("//javatests/com/google/copybara:test.bzl", "all_tests")

licenses(["notice"])

kt_jvm_test(
    name = "DummyKotlinTest",
    srcs = ["DummyKotlinTest.kt"],
    test_class = "com.google.copybara.kotlin.DummyKotlinTest",
    deps = [
        "//java/com/google/copybara/kotlin",
        "//third_party:junit",
        "//third_party:shell",
        "//third_party:truth",
    ],
)

all_tests(
    name = "all_tests",
    tags = ["local"],
    tests = [
        "DummyKotlinJavaTest.java",
    ],
    deps = [
        "//java/com/google/copybara/kotlin",
        "//third_party:junit",
        "//third_party:truth",
    ],
)
