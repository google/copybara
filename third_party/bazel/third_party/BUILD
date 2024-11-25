load("@rules_java//java:defs.bzl", "java_import", "java_library", "java_plugin")
load("@rules_license//rules:license.bzl", "license")

package(default_visibility = ["//visibility:public"])

# Filegroup to ship the sources to the Bazel embededded tools
# This filegroup should contains all GPL with classpath exception
# and LGPL code that we use in Bazel.
filegroup(
    name = "gpl-srcs",
    srcs = [],
)

alias(
    name = "apache_commons_collections",
    actual = "@copybara_maven//:commons_collections_commons_collections",
)

alias(
    name = "apache_commons_lang",
    actual = "@copybara_maven//:commons_lang_commons_lang",
)

alias(
    name = "apache_commons_compress",
    actual = "@copybara_maven//:org_apache_commons_commons_compress",
)

alias(
    name = "apache_commons_pool2",
    actual = "@copybara_maven//:org_apache_commons_commons_pool2",
)

alias(
    name = "apache_velocity",
    actual = "@copybara_maven//:org_apache_velocity_velocity",
)

java_library(
    name = "api_client",
    exports = [
        "@copybara_maven//:com_google_api_client_google_api_client",
        "@copybara_maven//:com_google_api_client_google_api_client_gson",
        "@copybara_maven//:com_google_http_client_google_http_client",
        "@copybara_maven//:com_google_http_client_google_http_client_gson",
    ],
    runtime_deps = [
        ":gson",
    ],
)

java_import(
    name = "asm-analysis",
    jars = ["asm/asm-analysis-9.6.jar"],
    srcjar = "asm/asm-analysis-9.6-sources.jar",
    runtime_deps = [":asm-tree"],
)

java_import(
    name = "asm-commons",
    jars = ["asm/asm-commons-9.6.jar"],
    srcjar = "asm/asm-commons-9.6-sources.jar",
    runtime_deps = [":asm-tree"],
)

java_import(
    name = "asm-tree",
    jars = ["asm/asm-tree-9.6.jar"],
    srcjar = "asm/asm-tree-9.6-sources.jar",
    runtime_deps = [":asm"],
)

java_import(
    name = "asm-util",
    jars = ["asm/asm-util-9.6.jar"],
    srcjar = "asm/asm-util-9.6-sources.jar",
    runtime_deps = [":asm-tree"],
)

java_library(
    name = "auth",
    exports = [
        "@copybara_maven//:com_google_auth_google_auth_library_credentials",
        "@copybara_maven//:com_google_auth_google_auth_library_oauth2_http",
    ],
    runtime_deps = [
        ":api_client",
        ":guava",
    ],
)

java_plugin(
    name = "auto_annotation_plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        ":apache_commons_collections",
        ":apache_velocity",
        ":auto_common",
        ":auto_service_lib",
        ":auto_value_lib",
        ":guava",
        ":jsr305",
        ":tomcat_annotations_api",
    ],
)

alias(
    name = "auto_common",
    actual = "@copybara_maven//:com_google_auto_auto_common",
)

java_library(
    name = "auto_service",
    exported_plugins = [
        ":auto_service_plugin",
    ],
    exports = [
        ":auto_service_api",
    ],
)

java_plugin(
    name = "auto_service_plugin",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    deps = [
        ":auto_common",
        ":auto_service_lib",
        ":guava",
    ],
)

java_library(
    name = "auto_service_api",
    exports = [
        "@copybara_maven//:com_google_auto_service_auto_service_annotations",
    ],
)

java_library(
    name = "auto_service_lib",
    exports = [
        "@copybara_maven//:com_google_auto_service_auto_service",
        "@copybara_maven//:com_google_auto_service_auto_service_annotations",
    ],
)

java_plugin(
    name = "auto_value_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        ":apache_commons_collections",
        ":apache_velocity",
        ":auto_common",
        ":auto_service_lib",
        ":auto_value_lib",
        ":guava",
        ":tomcat_annotations_api",
    ],
)

java_plugin(
    name = "auto_oneof_plugin",
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = [
        ":apache_commons_collections",
        ":apache_velocity",
        ":auto_common",
        ":auto_service_lib",
        ":auto_value_lib",
        ":guava",
        ":tomcat_annotations_api",
    ],
)

java_plugin(
    name = "auto_builder_plugin",
    processor_class = "com.google.auto.value.processor.AutoBuilderProcessor",
    deps = [
        ":apache_commons_collections",
        ":apache_velocity",
        ":auto_common",
        ":auto_service_lib",
        ":auto_value_lib",
        ":guava",
        ":tomcat_annotations_api",
    ],
)

java_plugin(
    name = "auto_value_gson_plugin",
    processor_class = "com.ryanharter.auto.value.gson.factory.AutoValueGsonAdapterFactoryProcessor",
    deps = [
        "@copybara_maven//:com_ryanharter_auto_value_auto_value_gson_extension",
        "@copybara_maven//:com_ryanharter_auto_value_auto_value_gson_factory",
    ],
)

java_library(
    name = "auto_value",
    exported_plugins = [
        ":auto_annotation_plugin",
        ":auto_builder_plugin",
        ":auto_oneof_plugin",
        ":auto_value_plugin",
        ":auto_value_gson_plugin",
    ],
    exports = [
        ":auto_value_api",
        ":tomcat_annotations_api",
        "@copybara_maven//:com_ryanharter_auto_value_auto_value_gson_runtime",
    ],
)

java_library(
    name = "auto_value_api",
    exports = [
        "@copybara_maven//:com_google_auto_value_auto_value_annotations",
    ],
)

java_library(
    name = "auto_value_lib",
    exports = [
        "@copybara_maven//:com_google_auto_value_auto_value",
        "@copybara_maven//:com_google_auto_value_auto_value_annotations",
    ],
)

