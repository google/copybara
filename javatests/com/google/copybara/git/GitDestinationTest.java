import com.google.copybara.util.console.Message;
  private void process(Writer<GitRevision> writer, DummyRevision originRef)
  private void processWithBaseline(Writer<GitRevision> writer, DummyRevision originRef,
  private void processWithBaselineAndConfirmation(Writer<GitRevision> writer,
      DummyRevision originRef, String baseline, boolean askForConfirmation)
        .matchesNext(MessageType.PROGRESS, "Git Destination: Fetching: file:.* master")
        .matchesNext(MessageType.WARNING, "Git Destination: 'master' doesn't exist in 'file://.*")
    thrown.expect(ValidationException.class);
    Writer<GitRevision> writer1 = destinationFirstCommit().newWriter(firstGlob, /*dryRun=*/false,
                                                        /*oldWriter=*/null);
    Writer<GitRevision> writer2 = destination().newWriter(
        Glob.createGlob(ImmutableList.of("baz/**")),/*dryRun=*/false, writer1);
    assertThat(destination().newWriter(firstGlob, /*dryRun=*/false, writer2)
    Writer<GitRevision>writer =
    processWithBaseline(newWriter(), ref, firstCommit);
    Writer<GitRevision> writer = firstCommitWriter();
    Writer<GitRevision> writer = destination().newWriter(destinationFiles, /*dryRun=*/ true,
                                            /*oldWriter=*/null);
    writer = destination().newWriter(destinationFiles, /*dryRun=*/ false, writer);
    Writer<GitRevision> writer = newWriter();
    Writer<GitRevision> writer = firstCommitWriter();
    newWriter().visitChanges(null,
  private Writer<GitRevision> newWriter() throws ValidationException {
    return destination().newWriter(destinationFiles, /*dryRun=*/ false, /*oldWriter=*/null);
  private Writer<GitRevision> firstCommitWriter() throws ValidationException {
    return destinationFirstCommit().newWriter(destinationFiles, /*dryRun=*/ false,
                                              /*oldWriter=*/null);
    Writer<GitRevision> writer = firstCommitWriter();
    process(writer, new DummyRevision("1"));
    writer = destination.newWriter(destinationFiles, /*dryRun=*/ false, writer);
    writer.visitChanges(/*start=*/ null, ignore -> VisitResult.CONTINUE);