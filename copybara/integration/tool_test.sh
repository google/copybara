#!/usr/bin/env bash

source third_party/bazel/bashunit/unittest.bash

function run_git() {
   git "$@" > $TEST_log 2>&1 || fail "Error running git"
}

# A log configuration that outputs to the console, so that we can check the log easier
log_config=$(mktemp)
cat > $log_config <<'EOF'
handlers=java.util.logging.ConsoleHandler
java.util.logging.SimpleFormatter.format=%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n
EOF

function copybara() {
  $TEST_SRCDIR/copybara/java/com/google/copybara/copybara \
      --jvm_flag=-Djava.util.logging.config.file=$log_config "$@" > $TEST_log 2>&1 \
      && return

  res=$?
  printf 'Copybara process returned error %d:\n' $res
  cat $TEST_log
  return $res
}

function set_up() {
   # Avoid reusing the same directory for each tests so that we don't
   # share state between tests.
   cd "$(mktemp -d)"
   # set XDG_CACHE_HOME so that we have a writeable place for our caches
   export XDG_CACHE_HOME="$(mktemp -d)"
   # An early check to avoid confusing test failures
   git version || fail "Git doesn't seem to be installed. Cannot test without git command."

   export HOME="$(mktemp -d)"
   git config --global user.name 'Bara Kopi'
   git config --global user.email 'bara@kopi.com'
}

function temp_dir() {
  echo "$PWD/$(mktemp -d $1.XXXXXXXXXX)"
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
  remote=$(temp_dir remote)
  repo_storage=$(temp_dir storage)
  workdir=$(temp_dir workdir)
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
workflows:
  - origin: !GitOrigin
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
  copybara test.copybara --git-repo-storage "$repo_storage" \
      --work-dir $workdir

  expect_log "Running Copybara for config 'cbtest', workflow 'default' (SQUASH).*repoUrl=file://$remote"
  expect_log 'Transform: Replace food'
  expect_log 'apply s/\\Qfood\\E/drink/ to .*/test.txt$'
  expect_log 'apply s/\\Qfood\\E/drink/ to .*/subdir/test.txt$'
  expect_not_log 'apply .* to .*/subdir$'
  expect_log 'Transform: Replace f\${os}o'
  expect_log 'Exporting .* to:'

  # Make sure we don't get detached head warnings polluting the log.
  expect_not_log "You are in 'detached HEAD' state"

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

  copybara test.copybara --git-repo-storage "$repo_storage" \
    --work-dir $workdir

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
  repo=$(temp_dir repo)
  cd $repo
  run_git init . --bare > $TEST_log 2>&1 || fail "Cannot create repo"
  run_git --work-tree=$(mktemp -d) commit --allow-empty -m "Empty repo" \
    > $TEST_log 2>&1 || fail "Cannot commit to empty repo"
  echo $repo
}

function prepare_glob_tree() {
  remote=$(temp_dir remote)
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
workflows:
  - origin: !GitOrigin
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
  copybara test.copybara
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
  remote=$(temp_dir remote)
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
workflows:
  - origin: !GitOrigin
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
  copybara test.copybara

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
  remote=$(temp_dir remote)
  repo_storage=$(temp_dir storage)
  workdir=$(temp_dir workdir)

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
workflows:
  - origin: !GitOrigin
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

  copybara destination/test.copybara --git-repo-storage "$repo_storage" \
    --work-dir $workdir --folder-dir destination

  [[ -f destination/test.txt ]] || fail "test.txt should exist"
  [[ -f destination/test.copybara ]] || fail "test.copybara should exist"
  [[ -f destination/keepme.keep ]] || fail "keepme.keep should exist"
  [[ -f destination/folder/keepme.keep ]] || fail "folder/keepme.keep should exist"
  [[ ! -f destination/dontkeep.txt ]] || fail "dontkeep.txt should be deleted"
}

function test_invalid_transformations_in_config() {
  cat > test.copybara <<EOF
name: "cbtest-invalid-xform"
workflows:
  - transformations: [42]
EOF
  copybara test.copybara origin/master && fail "Should fail"
  expect_log "sequence field 'transformations' expects elements of type 'Transformation', but transformations\[0\] is of type 'integer' (value = 42)"
}

function test_command_help_flag() {
  copybara --help
  expect_log 'Usage: copybara \[options\]'
  expect_log 'Example:'
}

function test_command_too_few_args() {
  copybara && fail "Should fail"
  expect_log 'Expected at least a configuration file.'
  expect_log 'Usage: copybara \[options\] CONFIG_PATH \[SOURCE_REF\]'
}

function test_command_too_many_args() {
  copybara config origin/master unexpected && fail "Should fail"
  expect_log "Expect at most two arguments."
  expect_log 'Usage: copybara \[options\] CONFIG_PATH \[SOURCE_REF\]'
}

function test_config_not_found() {
  copybara not_existent_file origin/master && fail "Should fail"
  expect_log "Config file 'not_existent_file' cannot be found."
}

run_suite "Integration tests for Copybara code sharing tool."
