#!/usr/bin/env bash
#
# Copyright 2016 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

source "${TEST_SRCDIR}/${TEST_WORKSPACE}/third_party/bazel/bashunit/unittest.bash"

# This should be kept in sync with ExitCode
readonly SUCCESS=0
readonly COMMAND_LINE_ERROR=1
readonly CONFIGURATION_ERROR=2
readonly REPOSITORY_ERROR=3
readonly NO_OP=4
readonly INTERRUPTED=8
readonly ENVIRONMENT_ERROR=30
readonly INTERNAL_ERROR=31

function run_git() {
   git "$@" > $TEST_log 2>&1 || fail "Error running git"
}

# A log configuration that outputs to the console, so that we can check the log easier
log_config=$(mktemp)
cat > $log_config <<'EOF'
handlers=java.util.logging.ConsoleHandler
java.util.logging.SimpleFormatter.format=%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n
EOF

# Extracted as a variable so that internal tests can extend the test with different binary
copybara_binary="${copybara_binary-"${TEST_SRCDIR}/${TEST_WORKSPACE}/java/com/google/copybara/copybara"}"

function copybara() {
  $copybara_binary --jvm_flag=-Djava.util.logging.config.file=$log_config "$@" \
      --output-root "$repo_storage" \
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
   cd "$(mktemp -d)" || return
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

# Runs Copybara and check that the exit code is the expected one.
function copybara_with_exit_code() {
  local expected_code=$1
  shift
  copybara "$@" && status=$? || status=$?
  if [ $status -ne $expected_code ]; then
      fail "Unexpected exit code $status. Expected $expected_code."
  fi
  return 0
}

function check_copybara_rev_id() {
   local repo="$1"
   local primary=$(get_primary_branch $repo)
   check_copybara_rev_id_branch "$repo" "$2" "$primary"
}

function check_copybara_rev_id_branch() {
   local repo="$1"
   local origin_id="$2"
   local branch="$3"
   ( cd $repo || return
     run_git log $branch -1 > $TEST_log
     expect_log "GitOrigin-RevId: $origin_id"
   )
}

function test_git_tracking() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  pushd $remote || return
  run_git init .
  echo "first version for food and foooooo" > test.txt
  mkdir subdir
  echo "first version for food and fools" > subdir/test.txt
  run_git add test.txt subdir/test.txt
  run_git commit -m "first commit"
  first_commit=$(run_git rev-parse HEAD)
  popd || return
  primary=$(get_primary_branch $remote)

    cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [
      core.replace(
        before = "food",
        after  = "drink"
      ),
      core.replace(
        before = "f\${os}o",
        after = "bar\${os}",
        regex_groups = {
          "os" : "o+"
        },
      )
    ],
)
EOF

  copybara copy.bara.sky --force

  expect_log '\[ 1/2\] Transform Replace food'
  expect_log '\[ 2/2\] Transform Replace f\${os}o'

  # Make sure we don't get detached head warnings polluting the log.
  expect_not_log "You are in 'detached HEAD' state"

  [[ -f $workdir/checkout/test.txt ]] || fail "Checkout was not successful"
  cat $workdir/checkout/test.txt > $TEST_log
  expect_log "first version for drink and barooooo"
  cat $workdir/checkout/subdir/test.txt > $TEST_log
  expect_in_file "first version for drink and barols" $workdir/checkout/subdir/test.txt
  check_copybara_rev_id "$destination" "$first_commit"

  # Do a new modification and check that we are tracking the changes to the branch
  pushd $remote || return
  echo "second version for food and foooooo" > test.txt
  echo "second version for food and fools" > subdir/test.txt

  run_git add test.txt
  run_git commit -m "second commit"
  second_commit=$(git rev-parse HEAD)
  popd || return

  copybara copy.bara.sky

  [[ -f $workdir/checkout/test.txt ]] || fail "Checkout was not successful"
  expect_in_file "second version for drink and barooooo" $workdir/checkout/test.txt

  check_copybara_rev_id "$destination" "$second_commit"

  ( cd $destination || return
    run_git show $primary > $TEST_log
  )

  expect_log "-first version for drink"
  expect_log "+second version for drink and barooooo"
}

