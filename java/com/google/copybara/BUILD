# Copyright 2016 Google Inc. All Rights Reserved.

java_binary(
    name = "copybara",
    srcs = ["Main.java"],
    main_class= "com.google.copybara.Main",
    deps = [":copybara_lib"],
    visibility = [
      "//copybara/integration:__pkg__",
    ]
)

java_library(
    name = "copybara_lib",
    srcs = glob(["**/*.java"], exclude = ["Main.java"]),
    deps = [
       "@guava//jar",
       "@snakeyaml//jar",
    ],
    visibility = [
      "//javatests/com/google/copybara:__pkg__",
    ]
)
