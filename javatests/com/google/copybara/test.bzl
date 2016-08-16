def all_tests(tests, deps, tags=[]):
  for file in tests:
    relative_target = file[:-5]
    suffix = relative_target.replace("/", ".")
    pos = PACKAGE_NAME.rfind("javatests/") + len("javatests/")
    test_class = PACKAGE_NAME[pos:].replace('/', '.',) + '.' + suffix
    native.java_test(
      name = file[:-5],
      srcs = [file],
      javacopts = ["-Xlint:unchecked", "-source", "1.7"],
      test_class = test_class,
      deps = deps + [
          # These deps are automatically included with Bazel, but not with the
          # internal BUILD system. So add them explicitly here.
          "//third_party:guava",
          "//third_party:jsr305",
          "//third_party:junit",
      ],
      tags = tags,
    )
