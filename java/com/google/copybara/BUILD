# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_binary(
    name = "copybara",
    srcs = ["Main.java"],
    main_class= "com.google.copybara.Main",
    deps = [
        ":copybara_lib",
        "@guava//jar",
        "@jcommander//jar",
    ],
    visibility = [
      "//copybara/integration:__pkg__",
    ]
)

java_library(
    name = "copybara_lib",
    srcs = glob(["**/*.java"], exclude = ["Main.java"]),
    deps = [
       "@guava//jar",
       "@jcommander//jar",
       "@snakeyaml//jar",
       "//third_party/bazel:shell",
    ],
    visibility = [
      "//visibility:public",
    ]
)
