#!/bin/bash

set -exo 

bazel build //java/com/google/copybara:copybara_deploy.jar

cp -f bazel-bin/java/com/google/copybara/copybara_deploy.jar ../core-stack

../core-stack/vehicle_os/docker/dev/in_docker.sh -nobringup direct_noninteractive sudo cp -f /mosaic/copybara_deploy.jar /opt/copybara/copybara_deploy.jar
