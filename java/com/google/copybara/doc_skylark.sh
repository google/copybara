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

output=$1
class_list_output=$2
shift
echo "Generating documentation for $# transitive jars"
printf '## Table of Contents\n\n\n' >> "$output"

for jar in "$@";do
    # Continue if no md file is found
    unzip -q -t "$jar" "*.copybara.md"  2>&1 > /dev/null || continue
    unzip -q -t "$jar" "starlark_class_list.txt"  2>&1 > /dev/null || continue
    mkdir -p temp_dir
    rm -f temp_dir/*
    cd temp_dir
    unzip -q "../$jar" "*.copybara.md" || continue
    cd ..
    unzip -p "$jar" "starlark_class_list.txt" >> ${jar//\//_}_class_list.txt || continue
    for file in temp_dir/*; do
      # Find module name and create a .md file. Not nice but should work for now
      name="$(cat $file | grep "^## " | head -1 | sed 's/## //g').md"
      # If the new file is bigger than the old one we assume it is an extension.
      # It works as far as we don't delete exposed information (We shouldn't).
      if [[ -f $name && "$(stat -c%s "$file")" -le "$(stat -c%s "$name")" ]]; then
  	continue;
      fi
      cat $file > "$name"
    done
done

# Consistent sorting between platforms
export LC_ALL=C
find *.md | sort -f | xargs cat > result

{
  # Grep h1 (#) and h2 (##), contruct a line as '## - [foo](foo)' so that we have the
  # correct indentation, and finally replace ## or #### by spaces.
  < result grep "^###\\? "  | awk '{ print ""$1"- ["$2"](#"tolower($2)")"}'   \
    | sed 's/###/    /g' | sed 's/##/  /g'

  printf '\n\n'
  cat result
} >> "$output"

find *_class_list.txt | xargs cat >> "$class_list_output"