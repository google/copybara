# Copyright 2016 Google Inc. All Rights Reserved.
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
