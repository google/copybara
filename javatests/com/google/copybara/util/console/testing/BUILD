# Copyright 2016 Google Inc. All Rights Reserved.

load("//javatests/com/google/copybara:test.bzl", "all_tests")

licenses(["unencumbered"])  # Owned by Google. No external contributions

all_tests(
  tests = glob(["*.java"]),
  deps = [
     "//java/com/google/copybara/util/console/testing",
     "//third_party:truth",
  ],
  tags = ["local"],
)
