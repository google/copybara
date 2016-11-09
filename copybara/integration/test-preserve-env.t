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

TODO(copybara-team): add some sort of pre-test support to cram so we can shove
all this boilerplate in there
  $ GIT_AUTHOR_NAME='test'; export GIT_AUTHOR_NAME
  $ GIT_AUTHOR_EMAIL='test@example.org'; export GIT_AUTHOR_EMAIL
  $ GIT_AUTHOR_DATE="2007-01-01 00:00:00 +0000"; export GIT_AUTHOR_DATE
  $ GIT_COMMITTER_NAME="$GIT_AUTHOR_NAME"; export GIT_COMMITTER_NAME
  $ GIT_COMMITTER_EMAIL="$GIT_AUTHOR_EMAIL"; export GIT_COMMITTER_EMAIL
  $ GIT_COMMITTER_DATE="$GIT_AUTHOR_DATE"; export GIT_COMMITTER_DATE
  $ mkdir bin
  $ PATH="$PWD/bin:$PATH"
  $ export PATH
  $ ln -s ${TEST_SRCDIR}/java/com/google/copybara/copybara bin/copybara

  $ git init remote
  Initialized empty Git repository in */remote/.git/ (glob)
  $ cd remote
  $ echo "first version for food and foooooo" > test.txt
  $ mkdir subdir
  $ echo "first version" > subdir/test.txt
  $ git add .
  $ git commit -m 'first commit'
  [master (root-commit) 6677cbd] first commit
   2 files changed, 2 insertions(+)
   create mode 100644 subdir/test.txt
   create mode 100644 test.txt
  $ first_commit=`git rev-parse HEAD`
  $ cd ..

  $ git init --bare destination
  Initialized empty Git repository in */destination/ (glob)

  $ cat > test.copybara <<EOF
  > name: "cbtest"
  > origin: !GitOrigin
  >   url: "file://$PWD/remote"
  >   ref: "master"
  > destination: !GitDestination
  >   url: "file://$PWD/destination"
  >   fetch: "master"
  >   push: "master"
  > transformations:
  >   - !Replace
  >     before:       food
  >     after:        drink
  >   - !Replace
  >     before:       f\${os}o
  >     after:        bar\${os}
  >     regexGroups:
  >       os: "o+"
  > EOF

Set up a logging git wrapper so we can demonstrate PATH et al were respected:
  $ cat > bin/git <<EOF
  > #!/bin/sh
  > echo \$PATH > $PWD/gitpath
  > echo \$GIT_EXEC_DIR > $PWD/git_exec_dir
  > EOF
  $ echo exec `which git` '"$@"' >> bin/git
  $ chmod +x bin/git
  $ mkdir workdir
  $ mkdir storage

  $ copybara test.copybara --git-repo-storage="$PWD/storage" \
  >  --work-dir "$PWD/workdir" --force > copybara.log 2>&1

  $ [ "`cat gitpath`" = "$PATH" ]
  $ cat git_exec_dir
  

Again, but this time with a busted GIT_EXEC_DIR to prove that works too:
  $ rm -rf destination
  $ git init --bare destination
  Initialized empty Git repository in */destination/ (glob)

  $ GIT_EXEC_DIR=/dev/null ; export GIT_EXEC_DIR
  $ copybara test.copybara --git-repo-storage="$PWD/storage" \
  >  --work-dir "$PWD/workdir" --force > copybara.log 2>&1
  $ [ "`cat gitpath`" = "$PATH" ]
  $ cat git_exec_dir
  /dev/null