function test_git_iterative() {
  remote=$(temp_dir remote)
  repo_storage=$(temp_dir storage)
  workdir=$(temp_dir workdir)
  destination=$(empty_git_bare_repo)

  pushd $remote || return
  run_git init .
  primary=$(get_primary_branch $remote)
  commit_one=$(single_file_commit "commit one" file.txt "food fooooo content1")
  commit_two=$(single_file_commit "commit two" file.txt "food fooooo content2")
  commit_three=$(single_file_commit "commit three" file.txt "food fooooo content3")
  commit_four=$(single_file_commit "commit four" file.txt "food fooooo content4")
  commit_five=$(single_file_commit "commit five" file.txt "food fooooo content5")

  popd || return
    cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    mode = "ITERATIVE",
)
EOF

  copybara copy.bara.sky default $commit_three --last-rev $commit_one

  check_copybara_rev_id "$destination" "$commit_three"

  ( cd $destination || return
    run_git log "${primary}~1..${primary}" > $TEST_log
  )
  expect_not_log "commit two"
  expect_log "commit three"

  copybara copy.bara.sky default $commit_five

  check_copybara_rev_id "$destination" "$commit_five"

  ( cd $destination || return
    run_git log "${primary}~2..${primary}~1" > $TEST_log
  )
  expect_log "commit four"

  ( cd $destination || return
    run_git log "${primary}~1..${primary}" > $TEST_log
  )
  expect_log "commit five"

  # Running the same workflow with the same ref is no-op
  copybara_with_exit_code $NO_OP copy.bara.sky default $commit_three
  expect_log "No new changes to import for resolved ref"
}