alias(
    name = "checker_framework_annotations",
    actual = "@copybara_maven//:org_checkerframework_checker_qual",
)

alias(
    name = "gson",
    actual = "@copybara_maven//:com_google_code_gson_gson",
)

alias(
    name = "caffeine",
    actual = "@copybara_maven//:com_github_ben_manes_caffeine_caffeine",
)

java_binary(
    name = "proguard",
    main_class = "proguard.ProGuard",
    runtime_deps = ["@copybara_maven//:com_guardsquare_proguard_base"],
)

java_library(
    name = "error_prone_annotations",
    exports = [
        "@copybara_maven//:com_google_errorprone_error_prone_annotations",
        "@copybara_maven//:com_google_errorprone_error_prone_type_annotations",
    ],
)

java_library(
    name = "error_prone",
    exports = [
        ":error_prone_annotations",
        "@copybara_maven//:com_google_errorprone_error_prone_check_api",
        "@copybara_maven//:com_google_errorprone_error_prone_core",
    ],
)

alias(
    name = "jcip_annotations",
    actual = "@copybara_maven//:com_github_stephenc_jcip_jcip_annotations",
)

alias(
    name = "pcollections",
    actual = "@copybara_maven//:org_pcollections_pcollections",
)

java_library(
    name = "guava",
    applicable_licenses = [":guava_license"],
    exports = [
        ":error_prone_annotations",
        ":jcip_annotations",
        ":jsr305",
        "@copybara_maven//:com_google_guava_guava",
    ],
)

license(
    name = "guava_license",
    package_name = "guava/31.1",
    license_kinds = [
        "@rules_license//licenses/spdx:Apache-2.0",
    ],
    license_text = "guava/LICENSE",
)

java_library(
    name = "flogger",
    applicable_licenses = [":flogger_license"],
    exports = [
        "@copybara_maven//:com_google_flogger_flogger",
        "@copybara_maven//:com_google_flogger_flogger_system_backend",
        "@copybara_maven//:com_google_flogger_google_extensions",
    ],
)

license(
    name = "flogger_license",
    package_name = "flogger/0.5.1",
    license_kinds = [
        "@rules_license//licenses/spdx:Apache-2.0",
    ],
)

alias(
    name = "javax_activation",
    actual = "@copybara_maven//:javax_activation_javax_activation_api",
)

# javax.annotation.Generated is not included in the default root modules in 9,
# see: http://openjdk.java.net/jeps/320.
java_library(
    name = "javax_annotations",
    neverlink = 1,  # @Generated is source-retention
    exports = ["@copybara_maven//:javax_annotation_javax_annotation_api"],
)

alias(
    name = "rxjava3",
    actual = "@copybara_maven//:io_reactivex_rxjava3_rxjava",
)

alias(
    name = "jsr305",
    actual = "@copybara_maven//:com_google_code_findbugs_jsr305",
)

alias(
    name = "jsr330_inject",
    actual = "@copybara_maven//:javax_inject_javax_inject",
)

alias(
    name = "tomcat_annotations_api",
    actual = "@copybara_maven//:org_apache_tomcat_tomcat_annotations_api",
)

alias(
    name = "java-diff-utils",
    actual = "@copybara_maven//:io_github_java_diff_utils_java_diff_utils",
)

# Testing

alias(
    name = "compile_testing",
    testonly = 1,
    actual = "@copybara_maven//:com_google_testing_compile_compile_testing",
)

alias(
    name = "guava-testlib",
    testonly = 1,
    actual = "@copybara_maven//:com_google_guava_guava_testlib",
)

# Not test_only due to //src/java_tools/junitrunner/java/com/google/testing/junit/junit4:runner
java_library(
    name = "junit4",
    exports = [
        "@copybara_maven//:junit_junit",
        "@copybara_maven//:org_hamcrest_hamcrest_core",
    ],
)

alias(
    name = "jimfs",
    testonly = 1,
    actual = "@copybara_maven//:com_google_jimfs_jimfs",
)

alias(
    name = "mockito",
    testonly = 1,
    actual = "@copybara_maven//:org_mockito_mockito_core",
)

alias(
    name = "turbine_direct",
    actual = "@copybara_maven//:com_google_turbine_turbine",
)

alias(
    name = "turbine",
    actual = "@copybara_maven//:com_google_turbine_turbine",
)

java_library(
    name = "truth",
    testonly = 1,
    exports = [
        "@copybara_maven//:com_google_truth_extensions_truth_java8_extension",
        "@copybara_maven//:com_google_truth_extensions_truth_proto_extension",
        "@copybara_maven//:com_google_truth_truth",
    ],
)

alias(
    name = "xz",
    actual = "@copybara_maven//:org_tukaani_xz",
)

# To be used by the starlark example.
filegroup(
    name = "junit4-jars",
    srcs = [
        "@copybara_maven//:junit_junit_file",
        "@copybara_maven//:org_hamcrest_hamcrest_core_file",
    ],
)

config_setting(
    name = "k8",
    values = {"host_cpu": "k8"},
)

config_setting(
    name = "piii",
    values = {"host_cpu": "piii"},
)

config_setting(
    name = "arm",
    values = {"host_cpu": "arm"},
)

config_setting(
    name = "aarch64",
    values = {"host_cpu": "aarch64"},
)

config_setting(
    name = "freebsd",
    values = {"host_cpu": "freebsd"},
)

config_setting(
    name = "openbsd",
    values = {"host_cpu": "openbsd"},
)

config_setting(
    name = "s390x",
    values = {"host_cpu": "s390x"},
)

config_setting(
    name = "ppc",
    values = {"host_cpu": "ppc"},
)
