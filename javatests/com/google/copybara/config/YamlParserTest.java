package com.google.copybara.config;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Option;
import com.google.copybara.Options;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.yaml.snakeyaml.TypeDescription;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;

@RunWith(JUnit4.class)
public class YamlParserTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private FileSystem fs;
  private YamlParser yamlParser;
  private Options options;

  @Before
  public void setup() {
    yamlParser = new YamlParser(ImmutableList.<TypeDescription>of());
    options = new Options(ImmutableList.<Option>of());
    fs = Jimfs.newFileSystem();
  }

  @Test
  public void testEmptyFile() throws IOException, ConfigValidationException {
    Files.write(fs.getPath("test"), "".getBytes());
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'test' is empty");
    yamlParser.loadConfig(fs.getPath("test"), options);
  }

  @Test
  public void testEmptyFileWithSpaces() throws IOException, ConfigValidationException {
    Files.write(fs.getPath("test"), "  ".getBytes());
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'test' is empty");
    yamlParser.loadConfig(fs.getPath("test"), options);
  }
}
