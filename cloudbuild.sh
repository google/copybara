#!/bin/bash

function log() {
  d=$(date +'%Y-%m-%d %H:%M:%S')
  echo $d" "$1
}

log "Running Copybara tests"

log "Fetching dependencies"
# Mercurial does not have an up-to-date .deb package
# The official release needs to be installed with pip.
apt-get -y install python-pip
pip install mercurial

log "Extracting Bazel"
# Only because first time it extracts the installation
bazel version

echo "-----------------------------------"
echo "Versions:"
hg --version | grep "(version" | sed 's/.*[(]version \([^ ]*\)[)].*/Mercurial: \1/'
git --version | sed 's/git version/Git:/'
bazel version | grep "Build label" | sed 's/Build label:/Bazel:/'
echo "-----------------------------------"

log "Running Bazel"

bazel "$@"

log "Done"
