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
      --jvm_flag=-Djava.util.logging.config.file=$log_config "$@" \
      --git-repo-storage "$repo_storage" \
      --work-dir "$workdir" > $TEST_log 2>&1 \
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
   export repo_storage=$(temp_dir storage)
   export workdir=$(temp_dir workdir)
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
     expect_log "GitOrigin-RevId: $origin_id"
   )
}

function test_git_tracking() {
  remote=$(temp_dir remote)
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

    cat > copybara.yaml <<EOF
name: "cbtest"
workflows:
  - origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !GitDestination
      url: "file://$destination"
      fetch: "master"
      push: "master"
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
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

  copybara copybara.yaml

  expect_log "Running Copybara for config 'cbtest', workflow 'default' .*repoUrl=file://$remote.*mode=SQUASH"
  expect_log 'Transform Replace food'
  expect_log 'apply s/\\Qfood\\E/drink/ to .*/test.txt$'
  expect_log 'apply s/\\Qfood\\E/drink/ to .*/subdir/test.txt$'
  expect_not_log 'apply .* to .*/subdir$'
  expect_log 'Transform Replace f\${os}o'
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
  second_commit=$(git rev-parse HEAD)
  popd

  copybara copybara.yaml

  [[ -f $workdir/test.txt ]] || fail "Checkout was not successful"
  expect_in_file "second version for drink and barooooo" $workdir/test.txt

  check_copybara_rev_id "$destination" "$second_commit"

  ( cd $destination
    run_git show master > $TEST_log
  )

  expect_log "-first version for drink"
  expect_log "+second version for drink and barooooo"
}
function test_git_iterative() {
  remote=$(temp_dir remote)
  repo_storage=$(temp_dir storage)
  workdir=$(temp_dir workdir)
  destination=$(empty_git_bare_repo)

  pushd $remote
  run_git init .
  commit_one=$(single_file_commit "commit one" file.txt "food fooooo content1")
  commit_two=$(single_file_commit "commit two" file.txt "food fooooo content2")
  commit_three=$(single_file_commit "commit three" file.txt "food fooooo content3")
  commit_four=$(single_file_commit "commit four" file.txt "food fooooo content4")
  commit_five=$(single_file_commit "commit five" file.txt "food fooooo content5")

  popd
    cat > copybara.yaml <<EOF
name: "cbtest"
workflows:
  - origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !GitDestination
      url: "file://$destination"
      fetch: "master"
      push: "master"
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    mode: ITERATIVE
EOF

  copybara copybara.yaml default $commit_three --last-rev $commit_one

  check_copybara_rev_id "$destination" "$commit_three"

  ( cd $destination
    run_git log master~1..master > $TEST_log
  )
  expect_not_log "commit two"
  expect_log "commit three"

  copybara copybara.yaml default $commit_five

  check_copybara_rev_id "$destination" "$commit_five"

  ( cd $destination
    run_git log master~2..master~1 > $TEST_log
  )
  expect_log "commit four"

  ( cd $destination
    run_git log master~1..master > $TEST_log
  )
  expect_log "commit five"
}

function single_file_commit() {
  message=$1
  file=$2
  content=$3
  echo $content > $file
  run_git add $file
  run_git commit -m "$message"
  git rev-parse HEAD
}

function test_get_git_changes() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  pushd $remote
  run_git init .
  commit_one=$(single_file_commit "commit one" file.txt "food fooooo content1")
  commit_two=$(single_file_commit "commit two" file.txt "food fooooo content2")
  commit_three=$(single_file_commit "commit three" file.txt "food fooooo content3")
  commit_four=$(single_file_commit "commit four" file.txt "food fooooo content4")
  commit_five=$(single_file_commit "commit five" file.txt "food fooooo content5")
  popd

    cat > copybara.yaml <<EOF
name: "cbtest"
workflows:
  - origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !GitDestination
      url: "file://$destination"
      fetch: "master"
      push: "master"
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    includeChangeListNotes: true
EOF

  copybara copybara.yaml default $commit_one

  check_copybara_rev_id "$destination" "$commit_one"

  ( cd $destination
    run_git log master~1..master > $TEST_log
  )
  # We only record changes when we know the previous version
  expect_not_log "commit one"

  copybara copybara.yaml default $commit_four

  check_copybara_rev_id "$destination" "$commit_four"

  ( cd $destination
    run_git log master~1..master > $TEST_log
  )
  # "commit one" should not be included because it was migrated before
  expect_not_log "commit one"
  expect_log "$commit_two.*commit two"
  expect_log "$commit_three.*commit three"
  expect_log "$commit_four.*commit four"
  expect_not_log "commit five"

  copybara copybara.yaml default $commit_five --last-rev $commit_three

  check_copybara_rev_id "$destination" "$commit_five"

  ( cd $destination
    run_git log master~1..master > $TEST_log
  )
  # We are forcing to use commit_three as the last migration. This has
  # no effect in squash workflow but it changes the release notes.
  expect_not_log "commit one"
  expect_not_log "commit two"
  expect_not_log "commit three"
  expect_log "$commit_four.*commit four"
  expect_log "$commit_five.*commit five"
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

  cat > copybara.yaml <<EOF
