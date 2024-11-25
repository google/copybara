# Description: Bazel's Starlark interpreter

load("@rules_java//java:defs.bzl", "java_library")

licenses(["notice"])  # Apache 2.0

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//third_party/bazel/main/java/net/starlark/java:bazel"],
)

# The Starlark evaluator
java_library(
    name = "eval",
    srcs = [
        "BuiltinFunction.java",
        "CallUtils.java",
        "CpuProfiler.java",
        "Debug.java",
        "Dict.java",
        "Eval.java",
        "EvalException.java",
        "EvalUtils.java",
        "FlagGuardedValue.java",
        "FormatParser.java",
        "GuardedValue.java",
        "HasBinary.java",
        "ImmutableSingletonStarlarkList.java",
        "ImmutableStarlarkList.java",
        "JNI.java",
        "LazyImmutableStarlarkList.java",
        "MethodDescriptor.java",
        "MethodLibrary.java",
        "Module.java",
        "Mutability.java",
        "MutableStarlarkList.java",
        "NoneType.java",
        "ParamDescriptor.java",
        "Printer.java",
        "RangeList.java",
        "RegularImmutableStarlarkList.java",
        "RegularTuple.java",
        "Sequence.java",
        "SingletonTuple.java",
        "Starlark.java",
        "StarlarkCallable.java",
        "StarlarkFloat.java",
        "StarlarkFunction.java",
        "StarlarkIndexable.java",
        "StarlarkInt.java",
        "StarlarkIterable.java",
        "StarlarkList.java",
        "StarlarkMembershipTestable.java",
        "StarlarkSemantics.java",
        "StarlarkSet.java",
        "StarlarkThread.java",
        "StarlarkValue.java",
        "StringModule.java",
        "Structure.java",
        "SymbolGenerator.java",
        "Tuple.java",
    ],
    visibility = ["//third_party/bazel/main/java/net/starlark/java:clients"],
    deps = [
        # Do not add Bazel or Google dependencies here!
        "//third_party/bazel/main/java/net/starlark/java/annot",
        "//third_party/bazel/main/java/net/starlark/java/spelling",
        "//third_party/bazel/main/java/net/starlark/java/syntax",
        "//third_party/bazel/third_party:error_prone_annotations",
        "//third_party/bazel/third_party:auto_value",
        "//third_party/bazel/third_party:flogger",
        "//third_party/bazel/third_party:guava",
        "//third_party/bazel/third_party:jsr305",
    ],
)

# Dynamic library for Starlark CPU profiler.
# The application or test must arrange for the library to be added
# to the java.library.path directory (e.g. by 'java -Djava.library.path=<dir>').
# What a mess.
filegroup(
    name = "cpu_profiler",
    srcs = select({
        "@platforms//os:macos": [":libcpu_profiler.dylib"],
        "@platforms//os:windows": [":cpu_profiler.dll"],
        "//conditions:default": [":libcpu_profiler.so"],  # POSIX
    }),
    visibility = ["//third_party/bazel/main/java/net/starlark/java:clients"],
)

genrule(
    name = "cpu_profiler_darwin",
    srcs = ["libcpu_profiler.so"],
    outs = ["libcpu_profiler.dylib"],
    cmd = "cp $< $@",
)

genrule(
    name = "cpu_profiler_windows",
    srcs = ["libcpu_profiler.so"],
    outs = ["cpu_profiler.dll"],
    cmd = "cp $< $@",
)

# The C++ portion of the Starlark CPU profiler.
cc_binary(
    name = "libcpu_profiler.so",
    srcs = select({
        "@platforms//os:macos": ["cpu_profiler_posix.cc"],
        "@platforms//os:linux": ["cpu_profiler_posix.cc"],
        "//conditions:default": ["cpu_profiler_unimpl.cc"],
    }),
    linkshared = 1,
    deps = ["@bazel_tools//tools/jdk:jni"],
)
