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
        ":copybara_lib",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third-party:snakeyaml",
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
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:shell",
        "//third_party:snakeyaml",
    ],
)
