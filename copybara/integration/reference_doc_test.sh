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

#!/usr/bin/env bash

source third_party/bazel/bashunit/unittest.bash

function test_reference_doc_generated() {
   doc=${TEST_SRCDIR}/copybara/java/com/google/copybara/docs.md

   [[ -f $doc ]] || fail "Documentation not generated"   
   # Check that we have table of contents and some basic modules
   grep "^# Table of Contents" $doc > /dev/null 2>&1 || fail "Table of contents not found"
   grep "^# core" $doc > /dev/null 2>&1 || fail "core doc not found"
   grep "^## core.replace" $doc > /dev/null 2>&1 || fail "core.replace doc not found"
   grep "before.*The text before the transformation" \
	 $doc > /dev/null 2>&1 || fail "core.replace field doc not found"
   grep "^## git.origin" $doc > /dev/null 2>&1 || fail "git.origin doc not found"
}

run_suite "Integration tests for reference documentation generation."

