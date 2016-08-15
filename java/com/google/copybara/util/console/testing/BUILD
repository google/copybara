# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["**/*.java"]),
    javacopts = ["-Xlint:unchecked", "-source", "1.7"],
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:truth",
    ],
)
