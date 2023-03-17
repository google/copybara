# Copyright 2022 Google Inc.
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
]

java_library(
    name = "version",
    srcs = glob(["**/*.java"]),
    javacopts = JAVACOPTS,
    deps = [
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/revision",
        "//java/com/google/copybara/templatetoken",
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:starlark",
    ],
)
