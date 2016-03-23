# Copyright 2016 Google Inc. All Rights Reserved.

java_test(
    name = "ExampleTest",
    size = "small",
    srcs = ["ExampleTest.java"],
    deps = [
        "//third_party:junit4",
        "//java/com/google/copybara:copybara_lib",
    ],
)

test_suite(
    name = "all_tests",
    tests = [":ExampleTest"]
)