# Copyright 2017 Google Inc.
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

licenses(["notice"])

package(default_visibility = ["//visibility:public"])

java_library(
    name = "git",
    testonly = 1,
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git",
        "//java/com/google/copybara/git:core",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/util",
        "//third_party:google_http_client",
        "//third_party:google_http_client_test",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:junit",
        "//third_party:mockito",
        "//third_party:truth",
    ],
)
