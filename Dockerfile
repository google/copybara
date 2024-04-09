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

ARG BAZEL_VERSION=:7.1.0

# Set up buildtools required
FROM golang:latest AS buildtools
RUN go install github.com/bazelbuild/buildtools/buildozer@latest
RUN go install github.com/bazelbuild/buildtools/buildifier@latest


# Set up bazel env
FROM gcr.io/bazel-public/bazel${BAZEL_VERSION} AS build
WORKDIR .
USER root

COPY --chown=ubuntu:root . . 
RUN apt-get update --fix-missing
RUN apt-get install -y openjdk-17-jdk openjdk-17-jre git locales mercurial lsb-release software-properties-common quilt
RUN add-apt-repository ppa:git-core/ppa -y
RUN apt-get install -y git
RUN apt-get update 

# cleanup apt cache afterwards to keep image size small
RUN rm -rf /var/lib/apt/lists/*
# Bazel does not allow running as root
USER ubuntu
RUN bazel build //java/com/google/copybara:copybara_deploy.jar --java_language_version=11 --tool_java_language_version=11 --java_runtime_version=remotejdk_11
USER root
RUN mkdir -p /opt/copybara && cp /home/ubuntu/bazel-bin/java/com/google/copybara/copybara_deploy.jar /opt/copybara/copybara_deploy.jar -r
COPY --from=buildtools /go/bin/buildozer /go/bin/buildifier /usr/bin/
USER ubuntu

COPY .docker/copybara /usr/local/bin/copybara
ENTRYPOINT ["/usr/local/bin/copybara"]
CMD ["migrate", "copy.bara.sky"]
WORKDIR /usr/src/app