function test_git_iterative_same_repo() {
  destination=$(temp_dir remote)
  repo_storage=$(temp_dir storage)
  workdir=$(temp_dir workdir)


  pushd $destination || return
  run_git init .
  commit_one=$(single_file_commit "commit one" file.txt "food fooooo content1")
  primary=$(get_primary_branch $destination)
  run_git checkout -b push
  run_git checkout $primary
  commit_two=$(single_file_commit "commit two" file.txt "food fooooo content2")
  commit_three=$(single_file_commit "commit three" file.txt "food fooooo content3")
  commit_four=$(single_file_commit "commit four" file.txt "food fooooo content4")
  commit_five=$(single_file_commit "commit five" file.txt "food fooooo content5")


  popd || return
    cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$destination",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "push",
      push = "push",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    mode = "ITERATIVE",
)
EOF

  copybara copy.bara.sky default $commit_three --last-rev $commit_one

  check_copybara_rev_id_branch "$destination" "$commit_three" push

  ( cd $destination || return
    run_git log push~1..push > $TEST_log
  )
  expect_not_log "commit two"
  expect_log "commit three"

  copybara copy.bara.sky default $commit_five

  check_copybara_rev_id_branch "$destination" "$commit_five" push

  ( cd $destination || return
    run_git log "push~2..push~1" > $TEST_log
  )
  expect_log "commit four"

  ( cd $destination || return
    run_git log "push~1..push" > $TEST_log
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

  pushd $remote || return
  run_git init .
  commit_one=$(single_file_commit "commit one" file.txt "food fooooo content1")
  commit_two=$(single_file_commit "commit two" file.txt "food fooooo content2")
  commit_three=$(single_file_commit "commit three" file.txt "food fooooo content3")
  commit_four=$(single_file_commit "commit four" file.txt "food fooooo content4")
  commit_five=$(single_file_commit "commit five" file.txt "food fooooo content5")
  popd || return
  primary=$(get_primary_branch $remote)

    cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [
        metadata.squash_notes()
    ],
)
EOF

  # Before running the tool for the first time, the last imported ref is empty
  copybara info copy.bara.sky default
  expect_log "'workflow_default': last_migrated None - last_available $commit_five"
  expect_log "Available changes (5):"
  expect_log ".*${commit_one:0:10}.*commit one.*Bara Kopi <bara@kopi.com>.*"
  expect_log ".*${commit_two:0:10}.*commit two.*Bara Kopi <bara@kopi.com>.*"
  expect_log ".*${commit_three:0:10}.*commit three.*Bara Kopi <bara@kopi.com>.*"
  expect_log ".*${commit_four:0:10}.*commit four.*Bara Kopi <bara@kopi.com>.*"
  expect_log ".*${commit_five:0:10}.*commit five.*Bara Kopi <bara@kopi.com>.*"

  copybara copy.bara.sky default $commit_one --force

  check_copybara_rev_id "$destination" "$commit_one"

  copybara info copy.bara.sky default
  expect_log "'workflow_default': last_migrated $commit_one - last_available $commit_five"
  expect_log "Available changes (4):"
  expect_log ".*${commit_two:0:10}.*commit two.*Bara Kopi <bara@kopi.com>.*"
  expect_log ".*${commit_three:0:10}.*commit three.*Bara Kopi <bara@kopi.com>.*"
  expect_log ".*${commit_four:0:10}.*commit four.*Bara Kopi <bara@kopi.com>.*"
  expect_log ".*${commit_five:0:10}.*commit five.*Bara Kopi <bara@kopi.com>.*"

  ( cd $destination || return
    run_git "log" "${primary}~1..${primary}" > $TEST_log
  )
  # By default we include the whole history if last_rev cannot be found. --squash-without-history
  # can be used for disabling this.
  expect_log "commit one"

  copybara copy.bara.sky default $commit_four

  copybara info copy.bara.sky default
  expect_log "'workflow_default': last_migrated $commit_four - last_available $commit_five"
  expect_log "Available changes (1):"
  expect_log ".*${commit_five:0:10}.*commit five.*Bara Kopi <bara@kopi.com>.*"

  check_copybara_rev_id "$destination" "$commit_four"

  ( cd $destination || return
    run_git log "${primary}~1..${primary}" > $TEST_log
  )
  # "commit one" should not be included because it was migrated before
  expect_not_log "commit one"
  expect_log "$commit_two.*commit two"
  expect_log "$commit_three.*commit three"
  expect_log "$commit_four.*commit four"
  expect_not_log "commit five"

  copybara copy.bara.sky default $commit_five --last-rev $commit_three

  check_copybara_rev_id "$destination" "$commit_five"

  ( cd $destination || return
    run_git log "${primary}~1..${primary}" > $TEST_log
  )
  # We are forcing to use commit_three as the last migration. This has
  # no effect in squash workflow but it changes the release notes.
  expect_not_log "commit one"
  expect_not_log "commit two"
  expect_not_log "commit three"
  expect_log "$commit_four.*commit four"
  expect_log "$commit_five.*commit five"
}

function test_can_skip_excluded_commit() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  pushd $remote || return
  run_git init .
  primary=$(get_primary_branch $remote)

  commit_primary=$(single_file_commit "last rev commit" file2.txt "origin")
  commit_one=$(single_file_commit "commit one" file.txt "foooo")
  # Because we exclude file2.txt this is effectively an empty commit in the destination
  commit_two=$(single_file_commit "commit two" file2.txt "bar")
  commit_three=$(single_file_commit "commit three" file.txt "baaaz")
  popd || return

    cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    origin_files = glob(include = ["**"], exclude = ["file2.txt"]),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    mode = "ITERATIVE",
)
EOF

  copybara copy.bara.sky default $commit_three --last-rev $commit_primary --force

  check_copybara_rev_id "$destination" "$commit_three"

  ( cd $destination || return
    run_git log > $TEST_log
  )
  expect_log "commit one"
  expect_not_log "commit two"
  expect_log "commit three"
}

function empty_git_bare_repo() {
  repo=$(temp_dir repo)
  cd $repo || return
  run_git init . --bare > $TEST_log 2>&1 || fail "Cannot create repo"
  run_git --work-tree="$(mktemp -d)" commit --allow-empty -m "Empty repo" \
    > $TEST_log 2>&1 || fail "Cannot commit to empty repo"
  echo $repo
}

