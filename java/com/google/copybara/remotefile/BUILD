# Copyright 2020 Google Inc.
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

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "remotefile",
    srcs = glob(["*.java"]),
    javacopts = [
        "-Xlint:unchecked",
        "-source",
        "1.8",
    ],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/config:base",
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/jcommander:converters",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/util/console",
        "//third_party:google_http_client",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:starlark",
    ],
)
