# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["**/*.java"]),
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//java/com/google/copybara:copybara_lib",
        "//third_party:guava",
        "//third_party:jimfs",
        "//third_party:joda_time",
        "//third_party:jsr305",
        "//third_party:truth",
    ],
)
