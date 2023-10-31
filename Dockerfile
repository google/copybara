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

ARG BAZEL_VERSION=6.0.0
FROM gcr.io/bazel-public/bazel:${BAZEL_VERSION} AS build
COPY . .
RUN bazel build //java/com/google/copybara:copybara_deploy.jar

FROM golang:latest AS buildtools
RUN go install github.com/bazelbuild/buildtools/buildozer@latest
RUN go install github.com/bazelbuild/buildtools/buildifier@latest

FROM openjdk:11-jre-slim
# Install git and cleanup apt cache afterwards to keep image size small
RUN apt-get update && apt-get install -y \
        git \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /home/ubuntu/bazel-bin/java/com/google/copybara/copybara_deploy.jar /opt/copybara/
COPY --from=buildtools /go/bin/buildozer /go/bin/buildifier /usr/bin/
COPY .docker/copybara /usr/local/bin/copybara
ENTRYPOINT ["/usr/local/bin/copybara"]
CMD ["migrate", "copy.bara.sky"]
WORKDIR /usr/src/app
