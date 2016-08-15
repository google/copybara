# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions

package(default_visibility = ["//visibility:public"])

load(":docs.bzl", "doc_generator")

export_files = [
    "doc.sh",
    "docs.bzl",
]

JAVACOPTS = [
    "-Xlint:unchecked",
    "-source",
    "1.7",
]

java_binary(
    name = "copybara",
    srcs = ["Main.java"],
    javacopts = JAVACOPTS,
    main_class = "com.google.copybara.Main",
    deps = [
        ":base",
        ":copybara_lib",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:jsr305",
    ],
)

doc_generator(
    name = "docs",
    deps = [":copybara"],
)

# TODO(matvore): Put Destination.java in this set.
BASE_SRCS = [
    "Author.java",
    "Change.java",
    "ConfigValidationException.java",
    "EnvironmentException.java",
    "NonReversibleValidationException.java",
    "Option.java",
    "Origin.java",
    "RepoException.java",
    "Transformation.java",
    "TransformWork.java",
    "ValidationException.java",
    "VoidOperationException.java",
    "WorkflowOptions.java",
]

java_library(
    name = "base",
    srcs = BASE_SRCS,
    javacopts = JAVACOPTS,
    deps = [
        "//java/com/google/copybara/util/console",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:joda_time",
        "//third_party:jsr305",
        "//third_party:skylark-lang",
    ],
)

java_library(
    name = "copybara_lib",
    srcs = glob(
        ["**/*.java"],
        exclude = ["Main.java"] + BASE_SRCS,
    ),
    javacopts = JAVACOPTS,
    deps = [
        ":base",
        "//java/com/google/copybara/doc:annotations",
        "//java/com/google/copybara/transform",
        "//java/com/google/copybara/util",
        "//java/com/google/copybara/util/console",
        "//third_party:autovalue",
        "//third_party:guava",
        "//third_party:jcommander",
        "//third_party:joda_time",
        "//third_party:jsr305",
        "//third_party:shell",
        "//third_party:skylark-lang",
    ],
)
