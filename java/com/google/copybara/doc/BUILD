# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_plugin(
    name = "document",
    srcs = glob(["**/*.java"]),
    javacopts = ["-Xlint:unchecked", "-source", "1.7"],
    processor_class = "com.google.copybara.doc.MarkdownGenerator",
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//third_party:autocommon",
        "//third_party:guava",
        "//third_party:jcommander",
    ],
)

java_library(
    name = "annotations",
    srcs = glob(["annotations/**/*.java"]),
    javacopts = ["-Xlint:unchecked", "-source", "1.7"],
    exported_plugins = [":document"],
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//third_party:guava",
    ],
)
