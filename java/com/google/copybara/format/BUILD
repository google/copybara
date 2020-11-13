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
    name = "format",
    srcs = [
        "BuildifierFormat.java",
        "BuildifierOptions.java",
        "FormatModule.java",
    ],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:flogger",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:shell",
        "//third_party:starlark",
    ],
)

# TODO(malcon): Temporary until format.goimports is removed.
java_library(
    name = "buildifier_options",
    srcs = ["BuildifierOptions.java"],
    deps = [
        "//java/com/google/copybara:base",
        "//third_party:jcommander",
    ],
)
