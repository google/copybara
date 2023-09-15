#!/bin/bash -e

./cloudbuild_setup.sh

log "Running 'bazel $@'"
time bazel "$@"

log "Done"
