#!/bin/bash -e

# Build script for Google Cloud Build

function log() {
  d=$(date +'%Y-%m-%d %H:%M:%S')
  echo $d" "$1
}

log "Setting up Copybara test env"

log "Fetching dependencies"
log "Running apt-get update --fix-missing"

apt-get update --fix-missing
apt-get install -y locales
apt-get install -y mercurial
apt-get install -y lsb-release
apt-get install -y software-properties-common
apt install -y openjdk-17-jdk openjdk-17-jre
add-apt-repository ppa:git-core/ppa -y
apt install git -y

apt-get install -y quilt

log "Extracting Bazel"
# Only because first time it extracts the installation
bazel version


echo "-----------------------------------"
echo "Versions:"
lsb_release -a
hg --version | grep "(version" | sed 's/.*[(]version \([^ ]*\)[)].*/Mercurial: \1/'
git --version | sed 's/git version/Git:/'
bazel version | grep "Build label" | sed 's/Build label:/Bazel:/'
java -version
echo "Quilt:"
quilt --version
echo "-----------------------------------"

log "Setting Locale"
export LANGUAGE=en_US.UTF-8
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
locale-gen en_US.UTF-8
update-locale LANG=en_US.UTF-8

log "Setup Complete"
