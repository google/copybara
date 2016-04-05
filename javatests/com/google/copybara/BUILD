# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_test(
    name = "CopybaraTest",
    size = "small",
    srcs = ["CopybaraTest.java"],
    test_class = "com.google.copybara.CopybaraTest",
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
    test_class = "com.google.copybara.CommandTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "//third_party/bazel:shell",
        "@truth//jar",
    ],
)

java_test(
    name = "GitDestinationTest",
    size = "small",
    srcs = ["git/GitDestinationTest.java"],
    test_class = "com.google.copybara.git.GitDestinationTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "//java/com/google/copybara:copybara_lib",
        "//third_party/bazel:shell",
        "@truth//jar",
    ],
    tags = ["local"],
)

java_test(
    name = "GitRepositoryTest",
    size = "small",
    srcs = ["git/GitRepositoryTest.java"],
    test_class = "com.google.copybara.git.GitRepositoryTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "//java/com/google/copybara:copybara_lib",
        "//third_party/bazel:shell",
        "@truth//jar",
    ],
    tags = ["local"],
)

java_test(
    name = "DeletePathTest",
    size = "small",
    srcs = ["transform/DeletePathTest.java"],
    test_class = "com.google.copybara.transform.DeletePathTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "//java/com/google/copybara:copybara_lib",
        "@jimfs//jar",
        "@truth//jar",
    ],
)

java_test(
    name = "ReplaceRegexTest",
    size = "small",
    srcs = ["transform/ReplaceRegexTest.java"],
    test_class = "com.google.copybara.transform.ReplaceRegexTest",
    deps = [
        "@bazel_tools//third_party:junit4",
        "//java/com/google/copybara:copybara_lib",
        "@jimfs//jar",
        "@truth//jar",
    ],
)
