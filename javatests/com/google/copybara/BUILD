# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_test(
    name = "CopybaraTest",
    size = "small",
    srcs = ["CopybaraTest.java"],
    test_class = "com.google.copybara.CopybaraTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "@jcommander//jar",
        "@truth//jar",
        "//java/com/google/copybara:copybara_lib",
    ],
)

java_test(
    name = "CommandTest",
    size = "small",
    srcs = ["CommandTest.java"],
    test_class = "com.google.copybara.CommandTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "@truth//jar",
        "//third_party/bazel:shell",
    ],
)

java_test(
    name = "GitDestinationTest",
    size = "small",
    srcs = ["git/GitDestinationTest.java"],
    tags = ["local"],
    test_class = "com.google.copybara.git.GitDestinationTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "@truth//jar",
        "//java/com/google/copybara:copybara_lib",
        "//third_party/bazel:shell",
    ],
)

java_test(
    name = "GitOriginTest",
    size = "small",
    srcs = ["git/GitOriginTest.java"],
    tags = ["local"],
    test_class = "com.google.copybara.git.GitOriginTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "@truth//jar",
        "//java/com/google/copybara:copybara_lib",
        "//third_party/bazel:shell",
    ],
)

java_test(
    name = "DeletePathTest",
    size = "small",
    srcs = ["transform/DeletePathTest.java"],
    test_class = "com.google.copybara.transform.DeletePathTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "@jimfs//jar",
        "@truth//jar",
        "//java/com/google/copybara:copybara_lib",
    ],
)

java_test(
    name = "ReplaceRegexTest",
    size = "small",
    srcs = ["transform/ReplaceRegexTest.java"],
    test_class = "com.google.copybara.transform.ReplaceRegexTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "@jimfs//jar",
        "@truth//jar",
        "//java/com/google/copybara:copybara_lib",
    ],
)
