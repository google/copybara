# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["**/*.java"]),
    javacopts = ["-Xlint:unchecked", "-source", "1.7"],
    deps = [
        "//java/com/google/copybara:base",
        "//java/com/google/copybara:copybara_lib",
        "//java/com/google/copybara/testing",
        "//third_party:truth",
    ],
    visibility = ["//visibility:public"],
)
