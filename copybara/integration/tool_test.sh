#!/usr/bin/env bash

source $TEST_SRCDIR/third_party/bazel/bashunit/unittest.bash

readonly copybara=$TEST_SRCDIR/java/com/google/copybara/copybara

function test_command() {
  $copybara > $TEST_log 2>&1
  expect_log "Best transformer is the identity transformer!"
}

run_suite "Integration tests for Copybara code sharing tool."
