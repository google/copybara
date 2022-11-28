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
    name = "abstract_gitapi_test",
    testonly = 1,
    srcs = [
        "AbstractGitHubApiTest.java",
        "AbstractGitHubGraphQLApiTest.java",
    ],
    data = glob(["*_testdata.json"]),
    deps = [
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/git/github:api",
        "//java/com/google/copybara/profiler",
        "//java/com/google/copybara/testing/git",
        "//third_party:guava",
        "//third_party:junit",
        "//third_party:mockito",
        "//third_party:truth",
    ],
)
