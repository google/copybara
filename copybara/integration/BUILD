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

load("//copybara/integration:cram.bzl", "cram_test")

#cram_test(
#    name = "hello",
#    srcs = ["hello.t"],
#)

#cram_test(
#    name = "test-preserve-env.t",
#    srcs = ["test-preserve-env.t"],
#    deps = ["//java/com/google/copybara"],
#)

#cram_test(
#    name = "test-help.t",
#    srcs = ["test-help.t"],
#    deps = ["//java/com/google/copybara"],
#)
