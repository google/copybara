# Copyright 2016 Google LLC
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

licenses(["notice"])

all_tests(
    name = "all_tests",
    tags = ["local"],
    tests = glob(["**/*Test.java"]),
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/credentials",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/http/auth",
        "//java/com/google/copybara/http/testing",
        "//java/com/google/copybara/remotefile",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//java/com/google/copybara/version",
        "//third_party:apache_commons_compress",
        "//third_party:google_http_client",
        "//third_party:google_http_client_test",
        "//third_party:guava_testlib",
        "//third_party:mockito",
        "//third_party:truth",
    ],
)
