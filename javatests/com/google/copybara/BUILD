# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_test(
    name = "CopybaraTest",
    size = "small",
    srcs = ["CopybaraTest.java"],
    test_class = "com.google.copybara.CopybaraTest",
    deps = [
        "//java/com/google/copybara:copybara_lib",
        "//third_party:jcommander",
        "//third_party:junit4",
        "//third_party:truth",
    ],
)

java_test(
    name = "CommandTest",
    size = "small",
    srcs = ["CommandTest.java"],
    test_class = "com.google.copybara.CommandTest",
    deps = [
        "//third_party:junit4",
        "//third_party:shell",
        "//third_party:truth",
    ],
)

java_test(
    name = "GitDestinationTest",
    size = "small",
    srcs = ["git/GitDestinationTest.java"],
    tags = ["local"],
    test_class = "com.google.copybara.git.GitDestinationTest",
    deps = [
        "//java/com/google/copybara:copybara_lib",
        "//third_party:junit4",
        "//third_party:shell",
        "//third_party:truth",
    ],
)

java_test(
    name = "GitOriginTest",
    size = "small",
    srcs = ["git/GitOriginTest.java"],
    tags = ["local"],
    test_class = "com.google.copybara.git.GitOriginTest",
    deps = [
        "//java/com/google/copybara:copybara_lib",
        "//third_party:junit4",
        "//third_party:shell",
        "//third_party:truth",
    ],
)

java_test(
    name = "DeletePathTest",
    size = "small",
    srcs = ["transform/DeletePathTest.java"],
    test_class = "com.google.copybara.transform.DeletePathTest",
    deps = [
        "//java/com/google/copybara:copybara_lib",
        "//third_party:jimfs",
        "//third_party:junit4",
        "//third_party:truth",
    ],
)

java_test(
    name = "ReplaceRegexTest",
    size = "small",
    srcs = ["transform/ReplaceRegexTest.java"],
    test_class = "com.google.copybara.transform.ReplaceRegexTest",
    deps = [
        "//java/com/google/copybara:copybara_lib",
        "//third_party:jimfs",
        "//third_party:junit4",
        "//third_party:truth",
    ],
)
