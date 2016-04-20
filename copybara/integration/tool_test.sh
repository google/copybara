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

function check_copybara_rev_id() {
   local repo="$1"
   local origin_id="$2"
   ( cd $repo
     run_git log master -1 > $TEST_log
     expect_log "Copybara-RevId: $origin_id"
   )
}

function test_git_tracking() {
  remote=$(mktemp -d --tmpdir remote.XXXXXXXXXX)
  repo_storage=$(mktemp -d --tmpdir storage.XXXXXXXXXX)
  workdir=$(mktemp -d --tmpdir workdir.XXXXXXXXXX)
  destination=$(empty_git_bare_repo)

  pushd $remote
  run_git init .
  echo "first version for food and foooooo" > test.txt
  mkdir subdir
  echo "first version" > subdir/test.txt
  run_git add test.txt subdir/test.txt
  run_git commit -m "first commit"
  first_commit=$(run_git rev-parse HEAD)
  popd

  cat > test.copybara <<EOF
name: "cbtest"
origin: !GitOrigin
  url: "file://$remote"
  defaultTrackingRef: "master"
destination: !GitDestination
  url: "file://$destination"
  pullFromRef: "master"
  pushToRef: "master"
transformations:
  - !Replace
    before:       food
    after:        drink
  - !Replace
    before:       f\${os}o
    after:        bar\${os}
    regexGroups:
      os: "o+"
EOF
  $copybara test.copybara --git-repo-storage "$repo_storage" \
    --work-dir $workdir > $TEST_log 2>&1
  expect_log "Running Copybara for cbtest \[.*file://$remote.*\]"
  expect_log 'transforming:.*Replace.*drink'
  expect_log 'apply s/\\Qfood\\E/drink/ to .*/test.txt$'
  expect_log 'apply s/\\Qfood\\E/drink/ to .*/subdir/test.txt$'
  expect_not_log 'apply .* to .*/subdir$'
  expect_log 'transforming:.*Replace.*bar'
  expect_log 'Exporting .* to:'

  [[ -f $workdir/test.txt ]] || fail "Checkout was not successful"
  cat $workdir/test.txt > $TEST_log
  expect_log "first version for drink and barooooo"

  check_copybara_rev_id "$destination" "$first_commit"

  # Do a new modification and check that we are tracking the changes to the branch
  pushd $remote
  echo "second version for food and foooooo" > test.txt
  run_git add test.txt
  run_git commit -m "second commit"
  second_commit=$(run_git rev-parse HEAD)
  popd

  $copybara test.copybara --git-repo-storage "$repo_storage" \
    --work-dir $workdir > $TEST_log 2>&1

  [[ -f $workdir/test.txt ]] || fail "Checkout was not successful"
  expect_in_file "second version for drink and barooooo" $workdir/test.txt

  check_copybara_rev_id "$destination" "$second_commit"

  ( cd $destination
    run_git show master > $TEST_log
  )

  expect_log "-first version for drink"
  expect_log "+second version for drink and barooooo"
}

function empty_git_bare_repo() {
  repo=$(mktemp -d --tmpdir repo.XXXXXXXXXX)
  cd $repo
  run_git init . --bare > $TEST_log 2>&1 || fail "Cannot create repo"
  run_git --work-tree=$(mktemp -d) commit --allow-empty -m "Empty repo" \
    > $TEST_log 2>&1 || fail "Cannot commit to empty repo"
  echo $repo
}

function prepare_glob_tree() {
  remote=$(mktemp -d --tmpdir remote.XXXXXXXXXX)
  destination=$(empty_git_bare_repo)

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
origin: !GitOrigin
  url: "file://$remote"
  defaultTrackingRef: "master"
destination: !GitDestination
  url: "file://$destination"
  pullFromRef: master
  pushToRef: master
transformations:
  - !Replace
    path:   "**.java"
    before: foo
    after:  bar
EOF
  $copybara test.copybara > $TEST_log 2>&1
  ( cd $(mktemp -d)
    run_git clone $destination .
    expect_in_file "foo" test.txt
    expect_in_file "bar" test.java
    expect_in_file "foo" folder/test.txt
    expect_in_file "bar" folder/test.java
    expect_in_file "foo" folder/subfolder/test.txt
    expect_in_file "bar" folder/subfolder/test.java
  )
}

function test_git_delete() {
  remote=$(mktemp -d --tmpdir remote.XXXXXXXXXX)
  destination=$(empty_git_bare_repo)

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
origin: !GitOrigin
  url: "file://$remote"
  defaultTrackingRef: "master"
destination: !GitDestination
  url: "file://$destination"
  pullFromRef: master
  pushToRef: master
transformations:
  - !DeletePath
    path: subdir/**
  - !DeletePath
    path: "**/*.java"
EOF
  $copybara test.copybara > $TEST_log 2>&1

  ( cd $(mktemp -d)
    run_git clone $destination .
    [[ ! -f subdir/test.txt ]] || fail "/subdir/test.txt should be deleted"
    [[ ! -f subdir2/test.java ]] || fail "/subdir2/test.java should be deleted"

    [[ -f test.txt ]] || fail "/test.txt should not be deleted"
    [[ ! -d subdir ]] || fail "/subdir should be deleted"
    [[ -d subdir2 ]] || fail "/subdir2 should not be deleted"
    [[ -f subdir2/test.txt ]] || fail "/subdir2/test.txt should not be deleted"
  )
}

function test_local_dir_destination() {
  remote=$(mktemp -d --tmpdir remote.XXXXXXXXXX)
  repo_storage=$(mktemp -d --tmpdir storage.XXXXXXXXXX)
  workdir=$(mktemp -d --tmpdir workdir.XXXXXXXXXX)

  ( cd $remote
    run_git init .
    echo "first version for food and foooooo" > test.txt
    echo "first version" > test.txt
    run_git add test.txt
    run_git commit -m "first commit"
  )
  mkdir destination

  cat > destination/test.copybara <<EOF
name: "cbtest"
origin: !GitOrigin
  url: "file://$remote"
  defaultTrackingRef: "master"
destination: !FolderDestination
  excludePathsForDeletion:
    - "test.copybara"
    - "**.keep"
EOF

  touch destination/keepme.keep
  mkdir -p destination/folder
  touch destination/folder/keepme.keep
  touch destination/dontkeep.txt

  $copybara destination/test.copybara --git-repo-storage "$repo_storage" \
    --work-dir $workdir --folder-dir destination> $TEST_log 2>&1

  [[ -f destination/test.txt ]] || fail "test.txt should exist"
  [[ -f destination/test.copybara ]] || fail "test.copybara should exist"
  [[ -f destination/keepme.keep ]] || fail "keepme.keep should exist"
  [[ -f destination/folder/keepme.keep ]] || fail "folder/keepme.keep should exist"
  [[ ! -f destination/dontkeep.txt ]] || fail "dontkeep.txt should be deleted"
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