name: "cbtest"
workflows:
  - origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !GitDestination
      url: "file://$destination"
      fetch: master
      push: master
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    transformations:
      - !Replace
        path:   "**.java"
        before: foo
        after:  bar
EOF
  copybara copybara.yaml
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

function test_git_pull_request() {
  sot=$(empty_git_bare_repo)
  public=$(empty_git_bare_repo)

  cat > copybara.yaml <<EOF
name: "cbtest"
workflows:
  - name : "export"
    origin: !GitOrigin
      url: "file://$sot"
      ref: "master"
    destination: !GitDestination
      url: "file://$public"
      fetch: master
      push: master
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
  - name : "import_change"
    mode: CHANGE_REQUEST
    origin: !GitOrigin
      url: "file://$public"
      ref: "master"
    destination: !GitDestination
      url: "file://$sot"
      fetch: master
      push: master
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
EOF

  # Create the SoT repo
  pushd $(mktemp -d)
  run_git clone $sot .
  commit_one=$(single_file_commit "commit one" one.txt "content")
  commit_two=$(single_file_commit "commit two" two.txt "content")
  commit_three=$(single_file_commit "commit three" three.txt "content")
  run_git push
  popd

  copybara copybara.yaml export $commit_two

  # Check that we have exported correctly the tree state as in commit_two
  pushd $(mktemp -d)
  run_git clone $public .
  [[ -f one.txt ]] || fail "one.txt should exist in commit two"
  [[ -f two.txt ]] || fail "two.txt should exist in commit two"
  [[ ! -f three.txt ]] || fail "three.txt should NOT exist in commit two"

  # Create a new change on top of the public version (commit_two)
  pr_request=$(single_file_commit "pull request" pr.txt "content")
  run_git push
  popd

  copybara copybara.yaml import_change $pr_request

  # Check that the SoT contains the change from pr_request but that it has not reverted
  # commit_three (SoT was ahead of public).
  pushd $(mktemp -d)
  run_git clone $sot .
  [[ -f one.txt ]] || fail "one.txt should exist after pr import"
  [[ -f two.txt ]] || fail "two.txt should exist after pr import"
  [[ -f three.txt ]] || fail "three.txt should exist after pr import"
  [[ -f pr.txt ]] || fail "pr.txt should exist after pr import"
  popd

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

  cat > copybara.yaml <<EOF
name: "cbtest"
workflows:
  - origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !GitDestination
      url: "file://$destination"
      fetch: master
      push: master
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    excludedOriginPaths:
      - "**/*.java"
      - "subdir/**"
EOF
  copybara copybara.yaml

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

function test_reverse_sequence() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  ( cd $remote
    run_git init .
    echo "foobaz" > test.txt
    run_git add -A
    run_git commit -m "first commit"
  )

  cat > copybara.yaml <<EOF
name: "cbtest"
global:
  - &forward_transforms !Sequence
      transformations:
        - !Replace
            before: "foo"
            after:  "bar"
        - !Replace
            before: "baz"
            after:  "bee"
workflows:
  - name : "forward"
    origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !GitDestination
      url: "file://$destination"
      fetch: master
      push: master
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    transformations:
      - *forward_transforms
  - name : "reverse"
    origin: !GitOrigin
      url: "file://$destination"
      ref: "master"
    destination: !GitDestination
      url: "file://$remote"
      fetch: reverse
      push: reverse
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    transformations:
      - !Reverse
          original: *forward_transforms
EOF
  copybara copybara.yaml forward

  ( cd $(mktemp -d)
    run_git clone $destination .
    [[ -f test.txt ]] || fail "/test.txt should exit"
    expect_in_file "barbee" test.txt
  )
  copybara copybara.yaml reverse --git-first-commit

  ( cd $(mktemp -d)
    run_git clone $remote .
    run_git checkout reverse
    [[ -f test.txt ]] || fail "/test.txt should exit"
    expect_in_file "foobaz" test.txt
  )
}

