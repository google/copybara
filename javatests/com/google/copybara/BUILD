# Copyright 2016 Google Inc. All Rights Reserved.

java_test(
    name = "CopybaraTest",
    size = "small",
    srcs = ["CopybaraTest.java"],
    deps = [
        "@bazel_tools//third_party:junit4",
        "//java/com/google/copybara:copybara_lib",
        "@jcommander//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "CommandTest",
    size = "small",
    srcs = ["CommandTest.java"],
    deps = [
        "@bazel_tools//third_party:junit4",
        "//third_party/bazel:shell",
        "@truth//jar",
    ],
)

java_test(
    name = "GitRepositoryTest",
    size = "small",
    srcs = ["git/GitRepositoryTest.java"],
    deps = [
        "@bazel_tools//third_party:junit4",
        "//java/com/google/copybara:copybara_lib",
        "//third_party/bazel:shell",
        "@truth//jar",
    ],
)

test_suite(
    name = "all_tests",
    tests = [
      ":CommandTest",
      ":CopybaraTest",
    ]
)