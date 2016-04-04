#!/usr/bin/env bash

source $TEST_SRCDIR/third_party/bazel/bashunit/unittest.bash

readonly copybara=$TEST_SRCDIR/java/com/google/copybara/copybara

function run_git() {
   git "$@" > $TEST_log 2>&1 || fail "Error running git"
}

function set_up() {
   # Avoid reusing the same directory for each tests so that we don't
   # share state between tests.
   cd "$(mktemp -d)"
   # An early check to avoid confusing test failures
   git version || fail "Git doesn't seem to be installed. Cannot test without git command."
}

function expect_in_file() {
  local regex="$1"
  local file="$2"
  cat "$file" > $TEST_log || fail "'$file' not found"
  expect_log "$regex" || fail "Cannot find '$regex' in '$file'"
}

function test_git_tracking() {
  remote=$(mktemp -d)
  repo_storage=$(mktemp -d)
  workdir=$(mktemp -d)

  ( cd $remote
    run_git init .
    echo "first version for food and foooooo" > test.txt
    mkdir subdir
    echo "first version" > subdir/test.txt
    run_git add test.txt subdir/test.txt
    run_git commit -m "first commit"
  )

  cat > test.copybara <<EOF
name: "cbtest"
sourceOfTruth: !GitRepository
  url: "file://$remote"
  defaultTrackingRef: "origin/master"
transformations:
  - !ReplaceRegex
    regex:       food
    replacement: drink
  - !ReplaceRegex
    regex:       f(o+)o
    replacement: bar\$1
EOF
  $copybara test.copybara --git_repo_storage "$repo_storage" \
    --work-dir $workdir > $TEST_log 2>&1
  expect_log "Running Copybara for cbtest \[.*file://$remote.*\]"
  expect_log 'transforming:.*ReplaceRegex.*drink'
  expect_log 'apply s/food/drink/ to .*/test.txt$'
  expect_log 'apply s/food/drink/ to .*/subdir/test.txt$'
  expect_not_log 'apply .* to .*/subdir$'
  expect_log 'transforming:.*ReplaceRegex.*bar'

  [[ -f $workdir/test.txt ]] || fail "Checkout was not successful"
  cat $workdir/test.txt > $TEST_log
  expect_log "first version for drink and barooooo"

  # Do a new modification and check that we are tracking the changes to the branch
  ( cd $remote
    echo "second version for food and foooooo" > test.txt
    run_git add test.txt
    run_git commit -m "second commit"
  )

  $copybara test.copybara --git_repo_storage "$repo_storage" \
    --work-dir $workdir > $TEST_log 2>&1

  [[ -f $workdir/test.txt ]] || fail "Checkout was not successful"
  expect_in_file "second version for drink and barooooo" $workdir/test.txt
}

function prepare_glob_tree() {
  remote=$(mktemp -d)
  repo_storage=$(mktemp -d)
  workdir=$(mktemp -d)

  ( cd $remote
    run_git init .
    echo "foo" > test.txt
    echo "foo" > test.java
    mkdir -p folder/subfolder
    echo "foo" > folder/test.txt
    echo "foo" > folder/test.java
    echo "foo" > folder/subfolder/test.txt
    echo "foo" > folder/subfolder/test.java
    run_git add -A
    run_git commit -m "first commit"
  )
}

function test_regex_with_path() {
  prepare_glob_tree

  cat > test.copybara <<EOF
name: "cbtest"
sourceOfTruth: !GitRepository
  url: "file://$remote"
  defaultTrackingRef: "origin/master"
transformations:
  - !ReplaceRegex
    path :       "**.java"
    regex:       foo
    replacement: bar
EOF
  $copybara test.copybara --git_repo_storage "$repo_storage" \
    --work-dir $workdir > $TEST_log 2>&1
  expect_in_file "foo" $workdir/test.txt
  expect_in_file "bar" $workdir/test.java
  expect_in_file "foo" $workdir/folder/test.txt
  expect_in_file "bar" $workdir/folder/test.java
  expect_in_file "foo" $workdir/folder/subfolder/test.txt
  expect_in_file "bar" $workdir/folder/subfolder/test.java
}

function test_git_delete() {
  remote=$(mktemp -d)
  repo_storage=$(mktemp -d)
  workdir=$(mktemp -d)

  ( cd $remote
    run_git init .
    echo "first version for food and foooooo" > test.txt
    mkdir subdir
    echo "first version" > subdir/test.txt
    mkdir subdir2
    echo "first version" > subdir2/test.java
    echo "first version" > subdir2/test.txt
    run_git add -A
    run_git commit -m "first commit"
  )

  cat > test.copybara <<EOF
name: "cbtest"
sourceOfTruth: !GitRepository
  url: "file://$remote"
  defaultTrackingRef: "origin/master"
transformations:
  - !DeletePath
    path: subdir/**
  - !DeletePath
    path: "**/*.java"
EOF
  $copybara test.copybara --git_repo_storage "$repo_storage" \
    --work-dir $workdir > $TEST_log 2>&1

  [[ ! -f $workdir/subdir/test.txt ]] || fail "/subdir/test.txt should be deleted"
  [[ ! -f $workdir/subdir2/test.java ]] || fail "/subdir2/test.java should be deleted"

  [[ -f $workdir/test.txt ]] || fail "/test.txt should not be deleted"
  [[ -d $workdir/subdir ]] || fail "/subdir should not be deleted"
  [[ -d $workdir/subdir2 ]] || fail "/subdir2 should not be deleted"
  [[ -f $workdir/subdir2/test.txt ]] || fail "/subdir2/test.txt should not be deleted"
}

function test_invalid_transformations_in_config() {
  cat > test.copybara <<EOF
name: "cbtest-invalid-xform"
transformations: [42]
EOF
  $copybara test.copybara origin/master > $TEST_log 2>&1 && fail "Should fail"
  expect_log 'Object parsed from Yaml is not a recognized Transformation: 42'
}

function test_command_help_flag() {
  $copybara --help > $TEST_log 2>&1
  expect_log 'Usage: copybara \[options\]'
  expect_log 'Example:'
}

function test_command_too_few_args() {
  $copybara > $TEST_log 2>&1 && fail "Should fail"
  expect_log 'Expected at least a configuration file.'
  expect_log 'Usage: copybara \[options\] CONFIG_PATH \[SOURCE_REF\]'
}

function test_command_too_many_args() {
  $copybara config origin/master unexpected > $TEST_log 2>&1 && fail "Should fail"
  expect_log "Expect at most two arguments."
  expect_log 'Usage: copybara \[options\] CONFIG_PATH \[SOURCE_REF\]'
}

function test_config_not_found() {
  $copybara not_existent_file origin/master > $TEST_log 2>&1 && fail "Should fail"
  expect_log "Config file 'not_existent_file' cannot be found."
}

run_suite "Integration tests for Copybara code sharing tool."
