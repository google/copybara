package com.google.copybara.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
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
public class PathMatcherBuilderTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path workdir;
  private SkylarkTestExecutor skylark;
  private OptionsBuilder options;

  @Before
  public void setup() throws IOException, RepoException {
    workdir = Files.createTempDirectory("workdir");
    options = new OptionsBuilder().setWorkdirToRealTempDir();
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testEmpty()
      throws IOException, ConfigValidationException, RepoException {
    assertThat(createPathMatcher("glob()").matches(workdir.resolve("foo.txt"))).isFalse();
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
    PathMatcherBuilder result = skylark.eval("result", "result=" + expression);
    return result.relativeTo(workdir);
  }

}