function get_primary_branch() {
  local repo=$1
  cd $repo || return
  local primary=$(git symbolic-ref --short HEAD)
  echo "$primary"
}

function prepare_glob_tree() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  ( cd $remote || return
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
  primary=$(get_primary_branch $remote)

  cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "${primary}",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "${primary}",
      push = "${primary}",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [
        core.replace(
            before = "foo",
            after  = "bar",
            paths = glob(['**.java']),
        )
    ],
)
EOF
  copybara copy.bara.sky --force
  ( cd "$(mktemp -d)" || return
    run_git clone $destination .
    expect_in_file "foo" test.txt
    expect_in_file "bar" test.java
    expect_in_file "foo" folder/test.txt
    expect_in_file "bar" folder/test.java
    expect_in_file "foo" folder/subfolder/test.txt
    expect_in_file "bar" folder/subfolder/test.java
  )
}

function git_pull_request() {
  sot=$(empty_git_bare_repo)
  public=$(empty_git_bare_repo)
  # Create the SoT repo
  pushd "$(mktemp -d)" || return
  run_git clone $sot .
  commit_one=$(single_file_commit "commit one" one.txt "content")
  commit_two=$(single_file_commit "commit two" two.txt "content")
  commit_three=$(single_file_commit "commit three" three.txt "content")
  run_git push
  popd || return
  primary=$(get_primary_branch $remote)

  cat > copy.bara.sky <<EOF
core.workflow(
    name = "export",
    origin = git.origin(
      url = "file://$sot",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$public",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
)

core.workflow(
    name = "import_change",
    origin = git.origin(
      url = "file://$public",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$sot",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    mode = "CHANGE_REQUEST",
)
EOF


  copybara copy.bara.sky export $commit_two

  # Check that we have exported correctly the tree state as in commit_two
  pushd "$(mktemp -d)" || return
  run_git clone $public .
  [[ -f one.txt ]] || fail "one.txt should exist in commit two"
  [[ -f two.txt ]] || fail "two.txt should exist in commit two"
  [[ ! -f three.txt ]] || fail "three.txt should NOT exist in commit two"

  # Create a new change on top of the public version (commit_two)
  pr_request=$(single_file_commit "pull request" pr.txt "content")
  run_git push
  popd || return

  copybara copy.bara.sky import_change $pr_request

  # Check that the SoT contains the change from pr_request but that it has not reverted
  # commit_three (SoT was ahead of public).
  pushd "$(mktemp -d)" || return
  run_git clone $sot .
  [[ -f one.txt ]] || fail "one.txt should exist after pr import"
  [[ -f two.txt ]] || fail "two.txt should exist after pr import"
  [[ -f three.txt ]] || fail "three.txt should exist after pr import"
  [[ -f pr.txt ]] || fail "pr.txt should exist after pr import"
  popd || return
}

function test_git_delete() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)
  destination=$(empty_git_bare_repo)

  ( cd $remote || return
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
  primary=$(get_primary_branch $remote)

  cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    origin_files = glob(include = ["**"], exclude = ['**/*.java', 'subdir/**']),
)
EOF
  copybara copy.bara.sky --force

  ( cd "$(mktemp -d)" || return
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

  ( cd $remote || return
    run_git init .
    echo "foobaz" > test.txt
    run_git add -A
    run_git commit -m "first commit"
  )
  primary=$(get_primary_branch $remote)

  cat > copy.bara.sky <<EOF
forward_transforms = [
  core.replace('foo', 'bar'),
  core.replace('baz', 'bee'),
]
core.workflow(
    name = "forward",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = forward_transforms,
)

core.workflow(
    name = "reverse",
    origin = git.origin(
      url = "file://$destination",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$remote",
      fetch = "reverse",
      push = "reverse",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = core.reverse(forward_transforms),
)
EOF
  copybara copy.bara.sky forward --force

  ( cd "$(mktemp -d)" || return
    run_git clone $destination .
    [[ -f test.txt ]] || fail "/test.txt should exit"
    expect_in_file "barbee" test.txt
  )
  copybara copy.bara.sky reverse --force

  ( cd "$(mktemp -d)" || return
    run_git clone $remote .
    run_git checkout reverse
    [[ -f test.txt ]] || fail "/test.txt should exit"
    expect_in_file "foobaz" test.txt
  )
}

function test_local_dir_destination() {
  remote=$(temp_dir remote)

  ( cd $remote || return
    run_git init .
    echo "first version for food and foooooo" > test.txt
    echo "first version" > test.txt
    run_git add test.txt
    run_git commit -m "first commit"
  )
  primary=$(get_primary_branch $remote)

  mkdir destination

  cat > destination/copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination_files = glob(include = ["**"], exclude = ["copy.bara.sky", "**.keep"]),
    destination = folder.destination(),
    reversible_check = True, # enabled to test for regression where folder.origin caused exceptions
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
)
EOF

  touch destination/keepme.keep
  mkdir -p destination/folder
  touch destination/folder/keepme.keep
  touch destination/dontkeep.txt

  copybara destination/copy.bara.sky --folder-dir destination

  [[ -f destination/test.txt ]] || fail "test.txt should exist"
  [[ -f destination/copy.bara.sky ]] || fail "copy.bara.sky should exist"
  [[ -f destination/keepme.keep ]] || fail "keepme.keep should exist"
  [[ -f destination/folder/keepme.keep ]] || fail "folder/keepme.keep should exist"
  [[ ! -f destination/dontkeep.txt ]] || fail "dontkeep.txt should be deleted"
}

function test_choose_non_default_workflow() {
  remote=$(temp_dir remote)

  ( cd $remote || return
    run_git init .
    echo "foo" > test.txt
    run_git add test.txt
    run_git commit -m "first commit"
  )
  primary=$(get_primary_branch $remote)
  mkdir destination

  cat > destination/copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = folder.destination(),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
)

core.workflow(
    name = "choochoochoose_me",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = folder.destination(),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [core.replace("foo", "bar")]
)
EOF

  copybara destination/copy.bara.sky choochoochoose_me --folder-dir destination
  expect_in_file "bar" destination/test.txt
}

