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

licenses(["notice"])  # Apache 2.0

java_library(
    name = "base",
    srcs = [
        "Config.java",
        "ConfigFile.java",
        "ConfigValidator.java",
        "LabelsAwareModule.java",
        "MapConfigFile.java",
        "Migration.java",
        "OptionsAwareModule.java",
        "PathBasedConfigFile.java",
        "SkylarkUtil.java",
    ],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:general_options",
        "//java/com/google/copybara:options",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:skylark-lang",
    ],
)

java_library(
    name = "global_migrations",
    srcs = ["GlobalMigrations.java"],
    deps = [
        ":base",
        "//third_party:jsr305",
        "//third_party:skylark-lang",
    ],
)

java_library(
    name = "parser",
    srcs = [
        "CapturingConfigFile.java",
        "SkylarkParser.java",
    ],
    deps = [
        ":base",
        ":global_migrations",
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:options",
        "//java/com/google/copybara/exception",
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:skylark-lang",
    ],
)