# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_library(
    name = "testing",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/copybara:copybara_lib",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
    visibility = [
        "//javatests:__subpackages__",
    ],
)
