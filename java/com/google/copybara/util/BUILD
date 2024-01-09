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

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

CONSISTENCYFILE_SRCS = ["ConsistencyFile.java"]

ORIGINUTIL_SRCS = ["OriginUtil.java"]

java_library(
    name = "consistency",
    srcs = CONSISTENCYFILE_SRCS,
    deps = [
        ":util",
        "//java/com/google/copybara/exception",
        "//third_party:guava",
    ],
)

java_library(
    name = "util",
    srcs = glob(
        ["**/*.java"],
        exclude = CONSISTENCYFILE_SRCS + ORIGINUTIL_SRCS,
    ),
    javacopts = [
        "-Xlint:unchecked",
    ],
    deps = [
        "//java/com/google/copybara:local_parallelizer",
        "//java/com/google/copybara/doc:annotations",  # unuseddeps: keep
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git:environment",
        "//java/com/google/copybara/util/console",
        "//third_party:autovalue",
        "//third_party:error_prone",
        "//third_party:flogger",
        "//third_party:google_http_client",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:protobuf_lite",
        "//third_party:re2j",
        "//third_party:shell",
        "//third_party:starlark",
    ],
)

java_library(
    name = "origin_util",
    srcs = ORIGINUTIL_SRCS,
    javacopts = [
        "-Xlint:unchecked",
    ],
    deps = [
        ":util",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:shell",
    ],
)
