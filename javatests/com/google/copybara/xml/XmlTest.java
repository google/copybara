/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.xml;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Throwables;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XmlTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void stringMatch() throws ValidationException, IOException {
    assertThat(skylark.<String>eval("x", ""
        + "x = xml.xpath(content = '"
        + "<test>"
        + "  <some>"
        + "    <value>the value</value>"
        + "  </some>"
        + "</test>', expression = '/test/some/value', type = 'STRING')"))
        .isEqualTo("the value");
  }

  @Test
  public void booleanMatch() throws ValidationException, IOException {
    assertThat(skylark.<Boolean>eval("x", ""
        + "x = xml.xpath(content = '"
        + "<test>"
        + "  <some>"
        + "    <value>True</value>"
        + "  </some>"
        + "</test>', expression = '/test/some/value', type = 'BOOLEAN')"))
        .isTrue();
  }

  @Test
  public void doubleMatch() throws ValidationException, IOException {
    assertThat(skylark.<Boolean>eval("x", ""
        + "x = xml.xpath(content = '"
        + "<test>"
        + "  <some>"
        + "    <value>42</value>"
        + "  </some>"
        + "</test>', expression = '/test/some/value', type = 'NUMBER') == 42"))
        .isTrue();
  }

  @Test
  public void xpath_rejectsDoctypeWithSystemEntity() throws Exception {
    Path sampleFile = tempFolder.newFile("sample.txt").toPath();
    Files.writeString(sampleFile, "SAMPLE_FILE_CONTENT", UTF_8);
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE r [ <!ENTITY x SYSTEM \""
            + sampleFile.toUri()
            + "\"> ]>\n"
            + "<r>&x;</r>";

    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () ->
                skylark.eval(
                    "x",
                    String.format(
                        "x = xml.xpath(content = \"\"\"%s\"\"\", expression = '/r/text()', type ="
                            + " 'STRING')",
                        xml)));

    assertThat(thrown).hasMessageThat().contains("Error parsing XML");
    assertThat(Throwables.getRootCause(thrown)).hasMessageThat().contains("disallow-doctype-decl");
    assertThat(thrown).hasMessageThat().doesNotContain("SAMPLE_FILE_CONTENT");
  }

  @Test
  public void xpath_rejectsDoctypeWithRemoteDtdAndParameterEntity() throws Exception {
    AtomicInteger connectCount = new AtomicInteger();
    try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
      int port = server.getLocalPort();
      Thread acceptor =
          new Thread(
              () -> {
                while (!server.isClosed()) {
                  try (Socket ignored = server.accept()) {
                    connectCount.incrementAndGet();
                  } catch (IOException expectedOnClose) {
                    break;
                  }
                }
              });
      acceptor.setDaemon(true);
      acceptor.start();

      String externalDtdXml =
          String.format("<!DOCTYPE test SYSTEM \"http://127.0.0.1:%d/external.dtd\"><test/>", port);
      String parameterEntityXml =
          String.format(
              "<!DOCTYPE test [ <!ENTITY %% pe SYSTEM \"http://127.0.0.1:%d/pe.dtd\"> %%pe;"
                  + " ]><test/>",
              port);

      ValidationException dtdThrown =
          assertThrows(
              ValidationException.class,
              () ->
                  skylark.eval(
                      "x",
                      String.format(
                          "x = xml.xpath(content = '%s', expression = '/test', type = 'STRING')",
                          externalDtdXml)));
      ValidationException peThrown =
          assertThrows(
              ValidationException.class,
              () ->
                  skylark.eval(
                      "x",
                      String.format(
                          "x = xml.xpath(content = '%s', expression = '/test', type = 'STRING')",
                          parameterEntityXml)));

      assertThat(dtdThrown).hasMessageThat().contains("Error parsing XML");
      assertThat(peThrown).hasMessageThat().contains("Error parsing XML");
      assertThat(connectCount.get()).isEqualTo(0);
    }
  }
  
  @Test
  public void xpath_rejectsDoctypeWithNestedInternalEntities() {
    String xml =
        "<?xml version=\"1.0\"?><!DOCTYPE doc [ <!ENTITY item \"val\"> <!ENTITY group1"
            + " \"&item;&item;&item;&item;&item;&item;&item;&item;&item;&item;\"> <!ENTITY group2"
            + " \"&group1;&group1;&group1;&group1;&group1;&group1;&group1;&group1;&group1;&group1;\">"
            + "]><doc>&group2;</doc>";

    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () ->
                skylark.eval(
                    "x",
                    String.format(
                        "x = xml.xpath(content = '%s', expression = '/doc', type = 'STRING')",
                        xml)));

    assertThat(thrown).hasMessageThat().contains("Error parsing XML");
  }

  @Test
  public void xpath_doesNotExpandXIncludeElements() throws Exception {
    Path includedFile = tempFolder.newFile("included.txt").toPath();
    Files.writeString(includedFile, "INCLUDED_FILE_CONTENT", UTF_8);
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<test xmlns:xi=\"http://www.w3.org/2001/XInclude\">"
            + "<value><xi:include href=\""
            + includedFile.toUri()
            + "\" parse=\"text\"/></value>"
            + "</test>";

    String result =
        skylark.eval(
            "x",
            String.format(
                "x = xml.xpath(content = '%s', expression = '/test/value', type = 'STRING')", xml));

    assertThat(result).isEmpty();
  }

  @Test
  public void xpath_preservesMavenPomNamespacesCdataAndPredefinedEntities() throws Exception {
    String pomXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!-- Comment before root --><project"
            + " xmlns=\"http://maven.apache.org/POM/4.0.0\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0"
            + " https://maven.apache.org/xsd/maven-4.0.0.xsd\">  <parent>   "
            + " <groupId>com.example</groupId>    <version>&#x31;.&#48;.0</version>  </parent> "
            + " <artifactId>demo-lib</artifactId>  <description>A &amp; B &lt;tag&gt;"
            + " &quot;quoted&quot; <![CDATA[<raw>]]></description></project>";

    String groupId =
        skylark.eval(
            "g",
            String.format(
                "g = xml.xpath(content = '%s', expression = 'project/parent/groupId', type ="
                    + " 'STRING')",
                pomXml));
    String version =
        skylark.eval(
            "v",
            String.format(
                "v = xml.xpath(content = '%s', expression = 'project/parent/version', type ="
                    + " 'STRING')",
                pomXml));
    Boolean hasArtifact =
        skylark.eval(
            "b",
            String.format(
                "b = xml.xpath(content = '%s', expression = 'boolean(project/artifactId)', type ="
                    + " 'BOOLEAN')",
                pomXml));
    String description =
        skylark.eval(
            "d",
            String.format(
                "d = xml.xpath(content = '%s', expression = 'project/description', type ="
                    + " 'STRING')",
                pomXml));

    assertThat(groupId).isEqualTo("com.example");
    assertThat(version).isEqualTo("1.0.0");
    assertThat(hasArtifact).isTrue();
    assertThat(description).isEqualTo("A & B <tag> \"quoted\" <raw>");
  }
}
