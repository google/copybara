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

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "guava",
    artifact = "com.google.guava:guava:21.0",
    sha1 = "3a3d111be1be1b745edfa7d91678a12d7ed38709",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "guava_testlib",
    artifact = "com.google.guava:guava-testlib:21.0",
    sha1 = "1ec77c45666cf17da76cd80725194148a8ffc440",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "autocommon",
    artifact = "com.google.auto:auto-common:0.6",
    sha1 = "cf7212b0f8bfef12657b942df8f4f2cf032d3f41",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "autovalue",
    artifact = "com.google.auto.value:auto-value:1.2",
    sha1 = "6873fed014fe1de1051aae2af68ba266d2934471",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "jcommander",
    artifact = "com.beust:jcommander:1.48",
    sha1 = "bfcb96281ea3b59d626704f74bc6d625ff51cbce",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:0.28",
    sha1 = "0a388c7877c845ff4b8e19689dda5ac9d34622c4",
)

# LICENSE: The Apache Software License, Version 2.0
# Required by mockito
maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:1.0",
    sha1 = "9b473564e792c2bdf1449da1f0b1b5bff9805704",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:1.9.5",
    sha1 = "c3264abeea62c4d2f367e21484fbb40c7e256393",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "jimfs",
    artifact = "com.google.jimfs:jimfs:1.0",
    sha1 = "edd65a2b792755f58f11134e76485a928aab4c97",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "jsr305",
    artifact = "com.google.code.findbugs:jsr305:3.0.1",
    sha1 = "f7be08ec23c21485b9b5a1cf1654c2ec8c58168d",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "google_http_client",
    artifact = "com.google.http-client:google-http-client:jar:1.22.0",
    sha1 = "d441fc58329c4a4c067acec04ac361627f66ecc8",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "google_http_client_test",
    artifact = "com.google.http-client:google-http-client-test:jar:1.22.0",
    sha1 = "a0450f3724614982e70e53979a37141c916b6536",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "google_http_client_gson",
    artifact = "com.google.http-client:google-http-client-gson:jar:1.22.0",
    sha1 = "826b874fa410a8135b7094c469951bf0dadb0c10",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:jar:2.8.1",
    sha1 = "02a8e0aa38a2e21cb39e2f5a7d6704cbdc941da0",
)

new_http_archive(
    name = "cram",
    build_file = "BUILD.cram",
    sha256 = "7da7445af2ce15b90aad5ec4792f857cef5786d71f14377e9eb994d8b8337f2f",
    strip_prefix = "cram-0.7/",
    url = "https://pypi.python.org/packages/source/c/cram/cram-0.7.tar.gz",
)

# LICENSE: Common Public License 1.0
maven_jar(
    name = "junit",
    artifact = "junit:junit:4.11",
    sha1 = "4e031bb61df09069aeb2bffb4019e7a5034a4ee0",
)

bazel_version="a664a5118e552504ba7962e1cccfea43b51ef28e"

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "io_bazel",
    url = "https://github.com/bazelbuild/bazel/archive/" + bazel_version + ".zip",
    strip_prefix = "bazel-" + bazel_version,
)

http_archive(
    name = "com_google_protobuf",
    url = "https://github.com/bazelbuild/bazel/archive/" + bazel_version + ".zip",
    strip_prefix = "bazel-" + bazel_version + "/third_party/protobuf/3.2.0",
)

new_http_archive(
    name = "com_google_protobuf_java",
    url = "https://github.com/bazelbuild/bazel/archive/" + bazel_version + ".zip",
    strip_prefix = "bazel-"+ bazel_version + "/third_party/protobuf/3.2.0",
    # We cannot use this because of https://github.com/bazelbuild/bazel/issues/3364 :
    # build_file = "@com_google_protobuf_java//:com_google_protobuf_java.BUILD"
    build_file = "@com_google_protobuf//:com_google_protobuf_java.BUILD"
)

# LICENSE: New BSD
maven_jar(
    name = "re2j",
    artifact = "com.google.re2j:re2j:1.1",
    sha1 = "d716952ab58aa4369ea15126505a36544d50a333",
)
