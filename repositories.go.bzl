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

load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")
load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies", "go_repository")

def copybara_go_repositories():
    go_rules_dependencies()

    go_register_toolchains()

    gazelle_dependencies()

    # LICENSE: The Apache Software License, Version 2.0
    maybe(
        go_repository,
        name = "skylark_syntax",
        importpath = "go.starlark.net",
        sum = "h1:Qoe+9POtDT51UBQ8XEnS9QKeHDQzEl2QRh3eok9R4aw=",
        version = "v0.0.0-20200203144150-6677ee5c7211",
    )
