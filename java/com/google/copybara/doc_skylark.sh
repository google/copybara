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

output=$1
shift
echo "Generating documentation for $# transitive jars"
printf '# Table of Contents\n\n\n' >> $output
touch detail
for jar in "$@";do
  # Continue if no md file is found
  unzip -q -p $jar "*.copybara.md" >> detail 2> /dev/null || continue
done
cat detail | grep "^# " | awk '{ print "  - ["$2"](#"$2")"}' >> $output

printf '\n\n' >> $output
cat detail >> $output
