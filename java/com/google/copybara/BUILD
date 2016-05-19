# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

load(":docs.bzl", "doc_generator")

export_files = [
    "doc.sh",
    "docs.bzl",
]

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
        "//third_party:jsr305",
        "//third_party:snakeyaml",
    ],
)

doc_generator(
    name = "docs",
    deps = [":copybara"],
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
        "//java/com/google/copybara/doc:annotations",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:joda_time",
        "//third_party:shell",
        "//third_party:snakeyaml",
    ],
    javacopts = ["-Xlint:unchecked"],
)
