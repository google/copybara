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

Currently, the only supported type of repository is Git. Support for other repositories types (such
as Hg) will be added in the future.

Copybara is similar to MOE, which is a tool to synchronize between source code repositories.
Copybara’s design has learned much from MOE, and will have ongoing support. MOE will not be
deprecated until Copybara is able to provide satisfactory services to MOE customers.

## Getting Started using Copybara

Copybara doesn't have a release process yet, so you need to compile from HEAD. In order to do that
you need:

  * [Install JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).
  * [Install Bazel](https://docs.bazel.build/versions/master/install.html).
  * Build:
      * `bazel build //java/com/google/copybara`.
	  * `bazel build //java/com/google/copybara:copybara_deploy.jar` to create a executable uberjar.
  * Tests: *bazel test //...* if you want to ensure you are not using a broken version.

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

Note that configuration files can be stored in any place, even in a local folder. We recommend to
use a VCS (like git) to store them; treat them as source code.

### Using Docker to build and run Copybara

You can build copybara using Docker like so

```
docker build --rm -t copybara .
```

Once this has finished building you can run the image like so from the root of the code you are trying to use Copybara on:

```
docker run -it -v "$(pwd)":/usr/src/app copybara

```

Two environment variables exist to allow you to run copybara:
* *COPYBARA_CONFIG* allows you to specify a config name that isn't `copy.bara.sky`
* *COPYBARA_COMMANDS* allows you to send in a specific list of commands to run, such as `--last-rev`, etc...

```
docker run -it -e COPYBARA_CONFIG='{nameofconfigfile}' -e COPYBARA_COMMANDS='{listofcommands}' -v "$(pwd)":/usr/src/app copybara
```

#### Git Config and Credentials

There are a number of ways by which to share your git config and ssh credentials with the docker container, an example with OS X is below:

```
docker run -it -v ~/.ssh:/root/.ssh -v ~/.gitconfig:/root/.gitconfig -v "$(pwd)":/usr/src/app copybara 

```

## Documentation

We are still working on the documentation. Here are some resources:

  * [Reference documentation](docs/reference.md)
  * [Examples](docs/examples.md)
  
## Contact us

If you have any questions about how Copybara works please contact us at our [mailing list](https://groups.google.com/forum/#!forum/copybara-discuss)

## Optional tips

  * If you want to see the test errors in Bazel, instead of having to cat the logs, add this line to your `~/.bazelrc: *test --test_output=streamed*`.

