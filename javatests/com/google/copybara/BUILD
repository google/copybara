# Copyright 2016 Google Inc. All Rights Reserved.

load(":test.bzl", "all_tests")

licenses(["unencumbered"])  # Owned by Google. No external contributions

LOCAL_TESTS = ["WorkflowTest.java", "util/DiffUtilTest.java"]
DEPS = [
    "//java/com/google/copybara:base",
    "//java/com/google/copybara:copybara_lib",
    "//java/com/google/copybara/doc:annotations",
    "//java/com/google/copybara/testing",
    "//java/com/google/copybara/util",
    "//java/com/google/copybara/util/console",
    "//java/com/google/copybara/util/console/testing",
    "//third_party:guava_testlib",
    "//third_party:jcommander",
    "//third_party:jimfs",
    "//third_party:shell",
    "//third_party:skylark-lang",
    "//third_party:truth",
]

all_tests(
    tests = LOCAL_TESTS,
    deps = DEPS,
    tags = ["local"],
)

all_tests(
    tests = glob(["**/*Test.java"], exclude = LOCAL_TESTS),
    deps = DEPS,
)
