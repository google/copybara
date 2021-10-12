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

java_plugin(
    name = "document",
    srcs = [
        "AnnotationProcessor.java",
    ] + glob([
        "annotations/**/*.java",
    ]),
    javacopts = [
        "-Xlint:unchecked",
        "-source",
        "1.8",
    ],
    processor_class = "com.google.copybara.doc.AnnotationProcessor",
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//third_party:autocommon",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:starlark",
    ],
)

java_library(
    name = "generator-lib",
    srcs = [
        "DocBase.java",
        "Generator.java",
        "MarkdownRenderer.java",
        "ModuleLoader.java",
    ],
    deps = [
        ":annotations",
        "//java/com/google/copybara/jcommander:converters",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:re2j",
        "//third_party:starlark",
    ],
)

java_library(
    name = "annotations",
    srcs = glob(["annotations/**/*.java"]),
    exported_plugins = [":document"],
    javacopts = [
        "-Xlint:unchecked",
        "-source",
        "1.8",
    ],
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//third_party:guava",
    ],
)
