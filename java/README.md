# Copybara

*A tool for transforming and moving code between repositories.*

Copybara is a tool used internally at Google. It transforms and moves code between repositories.

Often, source code needs to exist in multiple repositories, and Copybara allows you to transform
and move source code between these repositories. A common case is a project that involves
maintaining a confidential repository and a public repository in sync.

Copybara requires you to choose one of the repositories to be the authoritative repository, so that
there is always one source of truth. However, the tool allows contributions to any repository, and
any repository can be used to cut a release.

The most common use case involves repetitive movement of code from one repository to another.
Copybara can also be used for moving code once to a new repository.

Examples uses of Copybara include:

  - Importing sections of code from a confidential repository to a public repository.

  - Importing code from a public repository to a confidential repository.

  - Importing a change from a non-authoritative repository into the authoritative repository. When
    a change is made in the non-authoritative repository (for example, a contributor in the public
    repository), Copybara transforms and moves that change into the appropriate place in the
    authoritative repository. Any merge conflicts are dealt with in the same way as an out-of-date
    change within the authoritative repository.

One of the main features of Copybara is that it is stateless, or more specifically, that it stores
the state in the destination repository (As a label in the commit message). This allows several
users (or a service) to use Copybara for the same config/repositories and get the same result.

Currently, the only supported type of repository is Git. Copybara is also able
to read from Mercurial repositories, but the feature is still experimental.
The extensible architecture allows adding bespoke origins and destinations
for almost any use case.
Official support for other repositories types will be added in the future.

## Example

```python
core.workflow(
    name = "default",
    origin = git.github_origin(
      url = "https://github.com/google/copybara.git",
      ref = "master",
    ),
    destination = git.destination(
        url = "file:///tmp/foo",
    ),

    # Copy everything but don't remove a README_INTERNAL.txt file if it exists.
    destination_files = glob(["third_party/copybara/**"], exclude = ["README_INTERNAL.txt"]),

    authoring = authoring.pass_thru("Default email <default@default.com>"),
    transformations = [
        core.replace(
                before = "//third_party/bazel/bashunit",
                after = "//another/path:bashunit",
                paths = glob(["**/BUILD"])),
        core.move("", "third_party/copybara")
    ],
)
```

Run:

```shell
$ (mkdir /tmp/foo ; cd /tmp/foo ; git init --bare)
$ copybara copy.bara.sky
```

## Getting Started using Copybara

The easiest way to start is with weekly "snapshot" releases, that include pre-built a binary.
Note that these are released automatically without any manual testing, version compatibility or correctness guarantees.

Choose a release from https://github.com/google/copybara/releases.

### Building from Source

