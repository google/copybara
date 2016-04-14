# Copyright 2016 Google Inc. All Rights Reserved.

load(":test.bzl", "all_tests")

licenses(["unencumbered"])  # Owned by Google. No external contributions

all_tests(
  tests = glob(["**/*Test.java"]),
  deps = [
     "//java/com/google/copybara:copybara_lib",
     "//third_party:guava",
     "//third_party:jcommander",
     "//third_party:jimfs",
     "//third_party:jsr305",
     "//third_party:junit4",
     "//third_party:shell",
     "//third_party:truth",
  ],
)