function test_file_move() {
  remote=$(temp_dir remote)

  ( cd $remote || return
    run_git init .
    echo "foo" > test1.txt
    echo "foo" > test2.txt
    run_git add test1.txt test2.txt
    run_git commit -m "first commit"
  )
  primary=$(get_primary_branch $remote)
  mkdir destination

  cat > destination/copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = folder.destination(),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [
        core.move('test1.txt', 'test1.moved'),
        core.move('test2.txt', 'test2.moved'),
    ],
)
EOF

  copybara destination/copy.bara.sky --folder-dir destination

  expect_in_file "foo" destination/test1.moved
  expect_in_file "foo" destination/test2.moved
  [[ ! -f destination/test1.txt ]] || fail "test1.txt should have been moved"
  [[ ! -f destination/test2.txt ]] || fail "test2.txt should have been moved"
}

function test_profile() {
  remote=$(temp_dir remote)

  ( cd $remote || return
    run_git init .
    echo "foo" > test.txt
    run_git add test.txt
    run_git commit -m "first commit"
  )
  primary=$(get_primary_branch $remote)

  mkdir destination

  cat > destination/copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = folder.destination(),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [
        core.move('test.txt', 'test.moved'),
        core.move('test.moved', 'test.moved2'),
    ],
)
EOF

  copybara destination/copy.bara.sky --folder-dir destination
  expect_log "ioRepoTask.*PROFILE:.*[0-9]* //copybara/clean_outputdir"
  expect_log "repoTask.*PROFILE:.*[0-9]* //copybara/run/default/origin.resolve_source_ref"
  expect_log "doMigrate.*PROFILE:.*[0-9]* //copybara/run/default/squash/prepare_workdir"
  expect_log "checkout.*PROFILE:.*[0-9]* //copybara/run/default/squash/origin.checkout"
  expect_log "transform.*PROFILE:.*[0-9]* //copybara/run/default/squash/transforms/Moving test.txt"
  expect_log "transform.*PROFILE:.*[0-9]* //copybara/run/default/squash/transforms/Moving test.moved"
  expect_log "doMigrate.*PROFILE:.*[0-9]* //copybara/run/default/squash/transforms"
  expect_log "doMigrate.*PROFILE:.*[0-9]* //copybara/run/default/squash/destination.write"
  expect_log "run.*PROFILE:.*[0-9]* //copybara/run/default/squash"
  expect_log "run.*PROFILE:.*[0-9]* //copybara/run"
  expect_log "shutdown.*PROFILE:.*[0-9]* //copybara"
  expect_in_file "foo" destination/test.moved2
}

