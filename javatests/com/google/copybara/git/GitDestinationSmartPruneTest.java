package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.testing.GitTesting.assertThatCheckout;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitDestinationSmartPruneTest {

  private Path workdir;
  private Path repoGitDir;
  private Path baseWorkTree;
  private TestingConsole console;
  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws Exception {
    Path root = Files.createTempDirectory("root");

    console = new TestingConsole();
    options = new OptionsBuilder().setConsole(console).setOutputRootToTmpDir();
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    skylark = new SkylarkTestExecutor(options);

    workdir = Files.createDirectories(root.resolve("workdir"));
    repoGitDir = Files.createDirectories(root.resolve("repoGitDir"));
    repo().git(repoGitDir, "init", "--bare", repoGitDir.toString());
    baseWorkTree = Files.createDirectories(root.resolve("baseWorkTree"));
  }

  private GitRepository repo() {
    Map<String, String> joinedEnv = Maps.newHashMap(options.general.getEnvironment());
    joinedEnv.putAll(getGitEnv().getEnvironment());
    return GitRepository.newBareRepo(
        repoGitDir, new GitEnvironment(joinedEnv), true, DEFAULT_TIMEOUT);
  }

  private GitDestination destination(boolean force) throws ValidationException {
    options.setForce(force);
    return skylark.eval(
        "result",
        String.format(
            "result = git.destination(\n"
                + "    url = 'file://%s',\n"
                + "    fetch = 'master'\n,"
                + "    push = 'master'\n,"
                + ")",
            repoGitDir));
  }

  public void process(GitDestination destination, Glob destinationFiles, DummyRevision originRef)
      throws RepoException, IOException, ValidationException, InsideGitDirException {
    TransformResult result = TransformResults.of(workdir, originRef);

    if (repo().showRef().containsKey("refs/heads/master")) {
      repo().withWorkTree(baseWorkTree).forceCheckout("master");
    }
    repo().withWorkTree(baseWorkTree).forceClean();

    result =
        result.withAffectedFilesForSmartPrune(
            DiffUtil.diffFiles(workdir, baseWorkTree, true, System.getenv()));
    ImmutableList<DestinationEffect> destinationResult =
        destination
            .newWriter(
                new WriterContext(
                    "default",
                    "testuser",
                    false,
                    new DummyRevision("test"),
                    Glob.ALL_FILES.roots()))
            .write(result, destinationFiles, console);
    assertThat(destinationResult).hasSize(1);
    assertThat(destinationResult.get(0).getErrors()).isEmpty();
    assertThat(destinationResult.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(destinationResult.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(destinationResult.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");
  }

  @Test
  public void addFile() throws Exception {
    Files.write(workdir.resolve("new"), "content".getBytes());
    process(
        destination(true),
        Glob.createGlob(ImmutableList.of("**")),
        new DummyRevision("origin_ref"));
    assertThatCheckout(repo(), "HEAD").containsFile("new", "content").containsNoMoreFiles();
  }

  @Test
  public void removeFile() throws Exception {
    GitRepository repo = repo().withWorkTree(workdir);
    Files.write(workdir.resolve("a"), "content_a".getBytes());
    Files.write(workdir.resolve("b"), "content_b".getBytes());
    repo.add().files(ImmutableList.of("a", "b")).run();
    repo.simpleCommand("commit", "-m", "first commit");

    Files.delete(workdir.resolve("a"));

    process(
        destination(false),
        Glob.createGlob(ImmutableList.of("**")),
        new DummyRevision("origin_ref"));
    assertThatCheckout(repo(), "HEAD").containsFile("b", "content_b").containsNoMoreFiles();
  }

  @Test
  public void removeFileNotMatchingGlob() throws Exception {
    GitRepository repo = repo().withWorkTree(workdir);
    Files.write(workdir.resolve("a"), "content_a".getBytes());
    Files.write(workdir.resolve("b"), "content_b".getBytes());
    repo.add().files(ImmutableList.of("a", "b")).run();
    repo.simpleCommand("commit", "-m", "first commit");

    Files.delete(workdir.resolve("a"));
    Files.delete(workdir.resolve("b"));

    process(
        destination(false),
        Glob.createGlob(ImmutableList.of("a")),
        new DummyRevision("origin_ref"));
    assertThatCheckout(repo(), "HEAD").containsFile("b", "content_b").containsNoMoreFiles();
  }

  @Test
  public void addFileNotMatchingGlob() throws Exception {
    Files.write(workdir.resolve("a"), "content_a".getBytes());
    Files.write(workdir.resolve("b"), "content_b".getBytes());

    process(
        destination(true), Glob.createGlob(ImmutableList.of("a")), new DummyRevision("origin_ref"));
    assertThatCheckout(repo(), "HEAD").containsFile("a", "content_a").containsNoMoreFiles();
  }
}
