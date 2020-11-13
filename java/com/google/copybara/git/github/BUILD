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

licenses(["notice"])

package(default_visibility = ["//visibility:public"])

JAVACOPTS = [
    "-Xlint:unchecked",
    "-source",
    "1.8",
]

java_library(
    name = "util",
    srcs = glob(
        ["util/**/*.java"],
    ),
    javacopts = JAVACOPTS,
    deps = [
        "//java/com/google/copybara/exception",
        "//third_party:guava",
        "//third_party:re2j",
    ],
)

java_library(
    name = "api",
    srcs = glob(
        ["api/**/*.java"],
    ),
    javacopts = JAVACOPTS,
    deps = [
        "//java/com/google/copybara/checks",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git:core",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/util/console",
        "//third_party:flogger",
        "//third_party:google_http_client",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:starlark",
    ],
)
