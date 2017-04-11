import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.copybara.ChangeMessage.parseMessage;
import com.google.common.truth.Truth;
import com.google.copybara.ChangeMessage;
import com.google.copybara.git.GitRepository.GitLogEntry;
  private boolean skipPush;
    destinationFiles = Glob.createGlob(ImmutableList.of("**"));
            + "    skip_push = %s,\n"
            + ")", url, fetch, push, skipPush ? "True" : "False"));
    assertThat(repo().log(ref).run()).hasSize(expected);
    assertThat(parseMessage(lastCommit(branch).getBody())
        .labelsAsMultimap()).containsEntry(DummyOrigin.LABEL_NAME, originRef);
    assertThat(lastCommit(branch).getAuthor()).isEqualTo(author);
  }

  private GitLogEntry lastCommit(String ref) throws RepoException {
    return getOnlyElement(repo().log(ref).withLimit(1).run());
        destinationFirstCommit().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
        destination().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
    process(destinationFirstCommit().newWriter(destinationFiles), ref);
    process(destination().newWriter(destinationFiles), ref);
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded"));
        destination().newWriter(destinationFiles),
        destination().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
        destination().newWriter(destinationFiles),
    destinationFiles = Glob.createGlob(ImmutableList.of("foo/**"));
        destination().newWriter(destinationFiles),
    Glob firstGlob = Glob.createGlob(ImmutableList.of("foo/**", "bar/**"));
    Writer writer1 = destinationFirstCommit().newWriter(firstGlob);
    Writer writer2 = destination().newWriter(Glob.createGlob(ImmutableList.of("baz/**")));
    assertThat(destination().newWriter(firstGlob)
        destinationFirstCommit().newWriter(destinationFiles);
    writer = destination().newWriter(destinationFiles);
    writer = destination().newWriter(destinationFiles);
        destinationFirstCommit().newWriter(destinationFiles),
                .newWriter(destinationFiles)
    Truth.assertThat(checkPreviousImportReferenceMultipleParents()).isEqualTo("b2-origin");
  }
  @Test
  public void previousImportReferenceIsBeforeACommitWithMultipleParents_first_parent()
      throws Exception {
    options.gitDestination.lastRevFirstParent = true;
    Truth.assertThat(checkPreviousImportReferenceMultipleParents()).isEqualTo("b1-origin");
  }

  private String checkPreviousImportReferenceMultipleParents()
      throws IOException, RepoException, ValidationException {
    fetch = "b1";
    push = "b1";
    Files.write(scratchTree.resolve("master" + ".file"), ("master\n\n"
        + DummyOrigin.LABEL_NAME + ": should_not_happen").getBytes(UTF_8));
    scratchRepo.add().files("master" + ".file").run();
    scratchRepo.simpleCommand("commit", "-m", "master\n\n"
        + DummyOrigin.LABEL_NAME + ": should_not_happen");
    scratchRepo.simpleCommand("branch", "b1");
    scratchRepo.simpleCommand("branch", "b2");
    branchChange(scratchTree, scratchRepo, "b2", "b2-1\n\n"
        + DummyOrigin.LABEL_NAME + ": b2-origin");
    branchChange(scratchTree, scratchRepo, "b1", "b1-1\n\n"
        + DummyOrigin.LABEL_NAME + ": b1-origin");
    branchChange(scratchTree, scratchRepo, "b1", "b1-2");
    branchChange(scratchTree, scratchRepo, "b1", "b2-2");
    scratchRepo.simpleCommand("checkout", "b1");
    scratchRepo.simpleCommand("merge", "b2");
    return destination()
        .newWriter(destinationFiles)
  private void branchChange(Path scratchTree, GitRepository scratchRepo, final String branch,
      String msg) throws RepoException, IOException {
    scratchRepo.simpleCommand("checkout", branch);
    Files.write(scratchTree.resolve(branch + ".file"), msg.getBytes(UTF_8));
    scratchRepo.add().files(branch + ".file").run();
    scratchRepo.simpleCommand("commit", "-m", msg);
  }

        destinationFirstCommit().newWriter(destinationFiles),
        destination().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
        destination().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
        destination().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
        destinationFirstCommit().newWriter(destinationFiles),
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded.txt"));
        destination().newWriter(destinationFiles),
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("**/HEAD"));
        destination().newWriter(destinationFiles),
    process(destinationFirstCommit().newWriter(destinationFiles), ref);
    process(destination().newWriter(destinationFiles), ref);
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded"));
        destination().newWriter(destinationFiles), ref, firstCommit);
    process(destinationFirstCommit().newWriter(destinationFiles), ref);
    process(destination().newWriter(destinationFiles), ref);
        destination().newWriter(destinationFiles), ref, firstCommit);
    process(destinationFirstCommit().newWriter(destinationFiles), ref);
    process(destination().newWriter(destinationFiles), ref);
        destination().newWriter(destinationFiles), ref, firstCommit);
        destinationFirstCommit().newWriter(destinationFiles);
    process(destinationFirstCommit().newWriter(destinationFiles), ref);
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of(".gitignore"));
    process(destination().newWriter(destinationFiles), ref);
  @Test
  public void testLocalRepo() throws Exception {
    checkLocalRepo(false);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();
  }

  @Test
  public void testLocalRepoSkipPush() throws Exception {
    skipPush = true;
    GitRepository localRepo = checkLocalRepo(false);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // A simple push without origin is able to update the correct destination reference
    localRepo.simpleCommand("push");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();
  }

  @Test
  public void testLocalRepoSkipPushFlag() throws Exception {
    GitRepository localRepo = checkLocalRepo(true);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // A simple push without origin is able to update the correct destination reference
    localRepo.simpleCommand("push");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();
  }

  @Test
  public void testMultipleRefs() throws Exception {
    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("base"), "base\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("base").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "base");

    GitRevision master = repo().resolveReference("master", /*contextRef=*/null);

    repo().simpleCommand("update-ref","refs/other/master", master.asString());

    checkLocalRepo(true);
  }

  private GitRepository checkLocalRepo(boolean skipPushFlag)
      throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("foo"), "foo\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("foo").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "change");

    options.gitDestination.skipPush = skipPushFlag;
    Path localPath = Files.createTempDirectory("local_repo");

    options.gitDestination.localRepoPath = localPath.toString();
    Writer writer = destination().newWriter(destinationFiles);
    process(writer, new DummyRevision("origin_ref1"));

    //    Path localPath = Files.createTempDirectory("local_repo");
    GitRepository localRepo = GitRepository.initScratchRepo(/*verbose=*/true, localPath,
        System.getenv());

    GitTesting.assertThatCheckout(localRepo, "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();

    Files.write(workdir.resolve("test.txt"), "another content".getBytes());
    process(writer, new DummyRevision("origin_ref2"));

    GitTesting.assertThatCheckout(localRepo, "master")
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();

    ImmutableList<GitLogEntry> entries = localRepo.log("HEAD").run();
    assertThat(entries.get(0).getBody()).isEqualTo(""
        + "test summary\n"
        + "\n"
        + "DummyOrigin-RevId: origin_ref2\n");

    assertThat(entries.get(1).getBody()).isEqualTo(""
        + "test summary\n"
        + "\n"
        + "DummyOrigin-RevId: origin_ref1\n");

    assertThat(entries.get(2).getBody()).isEqualTo("change\n");

    return localRepo;
  }

  @Test
  public void testLabelInSameLabelGroupGroup() throws Exception {
    fetch = "master";
    push = "master";
    Writer writer = destinationFirstCommit().newWriter(destinationFiles);
    Files.write(workdir.resolve("test.txt"), "".getBytes());
    DummyRevision rev = new DummyRevision("first_commit");
    String msg = "This is a message\n"
        + "\n"
        + "That already has a label\n"
        + "THE_LABEL: value\n";
    writer.write(new TransformResult(workdir, rev, rev.getAuthor(), msg, rev), console);

    String body = lastCommit("HEAD").getBody();
    assertThat(body).isEqualTo("This is a message\n"
        + "\n"
        + "That already has a label\n"
        + "THE_LABEL: value\n"
        + "DummyOrigin-RevId: first_commit\n");
    // Double check that we can access it as a label.
    assertThat(ChangeMessage.parseMessage(body).labelsAsMultimap())
        .containsEntry("DummyOrigin-RevId", "first_commit");
  }

        destinationFirstCommit().newWriter(destinationFiles), ref1);
    process(destination().newWriter(destinationFiles), ref2);