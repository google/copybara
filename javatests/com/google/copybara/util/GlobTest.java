package com.google.copybara.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.ConfigValidationException;
import com.google.copybara.RepoException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GlobTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path workdir;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException, RepoException {
    workdir = Files.createTempDirectory("workdir");
    OptionsBuilder options = new OptionsBuilder()
        .setWorkdirToRealTempDir()
        .setConsole(new TestingConsole());
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void emptyIncludeExcludesEverything() throws Exception {
    PathMatcher matcher = createPathMatcher("glob([], exclude = ['foo'])");
    assertThat(matcher.matches(workdir.resolve("foo"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("bar/foo"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("baz"))).isFalse();
  }

  @Test
  public void errorForMissingInclude() throws Exception {
    skylark.evalFails("glob(exclude = ['foo'])", "missing mandatory positional argument 'include'");
    skylark.evalFails("glob()", "missing mandatory positional argument 'include'");
  }

  @Test
  public void errorForNotNamingExclude() throws Exception {
    skylark.evalFails("glob(['bar/*'], ['bar/foo'])", "too many [(]2[)] positional arguments");
  }

  @Test
  public void testSimpleInclude()
      throws IOException, ConfigValidationException, RepoException {
    PathMatcher pathMatcher = createPathMatcher("glob(['foo','bar'])");
    assertThat(pathMatcher.matches(workdir.resolve("foo"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("bar"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("baz"))).isFalse();
  }

  @Test
  public void testSimpleIncludeWithExclusion()
      throws IOException, ConfigValidationException, RepoException {
    PathMatcher pathMatcher = createPathMatcher("glob(['b*','foo'], exclude =['baz'])");
    assertThat(pathMatcher.matches(workdir.resolve("foo"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("bar"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("baz"))).isFalse();
  }

  @Test
  public void testWithDirs()
      throws IOException, ConfigValidationException, RepoException {
    PathMatcher pathMatcher = createPathMatcher(""
        + "glob(\n"
        + "  include = ['**/*.java'], \n"
        + "  exclude = ['**/Generated*.java']\n"
        + ")");
    assertThat(pathMatcher.matches(workdir.resolve("foo/Some.java"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("foo/GeneratedSome.java"))).isFalse();
    // Doesn't match because '**/' matches when there is already one directory segment.
    assertThat(pathMatcher.matches(workdir.resolve("Some.java"))).isFalse();
    assertThat(pathMatcher.matches(workdir.resolve("GeneratedSome.java"))).isFalse();
  }

  private PathMatcher createPathMatcher(final String expression)
      throws ConfigValidationException {
    Glob result = skylark.eval("result", "result=" + expression);
    return result.relativeTo(workdir);
  }

}
