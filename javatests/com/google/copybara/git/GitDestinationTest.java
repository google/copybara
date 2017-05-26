import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import java.time.ZoneId;
import java.time.ZonedDateTime;
    return new GitRepository(path, /*workTree=*/null, /*verbose=*/true, getGitEnv());
        firstCommitWriter(),
        firstCommitWriter(),
        firstCommitWriter(),
        newWriter(),
        firstCommitWriter(),
    process(firstCommitWriter(), ref);
    process(newWriter(), ref);
        newWriter(),
        newWriter(),
        firstCommitWriter(),
        newWriter(),
        newWriter(),
    Writer writer1 = destinationFirstCommit().newWriter(firstGlob, /*dryRun=*/false);
    Writer writer2 = destination().newWriter(Glob.createGlob(ImmutableList.of("baz/**")),
        /*dryRun=*/false);
    assertThat(destination().newWriter(firstGlob, /*dryRun=*/false)
                   .getDestinationStatus(ref1.getLabelName(), null).getBaseline())
        .isEqualTo(ref1.asString());
    assertThat(writer2.getDestinationStatus(ref2.getLabelName(), null).getBaseline())
        .isEqualTo(ref2.asString());
        firstCommitWriter();
    assertThat(writer.getDestinationStatus(DummyOrigin.LABEL_NAME, null)).isNull();
    writer = newWriter();
    assertThat(writer.getDestinationStatus(DummyOrigin.LABEL_NAME, null).getBaseline())
        .isEqualTo("first_commit");
    writer = newWriter();
    assertThat(writer.getDestinationStatus(DummyOrigin.LABEL_NAME, null).getBaseline())
        .isEqualTo("second_commit");
        firstCommitWriter(),
    assertThat(newWriter().getDestinationStatus(DummyOrigin.LABEL_NAME, null).getBaseline())
    return newWriter().getDestinationStatus(DummyOrigin.LABEL_NAME, null).getBaseline();
        firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414)));
    GitTesting.assertAuthorTimestamp(repo(), "master", timeFromEpoch(1414141414));
        newWriter(),
        new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1515151515)));
    GitTesting.assertAuthorTimestamp(repo(), "master", timeFromEpoch(1515151515));
  }

  static ZonedDateTime timeFromEpoch(long time) {
    return ZonedDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.of("-07:00"));
        firstCommitWriter(),
        firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414)));
        newWriter(),
        new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1414141490)));
        firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414)));
        newWriter(),
        new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1414141490)));
        firstCommitWriter(),
        firstCommitWriter(),
        .withTimestamp(timeFromEpoch(1414141414));
        firstCommitWriter(),
        newWriter(),
        newWriter(),
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    process(newWriter(), ref);
        newWriter(), ref, firstCommit);
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    process(newWriter(), ref);
        newWriter(), ref, firstCommit);
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    process(newWriter(), ref);
        newWriter(), ref, firstCommit);
  @Test
  public void processWithBaselineNotFound() throws Exception {
    fetch = "master";
    push = "master";
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(firstCommitWriter(), ref);

    Files.write(workdir.resolve("test.txt"), "more content".getBytes());
    thrown.expect(RepoException.class);
    thrown.expectMessage("Cannot find baseline 'I_dont_exist' from fetch reference 'master'");
    processWithBaseline(newWriter(), ref, "I_dont_exist");
  }

  @Test
  public void processWithBaselineNotFoundMasterNotFound() throws Exception {
    fetch = "test_test_test";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "more content".getBytes());
    thrown.expect(RepoException.class);
    thrown.expectMessage(
        "Cannot find baseline 'I_dont_exist' and fetch reference 'test_test_test'");
    processWithBaseline(firstCommitWriter(), new DummyRevision("origin_ref"), "I_dont_exist");
  }

        firstCommitWriter();
    String firstCommitHash = repo().parseRef("refs_for_master");
    assertThat(repo().parseRef("refs_for_master~1")).isEqualTo(firstCommitHash);
    process(firstCommitWriter(), ref);
    process(newWriter(), ref);
  @Test
  public void testDryRun() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("foo"), "foo\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("foo").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "change");

    Writer writer = destination().newWriter(destinationFiles, /*dryRun=*/ true);
    process(writer, new DummyRevision("origin_ref1"));

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // Run again without dry run
    writer = destination().newWriter(destinationFiles, /*dryRun=*/ false);
    process(writer, new DummyRevision("origin_ref1"));

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

    repo().simpleCommand("update-ref", "refs/other/master", master.getSha1());
    Writer writer = newWriter();
        getGitEnv());
    Writer writer = firstCommitWriter();
        firstCommitWriter(), ref1);
    process(newWriter(), ref2);

  private Writer newWriter() throws ValidationException {
    return destination().newWriter(destinationFiles, /*dryRun=*/ false);
  }

  private Writer firstCommitWriter() throws ValidationException {
    return destinationFirstCommit().newWriter(destinationFiles, /*dryRun=*/ false);
  }