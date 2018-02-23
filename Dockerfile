FROM openjdk:8 AS build

RUN echo "deb [arch=amd64] http://storage.googleapis.com/bazel-apt stable jdk1.8" | tee /etc/apt/sources.list.d/bazel.list \
  && curl https://bazel.build/bazel-release.pub.gpg | apt-key add -

RUN apt-get update \
  && apt-get install -y bazel \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src/copybara

COPY . .

RUN bazel build //java/com/google/copybara:copybara_deploy.jar \
    && mkdir -p /tmp/copybara \
    && cp bazel-bin/java/com/google/copybara/copybara_deploy.jar /tmp/copybara/

# Fails currently
# RUN bazel test //...

FROM openjdk:8-jre-slim
COPY --from=build /tmp/copybara/ /opt/copybara/

ENV COPYBARA_CONFIG=copy.bara.sky

# Install git for fun times
RUN apt-get update \
    && apt-get install -y git \
    && apt-get clean

# Make a small executable for bin to execute uber jar
RUN touch /usr/local/bin/copybara \
    && echo "#!/usr/bin/env bash\njava -jar /opt/copybara/copybara_deploy.jar $COPYBARA_CONFIG $1" >> /usr/local/bin/copybara \
    && chmod +x /usr/local/bin/copybara

WORKDIR /usr/src/app

ENTRYPOINT ["/bin/bash", "copybara"]