function test_invalid_transformations_in_config() {
  cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://foo/bar",
      ref = "primary",
    ),
    destination = folder.destination(),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [42],
)
EOF
  copybara_with_exit_code $CONFIGURATION_ERROR copy.bara.sky default
  expect_log "for 'transformations' element, got int, want function or transformation"
}

function test_command_help_flag() {
  copybara help
  expect_log 'Usage: copybara \[options\]'
  expect_log 'Example:'
}

# We want to log the command line arguments so that it is easy to reproduce
# user issues.
function test_command_args_logged() {
  copybara foo bar baz --option && fail "should fail"
  expect_log 'Running: .*foo bar baz --option'
}

function test_command_copybara_filename_no_correct_name() {
  copybara_with_exit_code $CONFIGURATION_ERROR migrate somename.bzl
  expect_log "Copybara config file filename should be 'copy.bara.sky'"
}

function setup_reversible_check_workflow() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  pushd $remote || return
  run_git init .
  echo "Is the reverse of the reverse forward?" > test.txt
  run_git add test.txt
  run_git commit -m "first commit"
  first_commit=$(run_git rev-parse HEAD)
  primary=$(get_primary_branch $remote)

  popd || return

    cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    reversible_check = True,
    transformations = [
      core.replace(
        before = "reverse",
        after  = "forward"
      )
    ],
)
EOF
}

function test_reversible_check() {
  setup_reversible_check_workflow
  copybara_with_exit_code $CONFIGURATION_ERROR copy.bara.sky --force
  expect_log "ERROR: Workflow 'default' is not reversible"
}

function test_disable_reversible_check() {
  setup_reversible_check_workflow
  copybara --disable-reversible-check copy.bara.sky --force
}

function test_info_list_cmd() {
      cat > copy.bara.sky <<EOF
core.workflow(
    name = "one_workflow",
    origin = git.origin(
      url = "file://foo",
      ref = "primary",
    ),
    destination = git.destination(
      url = "file://bar",
      fetch = "primary",
      push = "primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
)
core.workflow(
    name = "another_workflow",
    origin = git.origin(
      url = "file://foo",
      ref = "primary",
    ),
    destination = git.destination(
      url = "file://bar",
      fetch = "primary",
      push = "primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
)
EOF
  copybara_with_exit_code $SUCCESS INFO copy.bara.sky --info-list-only
  expect_log "another_workflow,one_workflow"
}

function test_config_not_found() {
  copybara_with_exit_code $COMMAND_LINE_ERROR copy.bara.sky origin/primary
  expect_log "Configuration file not found: copy.bara.sky"
}

#Verify that we instantiate LogConsole when System.console() is null
function test_no_ansi_console() {
  copybara_with_exit_code $COMMAND_LINE_ERROR copy.bara.sky
  expect_log "^[0-9]\{4\} [0-2][0-9]:[0-5][0-9]:[0-5][0-9].*"
}

# Verify that Copybara fails if we try to read the input from the user from a writeOnly LogConsole
function test_log_console_is_write_only() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  ( cd $remote || return
    run_git init .
    echo "first version for food and foooooo" > test.txt
    run_git add -A
    run_git commit -m "first commit"
  )
  primary=$(get_primary_branch $remote)
  cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    ask_for_confirmation = True,
)
EOF
  copybara_with_exit_code $INTERNAL_ERROR copy.bara.sky --force
  expect_log "LogConsole cannot read user input if system console is not present"
}

