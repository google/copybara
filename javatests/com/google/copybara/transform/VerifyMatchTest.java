package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VerifyMatchTest {

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  private void transform(Transformation verifyMatch) throws Exception {
    verifyMatch.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

  @Test
  public void invalidRegex() throws ValidationException {
    skylark.evalFails("core.verify_match(\n"
            + "  regex = '(open parenthesis',"
            + ")",
        "Regex '[(]open parenthesis' is invalid.");
  }

  @Test
  public void testSimpleMatchPasses() throws Exception {
    VerifyMatch transformation = eval("core.verify_match(\n"
        + "  regex = 'foo',\n"
        + ")");

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "foo");
    transform(transformation);
  }

  @Test
  public void testSimpleMatchVerifyNoMatchPasses() throws Exception {
    VerifyMatch transformation = eval("core.verify_match(\n"
        + "  regex = 'bar',\n"
        + "  verify_no_match = True,\n"
        + ")");

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "foo");
    transform(transformation);
  }

  @Test
  public void testSimpleMatchFails() throws Exception {
    VerifyMatch transformation = eval("core.verify_match(\n"
        + "  regex = 'foo',\n"
        + ")");
    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "bar");
    ValidationException e =
        assertThrows(ValidationException.class, () -> transform(transformation));
    assertThat(e)
        .hasMessageThat()
        .contains("1 file(s) failed the validation of Verify match 'foo'");
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, "File 'file1.txt' failed validation 'Verify match 'foo'.*");
  }

  @Test
  public void testSimpleReversalMatchFails() throws Exception {
    VerifyMatch transformation = eval("core.verify_match(\n"
        + "  regex = 'foo',\n"
        + "  also_on_reversal = True,\n"
        + ")");
    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "bar");
    ValidationException e =
        assertThrows(ValidationException.class, () -> transform(transformation.reverse()));
    assertThat(e)
        .hasMessageThat()
        .contains("1 file(s) failed the validation of Verify match 'foo'");
      console.assertThat().onceInLog(MessageType.ERROR,
          "File 'file1.txt' failed validation 'Verify match 'foo'.*");
  }

  @Test
  public void testSimpleNoMatchFails() throws Exception {
    VerifyMatch transformation = eval("core.verify_match(\n"
        + "  regex = 'foo',\n"
        + "  verify_no_match = True,\n"
        + ")");
    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "foo");
    assertThrows(ValidationException.class, () -> transform(transformation));
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, "File 'file1.txt' failed validation 'Verify match 'foo'.*");
  }

  @Test
  public void testWithGlob() throws Exception {
    VerifyMatch transformation = eval("core.verify_match(\n"
        + "  regex = 'foo',\n"
        + "  paths = glob(['**.java']),\n"
        + ")");
    prepareGlobTree();
    transform(transformation);
  }

  @Test
  public void testWithGlobFails() throws Exception {
    VerifyMatch transformation = eval("core.verify_match(\n"
        + "  regex = 'foo',\n"
        + "  paths = glob(['**.txt']),\n"
        + ")");

    prepareGlobTree();
    assertThrows(ValidationException.class, () -> transform(transformation));
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, "File 'file1.txt' failed validation 'Verify match 'foo'.*");
      console.assertThat().onceInLog(MessageType.ERROR,
          "File 'folder/file1.txt' failed validation 'Verify match 'foo'.*");
  }

  @Test
  public void testApacheLicense() throws Exception {
    VerifyMatch transformation = eval("core.verify_match(\n"
        + "  regex = '[\\n] [*] Copyright [(]C[)] 2016 Google Inc[.]',\n"
        + ")");

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "/*\n"
        + " * Copyright (C) 2016 Google Inc.\n"
        + " *\n"
        + " * Licensed under the Apache License, Version 2.0 (the \"License\");\n"
        + " * you may not use this file except in compliance with the License.\n"
        + " * You may obtain a copy of the License at");
    transform(transformation);
  }

  private void prepareGlobTree() throws IOException {
    writeFile(checkoutDir.resolve("file1.txt"), "bar");
    writeFile(checkoutDir.resolve("file1.java"), "foobar");
    Files.createDirectories(checkoutDir.resolve("folder/subfolder"));
    writeFile(checkoutDir.resolve("folder/file1.txt"), "bar");
    writeFile(checkoutDir.resolve("folder/file1.java"), "foo");
    writeFile(checkoutDir.resolve("folder/subfolder/file1.java"), "foo");
  }

  @Test
  public void doNotProcessSymlinks() throws Exception {
    VerifyMatch transformation =
        eval(
            "      core.verify_match("
                + "  regex='abc',"
                + "  paths = glob(['**'], exclude = ['i-exist']),"
                + "  verify_no_match=True"
                + ")");
    writeFile(checkoutDir.resolve("i-exist"), "abc");
    // Invalid symlinks should not cause an exception
    Files.createSymbolicLink(
        checkoutDir.resolve("invalid_symlink"), checkoutDir.resolve("i-dont-exist"));
    // Valid symlinks, even to something that contains matching text, should also be skipped
    Files.createSymbolicLink(checkoutDir.resolve("valid_symlink"), checkoutDir.resolve("i-exist"));

    transform(transformation);
  }

  private Path writeFile(Path path, String text) throws IOException {
    return Files.write(path, text.getBytes(UTF_8));
  }

  private VerifyMatch eval(String verifyMatchConfig) throws ValidationException {
    return skylark.eval("r", "r = " + verifyMatchConfig);
  }

}
