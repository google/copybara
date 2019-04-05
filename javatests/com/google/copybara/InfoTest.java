/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.util.ExitCode.SUCCESS;

import com.google.common.collect.ImmutableList;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InfoTest {

  private SkylarkTestExecutor skylark;
  private InfoCmd info;
  private String config;
  private TestingConsole console;

  @Before
  public void setUp() {
    OptionsBuilder options = new OptionsBuilder();
    options.setWorkdirToRealTempDir();
    console = new TestingConsole();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
    info = new InfoCmd(new ConfigValidator() {
    },
        migration -> {
        },
        (configPath, sourceRef) -> new ConfigLoader(
            skylark.createModuleSet(),
            skylark.createConfigFile("copy.bara.sky", config)) {
          @Override
          public Config loadForRevision(Console console, Revision revision)
              throws ValidationException {
            try {
              return skylark.loadConfig(configPath);
            } catch (IOException e) {
              throw new AssertionError("Should not fail", e);
            }
          }
        });
  }

  @Test
  public void testInfoAll() throws Exception {
    config = ""
        + "core.workflow("
        + "    name = 'foo',"
        + "    origin = git.origin(url = 'https://example.com/orig'),"
        + "    destination = git.destination(url = 'https://example.com/dest'),"
        + "    authoring = authoring.overwrite('Foo <foo@example.com>'),"
        + ")\n\n"
        + "git.mirror("
        + "    name = 'example',"
        + "    description = 'This is a description',"
        + "    origin = 'https://example.com/mirror1',"
        + "    destination = 'https://example.com/mirror2',"
        + ")\n\n"
        + "";

    ExitCode code = info.run(new CommandEnv(Files.createTempDirectory("test"),
            skylark.createModuleSet().getOptions(),
            ImmutableList.of("copy.bara.sky")));

    assertThat(code).isEqualTo(SUCCESS);

    console.assertThat().onceInLog(MessageType.INFO,
        ".*foo.*git\\.origin \\(https://example.com/orig\\)"
            + ".*git\\.destination \\(https://example.com/dest\\).*SQUASH.*");
    console.assertThat().onceInLog(MessageType.INFO,
        ".*example.*git\\.mirror \\(https://example.com/mirror1\\)"
            + ".*git\\.mirror \\(https://example.com/mirror2\\).*MIRROR.*This is a description.*");

    console.assertThat().onceInLog(MessageType.INFO,
        "To get information about the state of any migration run:(.|\n)*"
            + "copybara info copy.bara.sky \\[workflow_name\\](.|\n)*");
  }
}
