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

  $ ${TEST_SRCDIR}/java/com/google/copybara/copybara --help
  Usage: copybara [options] CONFIG_PATH [SOURCE_REF]
    Options:
      --folder-dir
         Local directory to put the output of the transformation
      --gerrit-change-id
         ChangeId to use in the generated commit message
         Default: <empty string>
      --first-migration
         Ignore that the fetch reference doesn't exist when pushing to destination
         Default: false
      --git-previous-ref
         Previous SHA-1 reference used for the migration.
         Default: <empty string>
      --git-repo-storage
         Location of the storage path for git repositories
         Default: */.copybara/repos (glob)
      --help
         Shows this help text
         Default: false
      --work-dir
         Directory where all the transformations will be performed. By default a
         temporary directory.
      -v
         Verbose output.
         Default: false
  
  Example:
    copybara myproject.copybara origin/master