To use an unreleased version of copybara, so you need to compile from HEAD.
In order to do that, you need to do the following:

  * [Install JDK 11](https://www.oracle.com/java/technologies/downloads/#java11).
  * [Install Bazel](https://bazel.build/install).
  * Clone the copybara source locally:
      * `git clone https://github.com/google/copybara.git`
  * Build:
      * `bazel build //java/com/google/copybara`
      * `bazel build //java/com/google/copybara:copybara_deploy.jar` to create an executable uberjar.
  * Tests: `bazel test //...` if you want to ensure you are not using a broken version. Note that
    certain tests require the underlying tool to be installed(e.g. Mercurial, Quilt, etc.). It is
    fine to skip those tests if your Pull Request is unrelated to those modules (And our CI will
    run all the tests anyway).

### System packages

These packages can be installed using the appropriate package manager for your
system.

#### Arch Linux

  * [`aur/copybara-git`][install/archlinux/aur-git]

[install/archlinux/aur-git]: https://aur.archlinux.org/packages/copybara-git "Copybara on the AUR"

### Using Intellij with Bazel plugin

If you use Intellij and the Bazel plugin, use this project configuration:

```
directories:
  copybara/integration
  java/com/google/copybara
  javatests/com/google/copybara
  third_party

targets:
  //copybara/integration/...
  //java/com/google/copybara/...
  //javatests/com/google/copybara/...
  //third_party/...
```

Note: configuration files can be stored in any place, even in a local folder.
We recommend using a VCS (like git) to store them; treat them as source code.

### Using pre-built Copybara in Bazel

If using a weekly snapshot release, install Copybara as follows:

1. Copybara ships with class files with version 65.0, so it must be run with Java Runtime 21 or greater. Add to your `.bazelrc` file: `run --java_runtime_version=remotejdk_21`
2. Use `http_jar` to download the release artifact.
   - In WORKSPACE: `load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_jar")`
   - In MODULE.bazel: `http_jar = use_repo_rule("@bazel_tools//tools/build_defs/repo:http.bzl", "http_jar")`
3. In WORKSPACE or MODULE.bazel, fill in the `[version]` placeholder:
    ```starlark
    http_jar(
        name = "com_github_google_copybara",
        # Fill in from https://github.com/google/copybara/releases/download/[version]/copybara_deploy.jar.sha256
        # sha256 = "",
        urls = ["https://github.com/google/copybara/releases/download/[version]/copybara_deploy.jar"],
    )
    ```
4. In any BUILD file (perhaps `/tools/BUILD.bazel`) declare the `java_binary`:
   ```starlark
   load("@rules_java//java:java_binary.bzl", "java_binary")
   java_binary(
      name = "copybara",
      main_class = "com.google.copybara.Main",
      runtime_deps = ["@com_github_google_copybara//jar"],
   )
   ```
5. Use that target with `bazel run`, for example `bazel run //tools:copybara -- migrate copy.bara.sky`

### Building Copybara from Source as an external Bazel repository

There are convenience macros defined for all of Copybara's dependencies. Add the
following code to your `WORKSPACE` file, replacing `{{ sha256sum }}` and
`{{ commit }}` as necessary.

```bzl
http_archive(
  name = "com_github_google_copybara",
  sha256 = "{{ sha256sum }}",
  strip_prefix = "copybara-{{ commit }}",
  url = "https://github.com/google/copybara/archive/{{ commit }}.zip",
)

load("@com_github_google_copybara//:repositories.bzl", "copybara_repositories")

copybara_repositories()

load("@com_github_google_copybara//:repositories.maven.bzl", "copybara_maven_repositories")

copybara_maven_repositories()

load("@com_github_google_copybara//:repositories.go.bzl", "copybara_go_repositories")

copybara_go_repositories()
```

You can then build and run the Copybara tool from within your workspace:

```sh
bazel run @com_github_google_copybara//java/com/google/copybara -- <args...>
```

### Using Docker to build and run Copybara

*NOTE: Docker use is currently experimental, and we encourage feedback or contributions.*

You can build copybara using Docker like so

```sh
docker build --rm -t copybara .
```

Once this has finished building, you can run the image like so from the root of
the code you are trying to use Copybara on:

```sh
docker run -it -v "$(pwd)":/usr/src/app copybara help
```

#### Environment variables

In addition to passing cmd args to the container, you can also set the following
environment variables as an alternative:
* `COPYBARA_SUBCOMMAND=migrate`
  * allows you to change the command run, defaults to `migrate`
* `COPYBARA_CONFIG=copy.bara.sky`
  * allows you to specify a path to a config file, defaults to root `copy.bara.sky`
* `COPYBARA_WORKFLOW=default`
  * allows you to specify the workflow to run, defaults to `default`
* `COPYBARA_SOURCEREF=''`
  * allows you to specify the sourceref, defaults to none
* `COPYBARA_OPTIONS=''`
  * allows you to specify options for copybara, defaults to none

```sh
docker run \
    -e COPYBARA_SUBCOMMAND='validate' \
    -e COPYBARA_CONFIG='other.config.sky' \
    -v "$(pwd)":/usr/src/app \
    -it copybara
```

#### Git Config and Credentials

There are a number of ways by which to share your git config and ssh credentials
with the Docker container, an example is below:

```sh
docker run \
    -v ~/.gitconfig:/root/.gitconfig:ro \
    -v ~/.ssh:/root/.ssh \
    -v ${SSH_AUTH_SOCK}:${SSH_AUTH_SOCK} -e SSH_AUTH_SOCK
    -v "$(pwd)":/usr/src/app \
    -it copybara
```

## Documentation

We are still working on the documentation. Here are some resources:

  * [Reference documentation](docs/reference.md)
  * [Examples](docs/examples.md)
  * [Tutorial on how to get started](https://blog.kubesimplify.com/moving-code-between-git-repositories-with-copybara)

## Contact us

If you have any questions about how Copybara works, please contact us at our
[mailing list](https://groups.google.com/forum/#!forum/copybara-discuss).

## Optional tips

* If you want to see the test errors in Bazel, instead of having to `cat` the
  logs, add this line to your `~/.bazelrc`:

  ```
  test --test_output=streamed
  ```
