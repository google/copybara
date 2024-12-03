load("@rules_license//rules:license.bzl", "license")

licenses(["notice"])  # Apache 2.0

package(
    default_visibility = [
        "//visibility:public",
    ],
)

license(
    name = "license",
    package_name = "bazelbuild/bazel",
    copyright_notice = "Copyright © 2014 The Bazel Authors. All rights reserved.",
    license_kinds = [
        "@rules_license//licenses/spdx:Apache-2.0",
    ],
    license_text = "LICENSE",
)

java_library(
    name = "shell",
    srcs = glob(["main/**/*.java"]),
    deps = ["@copybara_maven//:com_google_guava_guava"],
)
