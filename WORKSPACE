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

workspace(name = "copybara")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//third_party:bazel.bzl", "bazel_sha256", "bazel_version")

RULES_JVM_EXTERNAL_TAG = "3.0"

RULES_JVM_EXTERNAL_SHA = "62133c125bf4109dfd9d2af64830208356ce4ef8b165a6ef15bbff7460b35c3a"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "com.beust:jcommander:1.48",
        "com.google.auto.value:auto-value-annotations:1.6.3",
        "com.google.auto.value:auto-value:1.6.3",
        "com.google.auto:auto-common:0.10",
        "com.google.code.findbugs:jsr305:3.0.2",
        "com.google.code.gson:gson:jar:2.8.5",
        "com.google.flogger:flogger-system-backend:0.3.1",
        "com.google.flogger:flogger:0.3.1",
        "com.google.guava:failureaccess:1.0.1",
        "com.google.guava:guava-testlib:27.1-jre",
        "com.google.guava:guava:27.1-jre",
        "com.google.http-client:google-http-client-gson:jar:1.27.0",
        "com.google.http-client:google-http-client-test:jar:1.27.0",
        "com.google.http-client:google-http-client:jar:1.27.0",
        "com.google.jimfs:jimfs:1.1",
        "com.google.re2j:re2j:1.2",
        "com.google.truth:truth:0.45",
        "com.googlecode.java-diff-utils:diffutils:1.3.0",
        "commons-codec:commons-codec:jar:1.11",
        "junit:junit:4.13",
        "net.bytebuddy:byte-buddy-agent:1.9.10",
        "net.bytebuddy:byte-buddy:1.9.10",
        "org.mockito:mockito-core:2.28.2",
        "org.objenesis:objenesis:1.0",
    ],
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "io_bazel",
    sha256 = bazel_sha256,
    strip_prefix = "bazel-" + bazel_version,
    url = "https://github.com/bazelbuild/bazel/archive/" + bazel_version + ".zip",
)

# Stuff used by Bazel Starlark syntax package transitively:
http_archive(
    name = "com_google_protobuf",
    sha256 = bazel_sha256,
    strip_prefix = "bazel-" + bazel_version + "/third_party/protobuf/3.6.1",
    url = "https://github.com/bazelbuild/bazel/archive/" + bazel_version + ".zip",
)

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "rules_pkg",
    sha256 = "5bdc04987af79bd27bc5b00fe30f59a858f77ffa0bd2d8143d5b31ad8b1bd71c",
    url = "https://github.com/bazelbuild/rules_pkg/releases/download/0.2.0/rules_pkg-0.2.0.tar.gz",
)

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "rules_java",
    sha256 = "52423cb07384572ab60ef1132b0c7ded3a25c421036176c0273873ec82f5d2b2",
    url = "https://github.com/bazelbuild/rules_java/releases/download/0.1.0/rules_java-0.1.0.tar.gz",
)

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "rules_python",
    sha256 = "f7402f11691d657161f871e11968a984e5b48b023321935f5a55d7e56cf4758a",
    strip_prefix = "rules_python-9d68f24659e8ce8b736590ba1e4418af06ec2552",
    url = "https://github.com/bazelbuild/rules_python/archive/9d68f24659e8ce8b736590ba1e4418af06ec2552.zip",
)

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "rules_cc",
    sha256 = "faa25a149f46077e7eca2637744f494e53a29fe3814bfe240a2ce37115f6e04d",
    strip_prefix = "rules_cc-ea5c5422a6b9e79e6432de3b2b29bbd84eb41081",
    url = "https://github.com/bazelbuild/rules_cc/archive/ea5c5422a6b9e79e6432de3b2b29bbd84eb41081.zip",
)

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "rules_proto",
    sha256 = "7d05492099a4359a6006d1b89284d34b76390c3b67d08e30840299b045838e2d",
    strip_prefix = "rules_proto-9cd4f8f1ede19d81c6d48910429fe96776e567b1",
    url = "https://github.com/bazelbuild/rules_proto/archive/9cd4f8f1ede19d81c6d48910429fe96776e567b1.zip",
)