function run_multifile() {
  config_folder=$1
  shift
  flags="$*"
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  pushd $remote || return
  run_git init .
  primary=$(get_primary_branch $remote)
  echo "first version for food and foooooo" > test.txt
  mkdir subdir
  echo "first version" > subdir/test.txt
  run_git add test.txt subdir/test.txt
  run_git commit -m "first commit"
  first_commit=$(run_git rev-parse HEAD)
  popd || return
  mkdir -p $config_folder/foo/bar/baz
  mkdir -p $config_folder/baz
  # Just in case:
  cat > $config_folder/baz/origin.bara.sky <<EOF
  this shouldn't be loaded!
EOF
  cat > $config_folder/foo/remote.bara.sky <<EOF
remote_var="file://$remote"
EOF
  cat > $config_folder/foo/bar/baz/origin.bara.sky <<EOF
load("//foo/remote", "remote_var")
remote_origin=git.origin( url = remote_var, ref = "$primary")
EOF
  cat > $config_folder/foo/bar/copy.bara.sky <<EOF
load("baz/origin", "remote_origin")

core.workflow(
    name = "default",
    origin = remote_origin,
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary"
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
)
EOF
  cd $config_folder || return
  copybara foo/bar/copy.bara.sky $flags --force

  [[ -f $workdir/checkout/test.txt ]] || fail "Checkout was not successful"
}

# Test that we can find the root when config is in a git repo
function test_multifile_git_root() {
  config_folder=$(temp_dir config)
  pushd $config_folder || return
  run_git init .
  popd || return
  run_multifile $config_folder
}

# Test that on non-git repos we can pass a flag to set the root
function test_multifile_root_cfg_flag() {
  config_folder=$(temp_dir config)
  run_multifile $config_folder --config-root $config_folder
}

# Regression test: config roots that are symlinks work
function test_multifile_root_cfg_flag_symlink() {
  config_folder=$(temp_dir config)
  mkdir "${config_folder}/test"
  ln -s "${config_folder}/test" "${config_folder}/link"
  run_multifile "${config_folder}/link" --config-root "${config_folder}/link"
}

function test_verify_match() {
  prepare_glob_tree
  primary=$(get_primary_branch $remote)

  cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [
        core.verify_match(
            regex = "bar",
            paths = glob(['**.java']),
            verify_no_match = True
        )
    ],
)
EOF
  copybara copy.bara.sky --force
}

function test_subcommand_parsing_fails() {
  copybara_with_exit_code $COMMAND_LINE_ERROR migrate.sky copy.bara.sky

  expect_log "Invalid subcommand 'migrate.sky'"
}

function test_command_copybara_wrong_subcommand() {
  copybara_with_exit_code COMMAND_LINE_ERROR foooo
  expect_log "Invalid subcommand 'foooo'. Available commands: "
  expect_log "Try 'copybara help'"
}

function test_default_command_too_few_args() {
  copybara_with_exit_code $COMMAND_LINE_ERROR
  expect_log "Configuration file missing for 'migrate' subcommand."
  expect_log "Try 'copybara help'"
}

function test_migrate_missing_config() {
  copybara_with_exit_code $COMMAND_LINE_ERROR migrate

  expect_log "Configuration file missing for 'migrate' subcommand."
  expect_log "Try 'copybara help'"
}

function test_info_missing_config() {
  copybara_with_exit_code $COMMAND_LINE_ERROR info

  expect_log "Configuration file missing for 'info' subcommand."
}

function test_info_too_many_arguments() {
  copybara_with_exit_code $COMMAND_LINE_ERROR info copy.bara.sky default foo

  expect_log "Too many arguments for subcommand 'info'"
}

function test_validate_missing_config() {
  copybara_with_exit_code $COMMAND_LINE_ERROR validate

  expect_log "Configuration file missing for 'validate' subcommand."
}

function test_validate_too_many_arguments() {
  copybara_with_exit_code $COMMAND_LINE_ERROR validate copy.bara.sky default foo

  expect_log "Too many arguments for subcommand 'validate'"
}

