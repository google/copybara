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

licenses(["notice"])

package(default_visibility = ["//visibility:public"])

java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["**/*.java"]),
    javacopts = [
        "-Xlint:unchecked",
        "-source",
        "1.8",
    ],
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/authoring",
        "//java/com/google/copybara/buildozer:buildozer_options",
        "//java/com/google/copybara/checks",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/config:parser",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/format:buildifier_options",
        "//java/com/google/copybara/git",
        "//java/com/google/copybara/hg",
        "//java/com/google/copybara/monitor",
        "//java/com/google/copybara/remotefile",
        "//java/com/google/copybara/transform/debug",
        "//java/com/google/copybara/transform/patch",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:google_http_client",
        "//third_party:guava",
        "//third_party:jimfs",
        "//third_party:jsr305",
        "//third_party:starlark",
        "//third_party:truth",
    ],
)
