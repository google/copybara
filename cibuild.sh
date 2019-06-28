#!/bin/bash -e

# DISCLAIMER: This is not the Google Cloud Build script
# that you are looking for.


function log() {
  d=$(date +'%Y-%m-%d %H:%M:%S')
  echo $d" "$1
}

log "Running Copybara tests"

log "Fetching dependencies"
log "Running apt-get update --fix-missing"
sudo apt-get update --fix-missing
# Mercurial does not have an up-to-date .deb package
# The official release needs to be installed with pip.
sudo apt-get -y install python-pip
sudo apt-get install locales
sudo pip install --ignore-installed --upgrade mercurial

log "Extracting Bazel"
# Only because first time it extracts the installation
bazel version

echo "-----------------------------------"
echo "Versions:"
hg --version | grep "(version" | sed 's/.*[(]version \([^ ]*\)[)].*/Mercurial: \1/'
git --version | sed 's/git version/Git:/'
bazel version | grep "Build label" | sed 's/Build label:/Bazel:/'
echo "-----------------------------------"

log "Setting Locale"
export LANGUAGE=en_US.UTF-8
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
locale-gen en_US.UTF-8
sudo update-locale LANG=en_US.UTF-8

log "Running Bazel"
bazel test ... --test_output=errors --sandbox_tmpfs_path=/tmp -j 1000

log "Done"
