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

sh_test(
    name = "reference_doc_test",
    srcs = ["reference_doc_test.sh"],
    data = [
        "//docs:reference.md",
        "//java/com/google/copybara:reference.md",
        "//third_party/bazel/bashunit",
    ],
    visibility = ["//visibility:public"],
)

sh_test(
    name = "tool_test",
    srcs = ["tool_test.sh"],
    data = [
        "//java/com/google/copybara",
        "//third_party/bazel/bashunit",
    ],
    shard_count = 30,
    tags = ["local"],
    visibility = ["//visibility:public"],
)
