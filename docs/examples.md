## Contents
  - [Git-to-Git import](#basic-git-to-git-import)
  - [Github SSH import](#github-ssh-basic-import)
  - [Mercurial to Git import](#mercurial-to-git-import)
  - [Transformations](#transformations)
  - [Subcommands](#subcommands)

## Basic git-to-git import

This example will import Copybara source code to an internal git repository
under ``$GIT/third_party/copybara``.

Assuming you have an existing git repository. For the example in ``/tmp/foo``. But it could be
a remote one:

```bash
mkdir /tmp/foo
cd /tmp/foo
git init --bare .
```

Create a ``copy.bara.sky`` config file like:

```python
url = "https://github.com/google/copybara.git"

core.workflow(
    name = "default",
    origin = git.origin(
        url = url,
        ref = "master",
    ),
    destination = git.destination(
        url = "file:///tmp/foo",
        fetch = "master",
        push = "master",
    ),
    # Copy everything but don't remove a README_INTERNAL.txt file if it exists.
    destination_files = glob(["third_party/copybara/**"], exclude = ["README_INTERNAL.txt"]),

    authoring = authoring.pass_thru("Default email <default@default.com>"),
    transformations = [
	    core.move("", "third_party/copybara"),
	],
)
```

Invoke the tool like:

```bash
copybara copy.bara.sky --force
```

``--force`` should only be needed for empty destination repositories or non-existent
branches in the destination. After the first import, it should be always invoked as:

```
copybara copy.bara.sky
```

## GitHub SSH basic import

This example will import private source code to an external GitHub repository, and uses SSH.

PROTIP: You will need to have an ssh key setup without a password to accomplish this, Copybara doesn't
currently support ssh with a password.

Create a ``copy.bara.sky`` config file like:

```python
# Update these references to your orginzations repos
sourceUrl = "git@github.com:organization/internal-repo.git"
destinationUrl = "git@github.com:organization/external-repo.git"

core.workflow(
    name = "default",
    origin = git.origin(
        url = sourceUrl,
        ref = "master",
    ),
    destination = git.destination(
        url = destinationUrl,
        fetch = "master",
        push = "master",
    ),
    # Change path to the folder you want to publish publicly
    origin_files = glob(["path/to/folder/you/want/exported/**"]),

    authoring = authoring.pass_thru("Default email <default@default.com>"),

    # Change the path here to the folder you want to publish publicly
    transformations = [
	    core.move("path/to/folder/you/want/exported", ""),
	],
)
```

Invoke the tool like:

```bash
copybara copy.bara.sky --force
```

``--force`` should only be needed for empty destination repositories or non-existent
branches in the destination. After the first import, it should be always invoked as:

```
copybara copy.bara.sky
```

After running through this example, you should see all the source from
the folder you selected in the external-repo at the root. This can be helpful if you
are only trying to move a subdirectory in your git repo out for public use.

## Mercurial to Git import
Let's set up a simple migration from a Mercurial repository to a git repository. Note that Mercurial
support is still experimental.

In this example, we will import source code from the
[Mercurial source repository](https://www.mercurial-scm.org/repo/hg/) to a local git repository.
We'll get started by setting up a local bare git repository.

```
$ mkdir /tmp/gitdest
$ cd /tmp/gitdest
$ git init --bare .
```
Next up is creating and editing a `copy.bara.sky` config file. The config file will contain the
details of our workflow. Using your text editor of choice, create and edit the config file:
```
$ vim /tmp/copy.bara.sky
```
We'll define in the config to pull changes from the default branch in the origin repository.
```
core.workflow(
  name = "default",
  origin = hg.origin(
    url = "https://www.mercurial-scm.org/repo/hg",
    ref = "default",
  ),
  destination = git.destination(
    url = "file:///tmp/gitdest",
  ),
  # Files that you want to import
  origin_files = glob(['**']),
  # Files that you want to copy
  destination_files = glob(['**']),
  # Set up a default author
  authoring = authoring.pass_thru("Default email <default@default.com>"),
  # Import mode
  mode = "SQUASH",
)
```
Now we can run Copybara with this config to import the changes. However, since the Mercurial
repository has many commits, we can just pull default branch revisions from the most recent 15
revisions in the repository, using the `--last-rev` flag.

```
$ copybara /tmp/copy.bara.sky --force --last-rev -15
```
If we wanted to pull all revisions from the default branch, we would omit the `--last-rev` flag.
Since we are using `SQUASH` mode, all commits from the origin repository will be "squashed" into a
single commit.

If we navigate to our git destination repository, we can run `git log` and see the commit that
was created.
```
$ cd /tmp/gitdest
$ git log
```


## Transformations

Let's say that we realized that we need to do some code transformations to the imported code.
We could use core.replace to do it. Here we look for ``//third_party/bazel/bashunit`` text
and we replace it with the correct destination one just for BUILD files:


```python
url = "https://github.com/google/copybara.git"

core.workflow(
    name = "default",
    origin = git.origin(
        url = url,
        ref = "master",
    ),
    destination = git.destination(
        url = "file:///tmp/foo",
        fetch = "master",
        push = "master",
    ),

    # Copy everything but don't remove a README_INTERNAL.txt file if it exists.
    destination_files = glob(["third_party/copybara/**"], exclude = ["README_INTERNAL.txt"]),

    authoring = authoring.pass_thru("Default email <default@default.com>"),

    transformations = [
        core.replace(
            before = "//third_party/bazel/bashunit",
            after = "//another/path:bashunit",
            paths = glob(["**/BUILD"]),
        ),
        core.move("", "third_party/copybara"),
    ],
)
```

## Subcommands

The tool accepts different subcommands, _à la_ Bazel. If no
command is specified, *migrate* is executed by default. These two commands are
equivalent:

```shell
$ copybara copy.bara.sky
$ copybara migrate copy.bara.sky
```

You can validate your configuration running:

```shell
$ copybara validate copy.bara.sky
Copybara source mover
INFO: Configuration validated.
```

And you can get information about a migration workflow by running:

```shell
$ copybara info copy.bara.sky
Copybara source mover
...
INFO: Workflow 'default': last_migrated_ref 4dd20b2...
```