function test_validate_valid() {
  cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://foo/bar",
      ref = "primary",
    ),
    destination = git.destination(
      url = "file://bar/foo",
      fetch = "primary",
      push = "primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    mode = "ITERATIVE",
)
EOF

  copybara validate copy.bara.sky

  expect_log "Configuration '.*copy.bara.sky' is valid."
}

function test_validate_invalid() {
    cat > copy.bara.sky <<EOF
core.workflowFoo(
    name = "default",
)
EOF

  copybara_with_exit_code $CONFIGURATION_ERROR validate copy.bara.sky

  expect_log "Configuration '.*copy.bara.sky' is invalid."
  expect_log "Error loading config file"
}

function test_require_at_least_one_migration() {
    cat > copy.bara.sky <<EOF

EOF

  copybara_with_exit_code $CONFIGURATION_ERROR migrate copy.bara.sky

  expect_log "At least one migration is required"
}

function test_apply_patch() {
  prepare_glob_tree
  ( cd $remote || return
    echo "patched" > test.java
    echo "patched" > folder/test.java
    git --no-pager diff > $workdir/../diff1.patch
    run_git reset --hard
    expect_in_file "foo" test.java
    expect_in_file "foo" folder/test.java
    echo "patched again" > folder/subfolder/test.java
    git --no-pager diff > $workdir/../diff2.patch
    run_git reset --hard
    expect_in_file "foo" folder/subfolder/test.java
  )
  primary=$(get_primary_branch $remote)

  cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    transformations = [
        patch.apply(
            patches = ["diff1.patch", "diff2.patch"],
        )
    ],
)
EOF
  copybara copy.bara.sky --force
  ( cd "$(mktemp -d)" || return
    run_git clone $destination .
    expect_in_file "patched" test.java
    expect_in_file "patched" folder/test.java
    expect_in_file "patched again" folder/subfolder/test.java
  )
}

function test_description_migrator() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  pushd $remote || return
    run_git init .
    commit_initial=$(single_file_commit "initial rev commit" file2.txt "initial")
    commit_primary=$(single_file_commit "last rev commit" file23.txt "origin")
    commit_one=$(single_file_commit "c1 foooo origin/${commit_primary} bar" file.txt "one")

  popd || return
  primary=$(get_primary_branch $remote)

    cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    origin_files = glob(include = ["**"], exclude = ["file2.txt"]),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
     transformations = [
        metadata.map_references(
          before = "origin/\${reference}",
          after = "destination/\${reference}",
          regex_groups = {
              "before_ref": "[0-9a-f]+",
              "after_ref": "[0-9a-f]+",
          }
        )
    ],
    mode = "ITERATIVE",
)
EOF

  copybara copy.bara.sky default $commit_primary --last-rev $commit_initial
  copybara copy.bara.sky default $commit_one --last-rev $commit_primary

  check_copybara_rev_id "$destination" "$commit_one"

  ( cd $destination || return
    run_git log > $TEST_log
  )
  expect_log "c1 foooo destination/[a-f0-9]\{1,\} bar"
}

function test_invalid_last_rev() {
  remote=$(temp_dir remote)
  destination=$(empty_git_bare_repo)

  pushd $remote || return
    run_git init .
    commit_initial=$(single_file_commit "initial rev commit" file2.txt "initial")
  popd || return
  primary=$(get_primary_branch $remote)

    cat > copy.bara.sky <<EOF
core.workflow(
    name = "default",
    origin = git.origin(
      url = "file://$remote",
      ref = "$primary",
    ),
    origin_files = glob(include = ["**"], exclude = ["file2.txt"]),
    destination = git.destination(
      url = "file://$destination",
      fetch = "$primary",
      push = "$primary",
    ),
    authoring = authoring.pass_thru("Copybara Team <no-reply@google.com>"),
    mode = "ITERATIVE",
)
EOF

  copybara_with_exit_code $CONFIGURATION_ERROR copy.bara.sky default --last-rev --some-other-flag

  expect_log "Invalid refspec: --some-other-flag"
}

run_suite "Integration tests for Copybara code sharing tool."
