# Copybara

*A tool for transforming and moving code between repositories.*

## Getting Started

  * [Install JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).
  * [Install Bazel](http://bazel.io/docs/install.html).
  * Build: *bazel build //java/com/google/copybara*.
  * Tests: *bazel test //...*.

### Optional tips

  * If you want to see the test errors in Bazel, instead of having to cat the logs, add this line to your ~/.bazelrc *test --test_output=streamed*.

