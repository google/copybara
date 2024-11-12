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

ARG BAZEL_VERSION=:7.3.2

# Set up buildtools required
FROM golang:latest AS buildtools
RUN go install github.com/bazelbuild/buildtools/buildozer@latest github.com/bazelbuild/buildtools/buildifier@latest

# Set up bazel env
FROM gcr.io/bazel-public/bazel${BAZEL_VERSION} AS build

USER root
RUN apt-get update && \
    apt-get install --no-install-recommends -y openjdk-21-jdk-headless && \
    rm -rf /var/lib/apt/lists/*

# Bazel does not allow running as root
USER ubuntu

WORKDIR /home/ubuntu/
COPY . . 

RUN bazel build //java/com/google/copybara:copybara_deploy.jar --java_language_version=21 --tool_java_language_version=21 --java_runtime_version=remotejdk_21

# Use jammy to drop Python 2
FROM docker.io/eclipse-temurin:17-jre-jammy

RUN apt-get update && \
    apt-get install --no-install-recommends -y git mercurial quilt && \
    rm -rf /var/lib/apt/lists/*

COPY --from=buildtools /go/bin/buildozer /go/bin/buildifier /usr/local/bin/
COPY --from=build /home/ubuntu/bazel-bin/java/com/google/copybara/copybara_deploy.jar /opt/copybara/copybara_deploy.jar
COPY .docker/copybara /usr/local/bin/copybara

ENTRYPOINT ["/usr/local/bin/copybara"]
CMD ["migrate", "copy.bara.sky"]
WORKDIR /usr/src/app
