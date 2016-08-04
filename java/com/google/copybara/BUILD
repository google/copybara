# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

package(default_visibility = ["//visibility:public"])

load(":docs.bzl", "doc_generator", "skylark_doc_generator")

export_files = [
    "doc.sh",
    "docs.bzl",
]

java_binary(
    name = "copybara",
    srcs = ["Main.java"],
    javacopts = ["-Xlint:unchecked", "-source", "1.7"],
    main_class = "com.google.copybara.Main",
    deps = [
        ":copybara_lib",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:snakeyaml",
    ],
)

doc_generator(
    name = "yaml_docs",
    deps = [":copybara"],
)

skylark_doc_generator(
    name = "docs",
    deps = [":copybara"],
)

java_library(
    name = "copybara_lib",
    srcs = glob(
        ["**/*.java"],
        exclude = ["Main.java"],
    ),
    javacopts = ["-Xlint:unchecked", "-source", "1.7"],
    deps = [
        "//java/com/google/copybara/doc:annotations",
        "//third_party:autovalue",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
        "//third_party:joda_time",
        "//third_party:skylark-lang",
        "//third_party:shell",
        "//third_party:snakeyaml",
    ],
)
