# Copyright 2016 Google Inc. All Rights Reserved.

load("//javatests/com/google/copybara:test.bzl", "all_tests")

licenses(["unencumbered"])  # Owned by Google. No external contributions

all_tests(
    tests = glob(["**/*Test.java"]),
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara/testing",
        "//java/com/google/copybara/transform",
        "//java/com/google/copybara/util/console",
        "//java/com/google/copybara/util/console/testing",
        "//third_party:jimfs",
        "//third_party:truth",
    ],
)