function test_local_dir_destination() {
  remote=$(temp_dir remote)

  ( cd $remote
    run_git init .
    echo "first version for food and foooooo" > test.txt
    echo "first version" > test.txt
    run_git add test.txt
    run_git commit -m "first commit"
  )
  mkdir destination

  cat > destination/copybara.yaml <<EOF
name: "cbtest"
workflows:
  - origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    excludedDestinationPaths:
      - "copybara.yaml"
      - "**.keep"
    destination: !FolderDestination {}
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
EOF

  touch destination/keepme.keep
  mkdir -p destination/folder
  touch destination/folder/keepme.keep
  touch destination/dontkeep.txt

  copybara destination/copybara.yaml --folder-dir destination

  [[ -f destination/test.txt ]] || fail "test.txt should exist"
  [[ -f destination/copybara.yaml ]] || fail "copybara.yaml should exist"
  [[ -f destination/keepme.keep ]] || fail "keepme.keep should exist"
  [[ -f destination/folder/keepme.keep ]] || fail "folder/keepme.keep should exist"
  [[ ! -f destination/dontkeep.txt ]] || fail "dontkeep.txt should be deleted"
}

function test_choose_non_default_workflow() {
  remote=$(temp_dir remote)

  ( cd $remote
    run_git init .
    echo "foo" > test.txt
    run_git add test.txt
    run_git commit -m "first commit"
  )
  mkdir destination

  cat > destination/copybara.yaml <<EOF
name: "cbtest"
workflows:
  - name: "default"
    origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !FolderDestination {}
  - name: "choochoochoose_me"
    origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !FolderDestination {}
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    transformations:
      - !Replace
        before: foo
        after: bar
EOF

  copybara destination/copybara.yaml choochoochoose_me --folder-dir destination
  expect_in_file "bar" destination/test.txt
}

function test_file_move() {
  remote=$(temp_dir remote)

  ( cd $remote
    run_git init .
    echo "foo" > test1.txt
    echo "foo" > test2.txt
    run_git add test1.txt test2.txt
    run_git commit -m "first commit"
  )
  mkdir destination

  cat > destination/copybara.yaml <<EOF
name: "cbtest"
workflows:
  - origin: !GitOrigin
      url: "file://$remote"
      ref: "master"
    destination: !FolderDestination {}
    authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    transformations:
      - !MoveFiles
        paths:
          - before: test1.txt
            after: test1.moved
          - before: test2.txt
            after: test2.moved
EOF

  copybara destination/copybara.yaml --folder-dir destination

  expect_in_file "foo" destination/test1.moved
  expect_in_file "foo" destination/test2.moved
  [[ ! -z destination/test1.txt ]] || fail "test1.txt should have been moved"
  [[ ! -z destination/test2.txt ]] || fail "test2.txt should have been moved"
}

function test_invalid_transformations_in_config() {
  cat > copybara.yaml <<EOF
name: "cbtest-invalid-xform"
workflows:
  - authoring:
      defaultAuthor: {name: "Copybara Team", email: "no-reply@google.com"}
      mode: PASS_THRU
    transformations: [42]
EOF
  copybara copybara.yaml origin/master && fail "Should fail"
  expect_log "sequence field 'transformations' expects elements of type 'Transformation', but transformations\[0\] is of type 'integer' (value = 42)"
}

function test_command_help_flag() {
  copybara --help
  expect_log 'Usage: copybara \[options\]'
  expect_log 'Example:'
}

function test_command_copybara_filename_no_correct_name() {
  copybara somename.yaml && fail "Should fail"
  expect_log "Copybara config file filename should be 'copybara.yaml'"
}

function test_command_too_few_args() {
  copybara && fail "Should fail"
  expect_log 'Expected at least a configuration file.'
  expect_log 'Usage: copybara \[options\] CONFIG_PATH \[WORKFLOW_NAME \[SOURCE_REF\]\]'
}

function test_command_too_many_args() {
  copybara config workflow_name origin/master unexpected && fail "Should fail"
  expect_log "Expect at most three arguments."
  expect_log 'Usage: copybara \[options\] CONFIG_PATH \[WORKFLOW_NAME \[SOURCE_REF\]\]'
}

function test_config_not_found() {
  copybara copybara.yaml origin/master && fail "Should fail"
  expect_log "Config file 'copybara.yaml' cannot be found."
}

run_suite "Integration tests for Copybara code sharing tool."
