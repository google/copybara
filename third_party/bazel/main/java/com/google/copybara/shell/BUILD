load("@rules_java//java:defs.bzl", "java_library")

licenses(["notice"])  # Apache 2.0


package(
    default_visibility = [
        "//visibility:public",
    ],
)

java_library(
    name = "shell",
    srcs = glob(["*.java"]),
    deps = ["@copybara_maven//:com_google_guava_guava"],
)
