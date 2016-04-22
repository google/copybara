# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

sh_test(
    name = "tool_test",
    srcs = ["tool_test.sh"],
    data = [
        "//java/com/google/copybara",
        "//third_party/bazel/bashunit",
    ],
    tags = ["local"],
    visibility = ["//visibility:public"],
)

py_binary(
    name = "cram",
    srcs = ["cram.py"],
    deps = ["@cram//:cramlib"],
)
