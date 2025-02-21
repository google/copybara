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
load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven")

COPYBARA_MAVEN_ARTIFACTS = [
    maven.artifact("com.github.stephenc.jcip", "jcip-annotations", "1.0-1"),
    maven.artifact("com.google.auto.service", "auto-service", "1.1.1"),
    maven.artifact("com.google.auto.service", "auto-service-annotations", "1.1.1"),
    maven.artifact("com.google.auto.value", "auto-value-annotations", "1.10.2"),
    maven.artifact("com.google.auto.value", "auto-value", "1.10.2"),
    maven.artifact("com.google.auto", "auto-common", "1.2.2"),
    maven.artifact("com.google.code.findbugs", "jsr305", "3.0.2", neverlink = False),
    maven.artifact("com.google.code.gson", "gson", "2.10.1"),
    maven.artifact("com.google.errorprone", "error_prone_type_annotations", "2.20.0"),
    maven.artifact("com.google.flogger", "flogger-system-backend", "0.7.4"),
    maven.artifact("com.google.flogger", "flogger", "0.7.4"),
    maven.artifact("com.google.flogger", "google-extensions", "0.7.4"),
    maven.artifact("com.google.guava", "failureaccess", "1.0.1"),
    maven.artifact("com.google.guava", "guava-testlib", "33.0.0-jre", testonly = True),
    maven.artifact("com.google.guava", "guava", "33.0.0-jre"),
    maven.artifact("com.google.http-client", "google-http-client-gson", "1.43.3"),
    maven.artifact("com.google.http-client", "google-http-client-test", "1.43.3", testonly = True),
    maven.artifact("com.google.http-client", "google-http-client", "1.43.3"),
    maven.artifact("com.google.jimfs", "jimfs", "1.2"),
    maven.artifact("com.google.re2j", "re2j", "1.7"),
    maven.artifact("com.google.testparameterinjector", "test-parameter-injector", "1.8", testonly = True),
    maven.artifact("com.google.truth", "truth", "1.3.0", testonly = True),
    maven.artifact("com.google.truth.extensions", "truth-java8-extension", "1.3.0", testonly = True),
    maven.artifact("com.googlecode.java-diff-utils", "diffutils", "1.3.0"),
    maven.artifact("com.ryanharter.auto.value", "auto-value-gson-extension", "1.3.1"),
    maven.artifact("com.ryanharter.auto.value", "auto-value-gson-runtime", "1.3.1"),
    maven.artifact("com.ryanharter.auto.value", "auto-value-gson-factory", "1.3.1"),
    maven.artifact("commons-codec", "commons-codec", "1.16.0"),
    maven.artifact("junit", "junit", "4.13.2", testonly = True),
    maven.artifact("net.bytebuddy", "byte-buddy-agent", "1.14.5", testonly = True),
    maven.artifact("net.bytebuddy", "byte-buddy", "1.14.5", testonly = True),
    maven.artifact("org.mockito", "mockito-core", "4.5.1", testonly = True),
    maven.artifact("org.objenesis", "objenesis", "1.0", testonly = True),
    maven.artifact("org.apache.commons", "commons-compress", "1.25.0"),
    maven.artifact("org.apache.tomcat", "tomcat-annotations-api", "8.0.5"),
    maven.artifact("org.apache.velocity", "velocity", "1.7"),
    maven.artifact("org.jcommander", "jcommander", "2.0"),
    maven.artifact("org.jsoup", "jsoup", "1.16.1"),
    maven.artifact("org.tomlj", "tomlj", "1.1.0"),
    maven.artifact("org.tukaani", "xz", "1.9"),
    maven.artifact("com.sun.mail", "javax.mail", "1.5.6"),
]

COPYBARA_MAVEN_ARTIFACT_ADDITIONAL_REPOSITORIES = [
    "https://maven.google.com",
]

def copybara_maven_repositories():
    maybe(
        maven_install,
        name = "copybara_maven",
        artifacts = COPYBARA_MAVEN_ARTIFACTS,
        repositories = COPYBARA_MAVEN_ARTIFACT_ADDITIONAL_REPOSITORIES + ["https://repo1.maven.org/maven2"],
    )
    maybe(
        maven_install,
        name = "maven",
        artifacts = COPYBARA_MAVEN_ARTIFACTS,
        repositories = COPYBARA_MAVEN_ARTIFACT_ADDITIONAL_REPOSITORIES + ["https://repo1.maven.org/maven2"],
    )
