# Copyright 2016 Google Inc. All Rights Reserved.
#!/usr/bin/env bash

output=$1
shift
doc_elements=$1
shift
echo "Generating documentation for $# transitive jars"
echo "# Table of Contents" >> $output
echo "" >> $output
for element in $(echo $doc_elements | tr ',' ' ');do
  echo "  - [$element](#$(echo $element | tr '[:upper:]' '[:lower:]'))" >> $output
done
echo "" >> $output
# This is a brutal because we unzip all the transitive deps about
# five times. But it takes less than a second and we don't run
# often, so fine for now.
# Lets see how it performs with more jars
for element in $(echo $doc_elements | tr ',' ' ');do
  touch $element.out
  for jar in "$@";do
    # Continue if no md file is found
    unzip -q -p $jar "$element.md" > temp 2>&1 || continue
    cat temp >> $element.out
  done
  echo "# $element" >> $output
  echo "" >> $output
  cat $element.out >> $output
done
