# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_binary(
    name = "copybara",
    srcs = ["Main.java"],
    main_class = "com.google.copybara.Main",
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "@guava//jar",
        "@jcommander//jar",
        ":copybara_lib",
    ],
)

java_library(
    name = "copybara_lib",
    srcs = glob(
        ["**/*.java"],
        exclude = ["Main.java"],
    ),
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "@guava//jar",
        "@jcommander//jar",
        "@snakeyaml//jar",
        "//third_party/bazel:shell",
    ],
)
