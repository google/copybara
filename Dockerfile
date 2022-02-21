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

FROM gcr.io/cloud-builders/bazel:latest AS build
COPY . .
RUN ./cloudbuild.sh build //java/com/google/copybara:copybara_deploy.jar
RUN mkdir -p /tmp/copybara && \
    cp bazel-bin/java/com/google/copybara/copybara_deploy.jar /tmp/copybara/

FROM golang:latest AS buildtools
RUN go get github.com/bazelbuild/buildtools/buildozer
RUN go get github.com/bazelbuild/buildtools/buildifier

FROM openjdk:11-jre-slim
WORKDIR /usr/src/app
ENV COPYBARA_CONFIG=copy.bara.sky \
    COPYBARA_SUBCOMMAND=migrate \
    COPYBARA_OPTIONS='' \
    COPYBARA_WORKFLOW=default \
    COPYBARA_SOURCEREF=''
COPY --from=build /tmp/copybara/ /opt/copybara/
COPY --from=buildtools /go/bin/buildozer /go/bin/buildifier /usr/bin/
COPY .docker/entrypoint.sh /usr/local/bin/copybara
RUN chmod +x /usr/local/bin/copybara
RUN apt-get update && \
    apt-get install -y git && \
    apt-get clean
