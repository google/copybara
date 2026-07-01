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

function test_reference_doc_generated() {
   doc=${TEST_SRCDIR}/${TEST_WORKSPACE}/java/com/google/copybara/reference.md
   source_doc=${TEST_SRCDIR}/${TEST_WORKSPACE}/docs/reference.md

   [[ -f $doc ]] || fail "Documentation not generated"
   # Check that we have table of contents and some basic modules
   grep "^## Table of Contents" "$doc" > /dev/null 2>&1 || fail "Table of contents not found"
   grep "^## core" "$doc" > /dev/null 2>&1 || fail "core doc not found"
   grep "^### core.replace" "$doc" > /dev/null 2>&1 || fail "core.replace doc not found"
   grep "before.*The text before the transformation" \
	 "$doc" > /dev/null 2>&1 || fail "core.replace field doc not found"
   grep "^### git.origin" "$doc" > /dev/null 2>&1 || fail "git.origin doc not found"
   grep "Finds links to commits in change messages" "$doc" > /dev/null 2>&1 \
     || fail "single example not found"

   diff -B -u $source_doc $doc || fail "Generate the documentation with scripts/update_docs [-a]"
}

run_suite "Integration tests for reference documentation generation."
