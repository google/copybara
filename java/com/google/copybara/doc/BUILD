# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_plugin(
    name = "document",
    srcs = glob([
        "skylark/**/*.java",
        "annotations/**/*.java",
    ]),
    javacopts = [
        "-Xlint:unchecked",
        "-source",
        "1.7",
    ],
    processor_class = "com.google.copybara.doc.skylark.MarkdownGenerator",
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//third_party:autocommon",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:skylark-lang",
    ],
)

java_library(
    name = "annotations",
    srcs = glob(["annotations/**/*.java"]),
    exported_plugins = [":document"],
    javacopts = [
        "-Xlint:unchecked",
        "-source",
        "1.7",
    ],
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//third_party:guava",
    ],
)
