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

function test_transformations_in_config() {
  cat > test.copybara <<EOF
name: "cbtest"
repository: "http://www.example.com"
transformations:
  - !ReplaceRegex
    regex:       food
    replacement: drink
  - !ReplaceRegex
    regex:       f(o+)o
    replacement: bar\1
EOF
  $copybara test.copybara master > $TEST_log 2>&1
  expect_log 'Running Copybara for cbtest \[http://www.example.com ref:master\]'
  expect_log 'transforming:.*ReplaceRegex.*drink'
  expect_log 'transforming:.*ReplaceRegex.*bar'
}

function test_invalid_transformations_in_config() {
  cat > test.copybara <<EOF
name: "cbtest-invalid-xform"
repository: "http://www.example.com"
transformations: [42]
EOF
  $copybara test.copybara master > $TEST_log 2>&1 && fail "Should fail"
  expect_log 'Object parsed from Yaml is not a recognized Transformation: 42'
}

function test_command_help_flag() {
  $copybara --help > $TEST_log 2>&1
  expect_log 'Usage: copybara \[options\]'
  expect_log 'Example:'
}

function test_command_too_few_args() {
  $copybara master > $TEST_log 2>&1 && fail "Should fail"
  expect_log 'Expect exactly two arguments.'
  expect_log 'Usage: copybara \[options\] CONFIG_PATH SOURCE_REF'
}

function test_command_too_many_args() {
  $copybara config master unexpected > $TEST_log 2>&1 && fail "Should fail"
  expect_log 'Expect exactly two arguments.'
  expect_log 'Usage: copybara \[options\] CONFIG_PATH SOURCE_REF'
}

function test_config_not_found() {
  $copybara not_existent_file master > $TEST_log 2>&1 && fail "Should fail"
  expect_log "Config file 'not_existent_file' cannot be found."
}

run_suite "Integration tests for Copybara code sharing tool."
