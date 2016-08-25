package(default_visibility = ["//visibility:public"])

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_library(
    name = "util",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:shell",
        "//third_party:skylark-lang",
    ],
    javacopts = [
        "-Xlint:unchecked",
        "-source",
        "1.7",
    ],
)
