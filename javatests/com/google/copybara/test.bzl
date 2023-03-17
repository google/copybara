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

"""
This file contains macros used in the copybara project.
"""

def all_tests(tests, deps, name = "all_tests", tags = [], shard_count = 1, data = [], env = {}):
    """
    This macro encapsulates the java_test rules used in the copybara project.

    Args:
          tests: list[string], List of test files to create java_tests from.
          deps: list[string], List of dependencies.
          name: name of the rule
          tags: list[string], Test categorization.
          shard_count: int, Number of parallel shards to use to run the test.
          data: list[string], Files needed at runtime by java tests.
          env: dict[string, string], Test environment variables.
    """
    for file in tests:
        # TODO(malcon): Skip files not ending as *Test.java
        relative_target = file[:-5]
        suffix = relative_target.replace("/", ".")
        pos = native.package_name().rfind("javatests/") + len("javatests/")
        test_class = native.package_name()[pos:].replace("/", ".") + "." + suffix
        native.java_test(
            name = file[:-5],
            srcs = [file],
            javacopts = [
                "-Xlint:unchecked",
                "-Xep:FutureReturnValueIgnored:OFF",
            ],
            test_class = test_class,
            data = data,
            deps = deps + [
                # These deps are automatically included with Bazel, but not with the
                # internal BUILD system. So add them explicitly here.
                "//third_party:guava",
                "//third_party:jsr305",
                "//third_party:junit",
            ],
            tags = tags,
            shard_count = shard_count,
            env = env,
        )
