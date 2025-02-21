licenses(["notice"])  # Apache 2.0

package_group(
    name = "copybara_pkgs",
    packages = [
        "//java/com/google/copybara/...",
        "//javatests/com/google/copybara/...",
    ],
)

package(
    default_visibility = [":copybara_pkgs"],
)

java_library(
    name = "guava",
    exports = [
        "@copybara_maven//:com_google_guava_failureaccess",
        "@copybara_maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "guava_testlib",
    testonly = 1,
    exports = [
        "@copybara_maven//:com_google_guava_guava_testlib",
    ],
)

java_library(
    name = "apache_commons_compress",
    exports = [
        "@copybara_maven//:org_apache_commons_commons_compress",
    ],
)

java_library(
    name = "autocommon",
    exports = [
        "@copybara_maven//:com_google_auto_auto_common",
    ],
)

java_plugin(
    name = "autovalue_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@copybara_maven//:com_google_auto_value_auto_value",
        "@copybara_maven//:com_google_auto_value_auto_value_annotations",
    ],
)

java_library(
    name = "autovalue",
    exported_plugins = [
        ":autovalue_plugin",
    ],
    # TODO(matvore): What to export to give access to @AutoValue annotation
    # without depending on the processor at runtime?
    exports = [
        "@copybara_maven//:com_google_auto_value_auto_value",
        "@copybara_maven//:com_google_auto_value_auto_value_annotations",
    ],
)

java_library(
    name = "jcommander",
    exports = [
        "@maven//:org_jcommander_jcommander",
    ],
)

java_library(
    name = "jimfs",
    exports = [
        "@copybara_maven//:com_google_jimfs_jimfs",
    ],
)

java_library(
    name = "junit",
    testonly = 1,
    exports = [
        "@copybara_maven//:junit_junit",
    ],
)

java_library(
    name = "testparameterinjector",
    testonly = 1,
    exports = [
        "@copybara_maven//:com_google_testparameterinjector_test_parameter_injector",
    ],
)

java_library(
    name = "re2j",
    exports = [
        "@copybara_maven//:com_google_re2j_re2j",
    ],
)

java_library(
    name = "shell",
    exports = [
        "//third_party/bazel:shell",
    ],
)

java_library(
    name = "truth",
    testonly = 1,
    exports = [
        "@copybara_maven//:com_google_truth_extensions_truth_java8_extension",
        "@copybara_maven//:com_google_truth_truth",
        "@copybara_maven//:com_googlecode_java_diff_utils_diffutils",
    ],
)

java_library(
    name = "google_code_gson",
    exports = [
        "@copybara_maven//:com_google_code_gson_gson",
    ],
)

java_library(
    name = "google_http_client",
    exports = [
        ":google_code_gson",
        "@copybara_maven//:com_google_http_client_google_http_client",
        "@copybara_maven//:com_google_http_client_google_http_client_gson",
        "@copybara_maven//:commons_codec_commons_codec",
    ],
)

java_library(
    name = "google_http_client_test",
    testonly = 1,
    exports = [
        "@copybara_maven//:com_google_http_client_google_http_client_test",
    ],
)

java_library(
    name = "mockito",
    testonly = 1,
    exports = [
        "@copybara_maven//:org_mockito_mockito_core",
    ],
    runtime_deps = [
        "@copybara_maven//:net_bytebuddy_byte_buddy",
        "@copybara_maven//:net_bytebuddy_byte_buddy_agent",
        "@copybara_maven//:org_objenesis_objenesis",
    ],
)

java_library(
    name = "jsr305",
    exports = [
        "@copybara_maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "starlark",
    exports = [
        "@io_bazel//src/main/java/net/starlark/java/annot",
        "@io_bazel//src/main/java/net/starlark/java/eval",
        "@io_bazel//src/main/java/net/starlark/java/lib/json",
        "@io_bazel//src/main/java/net/starlark/java/syntax",
    ],
)

java_library(
    name = "flogger",
    exports = [
        "@copybara_maven//:com_google_flogger_flogger",
    ],
    runtime_deps = [
        "@copybara_maven//:com_google_flogger_flogger_system_backend",
    ],
)

java_library(
    name = "error_prone",
    exports = [
        "@io_bazel//third_party:error_prone_annotations",
    ],
)

java_library(
    name = "hg_testing",
    testonly = 1,
    exports = [
        "//java/com/google/copybara/hg/testing",
    ],
)

java_library(
    name = "tomlj",
    exports = [
        "@copybara_maven//:org_tomlj_tomlj",
    ],
)

java_library(
    name = "jsoup",
    exports = [
        "@copybara_maven//:org_jsoup_jsoup",
    ],
)

java_library(
    name = "sun_mail",
    exports = [
        "@copybara_maven//:com_sun_mail_javax_mail",
    ],
)

java_library(
    name = "protobuf_lite",
    exports = [
        "@copybara_maven//:com_google_protobuf_protobuf_lite",
    ],
)

java_library(
    name = "tukaani_xz",
    exports = [
        "@copybara_maven//:org_tukaani_xz",
    ],
)

# Required temporarily until @io_bazel//src/main/java/com/google/devtools/build/lib/syntax/...
# is fixed
exports_files(["bazel.patch"])
