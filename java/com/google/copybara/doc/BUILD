# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

java_plugin(
    name = "document",
    srcs = glob(["**/*.java"]),
    processor_class = "com.google.copybara.doc.MarkdownGenerator",
    visibility = [
        "//visibility:public",
    ],

    deps = [
        "//third_party:autocommon",
        "//third_party:guava",
    ],
)

java_library(
    name = "annotations",
    srcs = glob(["annotations/**/*.java"]),
    exported_plugins = [":document"],
    visibility = [
        "//visibility:public",
    ],
)