/*
 * Copyright (C) 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConvertEncodingTest {

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testMoveAndItsReverse() throws Exception {
    Transformation t = skylark.eval("m", ""
        + "m = core.convert_encoding("
        + "   before = 'ISO-8859-1',"
        + "   after = 'UTF-8',"
        + "   paths = glob(['**.txt']),"
        + ")");

    Files.writeString(checkoutDir.resolve("foo.txt"), "ÿÿ", StandardCharsets.ISO_8859_1);

    // Lets check first that without doing anything we would get an error by reading as UTF-8
    assertThrows(MalformedInputException.class, () ->
        Files.readString(checkoutDir.resolve("foo.txt"), StandardCharsets.UTF_8));

    transform(t);

    assertThat(Files.readString(checkoutDir.resolve("foo.txt"), StandardCharsets.UTF_8))
        .isEqualTo("ÿÿ");

    transform(t.reverse());

    assertThat(Files.readString(checkoutDir.resolve("foo.txt"), StandardCharsets.ISO_8859_1))
        .isEqualTo("ÿÿ");
  }

  @CanIgnoreReturnValue
  private TransformationStatus transform(Transformation t) throws Exception {
    return t.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

}
