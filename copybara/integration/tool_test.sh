#!/usr/bin/env bash

source $TEST_SRCDIR/third_party/bazel/bashunit/unittest.bash

readonly copybara=$TEST_SRCDIR/java/com/google/copybara/copybara

function test_command() {
  cat > test.copybara <<EOF
name: "cbtest"
repository: "http://www.example.com"
EOF
  $copybara test.copybara master > $TEST_log 2>&1
  expect_log 'Running Copybara for cbtest \[http://www.example.com ref:master\]'
}

function test_config_not_found() {
  $copybara not_existent_file master > $TEST_log 2>&1 && fail "Should fail"
  expect_log "Config file 'not_existent_file' cannot be found."
}

run_suite "Integration tests for Copybara code sharing tool."
