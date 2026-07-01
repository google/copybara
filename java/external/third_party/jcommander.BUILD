package(
    default_visibility = ["//visibility:public"],
)
JAVACOPTS = [
    "-Xlint:unchecked",
    "-Werror:-unchecked,-rawtypes",
    "-Xep:CollectionIncompatibleType:OFF",
]

java_library(
    name = "jcommander",
    srcs = glob(["**/src/main/**/*.java"]),
    javacopts = JAVACOPTS,
)
