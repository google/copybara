# Copyright 2016 Google Inc. All Rights Reserved.

java_test(
    name = "ExampleTest",
    size = "small",
    srcs = ["ExampleTest.java"],
    deps = [
        "@bazel_tools//third_party:junit4",
        "//java/com/google/copybara:copybara_lib",
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

test_suite(
    name = "all_tests",
    tests = [
      ":CommandTest",
      ":ExampleTest",
    ]
)