/*
 * Copyright (C) 2022 Google LLC
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

package com.google.copybara.remotefile;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.BaseEncoding;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Origin.Reader;
import com.google.copybara.Origin.Reader.ChangesResponse;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.credentials.ConstantCredentialIssuer;
import com.google.copybara.credentials.CredentialModule.UsernamePasswordIssuer;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.http.auth.UsernamePasswordInterceptor;
import com.google.copybara.http.testing.MockHttpTester;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.version.VersionList;
import com.google.copybara.version.VersionResolver;
import com.google.copybara.version.VersionSelector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RemoteArchiveOriginTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock HttpStreamFactory transport;
  @Mock VersionSelector versionSelector;
  @Mock VersionList versionList;
  @Mock VersionResolver versionResolver;
  private Path workdir;
  private RemoteFileOptions remoteFileOptions;
  private Authoring authoring;
  private GeneralOptions generalOptions;

  @Before
  public void setUp() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    remoteFileOptions = new RemoteFileOptions();
    remoteFileOptions.transport = () -> transport;
    generalOptions =
        new GeneralOptions(System.getenv(), FileSystems.getDefault(), new TestingConsole());
    generalOptions.setVersionSelectorUseCliRefForTest(false);
    authoring =
        new Authoring(
            new Author("foo", "default@example.com"),
            AuthoringMappingMode.PASS_THRU,
            ImmutableSet.of());
  }

  public RemoteArchiveOrigin getRemoteArchiveOriginUnderTest(
      String archiveSourceUrl,
      VersionList versionList,
      VersionSelector versionSelector,
      VersionResolver versionResolver,
      RemoteFileType remoteFileType,
      AuthInterceptor auth)
      throws Exception {
    return new RemoteArchiveOrigin(
        Author.parse("Copybara <noreply@copybara.io>"),
        "a message",
        generalOptions,
        remoteFileOptions,
        remoteFileType,
        archiveSourceUrl,
        versionList,
        versionSelector,
        versionResolver,
        auth);
  }

  @Test
  public void checkout_zipFileWithFlatStructure_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.zip"), null))
        .thenReturn(new ByteArrayInputStream(createZipFile("test.txt", "hello world\n")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.zip",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.ZIP,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("test.txt", "hello world\n");
  }

  @Test
  public void checkout_zipFileWithEmptyGlob_unpackingSucceedsWithExcludedFiles() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.zip"), null))
        .thenReturn(new ByteArrayInputStream(createZipFile("test.txt", "hello world\n")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.zip",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.ZIP,
            null);
    Reader<RemoteArchiveRevision> reader =
        underTest.newReader(
            Glob.createGlob(ImmutableList.of("no match"), ImmutableList.of("**")), authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsNoFiles("test.txt", "hello world\n");
  }

  @Test
  public void checkout_zipFileWithMultipleDirs_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.zip"), null))
        .thenReturn(
            new ByteArrayInputStream(
                createZipFile(
                    ImmutableList.of(
                        new AbstractMap.SimpleEntry<>("parent/dir-one/first.txt", "first file\n"),
                        new AbstractMap.SimpleEntry<>(
                            "parent/dir-two/second.txt", "second file\n")))));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.zip",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.ZIP,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("parent/dir-one/first.txt", "first file\n");
    assertThatPath(workdir).containsFile("parent/dir-two/second.txt", "second file\n");
  }

  @Test
  public void checkout_jarContainingFlatDirectory_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.jar"), null))
        .thenReturn(new ByteArrayInputStream(createJarFile("HelloWorld.class", "")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.jar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.JAR,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFiles("HelloWorld.class");
  }

  @Test
  public void checkout_jarContainingNestedDirectory_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.jar"), null))
        .thenReturn(
            new ByteArrayInputStream(
                createJarFile("test/java/com/google/test/test.java", "// hello world")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.jar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.JAR,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("test/java/com/google/test/test.java", "// hello world");
  }

  @Test
  public void checkout_jarContainingFlatFile_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.jar"), null))
        .thenReturn(new ByteArrayInputStream(createJarFile("test.java", "// hello world")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.jar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.JAR,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("test.java", "// hello world");
  }

  @Test
  public void checkout_jarFileContainingMultipleDirs_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.jar"), null))
        .thenReturn(
            new ByteArrayInputStream(
                createZipFile(
                    ImmutableList.of(
                        new AbstractMap.SimpleEntry<>("parent/dir-one/First.class", "first file\n"),
                        new AbstractMap.SimpleEntry<>(
                            "parent/dir-two/Second.class", "second file\n")))));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.jar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.JAR,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("parent/dir-one/First.class", "first file\n");
    assertThatPath(workdir).containsFile("parent/dir-two/Second.class", "second file\n");
  }

  @Test
  public void checkout_tarContainingFlatDirectory_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.tar"), null))
        .thenReturn(new ByteArrayInputStream(createTarFile("test.txt", "hello world")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("test.txt", "hello world");
  }

  @Test
  public void checkout_tarContainingDirectory_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.tar"), null))
        .thenReturn(new ByteArrayInputStream(createTarFile("test/test.txt", "hello world")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("test/test.txt", "hello world");
  }

  @Test
  public void checkout_tarFileWithSymlinks_unpacksSuccessfully() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.tar"), null))
        .thenReturn(
            new ByteArrayInputStream(
                createTarFile(
                    ImmutableList.of(
                        new AbstractMap.SimpleEntry<>("test/test/test.txt", "hello world"),
                        new AbstractMap.SimpleEntry<>("test/testsymlink.txt", "hello world"),
                        new AbstractMap.SimpleEntry<>(
                            "test/symlinktodir/test/test.txt", "hello world")))));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("test/test/test.txt", "hello world");
    assertThatPath(workdir).containsFile("test/testsymlink.txt", "hello world");
    assertThatPath(workdir).containsFile("test/symlinktodir/test/test.txt", "hello world");
  }

  @Test
  public void checkout_tarFileEmptyGlob_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.tar"), null))
        .thenReturn(new ByteArrayInputStream(createTarFile("test/test.txt", "hello world")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);
    Reader<RemoteArchiveRevision> reader =
        underTest.newReader(
            Glob.createGlob(ImmutableList.of("no match"), ImmutableList.of("**")), authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsNoFiles("test.txt", "hello world\n");
  }

  @Test
  public void checkout_asIsArchive_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://foo.zip"), null))
        .thenReturn(new ByteArrayInputStream(createZipFile("test.txt", "hello world")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.zip",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.AS_IS,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFiles("foo.zip");
  }

  @Test
  public void checkout_asIsArchiveWithVersionTargeting_unpackingSucceeds() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of("v.1.0.0"));
    when(versionList.list()).thenReturn(ImmutableSet.of("v1.0.0"));
    when(transport.open(new URL("https://foo.v.1.0.0.zip"), null))
        .thenReturn(new ByteArrayInputStream(createTarFile("test/test.txt", "hello world")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.${VERSION}.zip",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.AS_IS,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFiles("foo.v.1.0.0.zip");
  }

  @Test
  public void checkout_withVersionResolver_resolvesCorrectly() throws Exception {
    generalOptions.setVersionSelectorUseCliRefForTest(true);
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of("v0.1.1"));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    when(transport.open(new URL("https://v0.1.1.tar"), null))
        .thenReturn(new ByteArrayInputStream(createTarFile("test/test.txt", "hello world")));
    when(versionResolver.resolve(eq("v0.1.1"), any()))
        .thenReturn(
            new RemoteArchiveRevision(new RemoteArchiveVersion("https://v0.1.1.tar", "v0.1.1")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://${VERSION}.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);
    Reader<RemoteArchiveRevision> reader =
        underTest.newReader(
            Glob.createGlob(ImmutableList.of("no match"), ImmutableList.of("**")), authoring);

    reader.checkout(underTest.resolve("v0.1.1"), workdir);

    verify(versionResolver, times(1)).resolve(eq("v0.1.1"), any());
    assertThatPath(workdir).containsNoFiles("test.txt", "hello world\n");
  }

  @Test
  public void checkout_withCliRefDisabledOnLastRev_resolvesCorrectly() throws Exception {
    generalOptions.setVersionSelectorUseCliRefForTest(false);
    when(transport.open(new URL("https://v0.1.1.tar"), null))
        .thenReturn(new ByteArrayInputStream(createTarFile("test/test.txt", "hello world")));
    when(versionResolver.resolve(eq("v0.1.1"), any()))
        .thenReturn(
            new RemoteArchiveRevision(new RemoteArchiveVersion("https://v0.1.1.tar", "v0.1.1")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://${VERSION}.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolveLastRev("v0.1.1"), workdir);

    verify(versionResolver).resolve(eq("v0.1.1"), any());
    assertThatPath(workdir).containsFile("test/test.txt", "hello world");
  }

  @Test
  public void checkout_withoutResolverSetResolvingLastRev_resolvesCorrectly() throws Exception {
    generalOptions.setVersionSelectorUseCliRefForTest(false);
    when(transport.open(new URL("https://v0.1.1.tar"), null))
        .thenReturn(new ByteArrayInputStream(createTarFile("test/test.txt", "hello world")));
    when(versionResolver.resolve(eq("v0.1.1"), any()))
        .thenReturn(
            new RemoteArchiveRevision(new RemoteArchiveVersion("https://v0.1.1.tar", "v0.1.1")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://${VERSION}.tar", versionList, versionSelector, null, RemoteFileType.TAR, null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolveLastRev("v0.1.1"), workdir);

    assertThatPath(workdir).containsFile("test/test.txt", "hello world");
  }

  @Test
  public void checkout_withBasicAuth_resolvesCorrectly() throws Exception {
    MockHttpTester http = new MockHttpTester();
    remoteFileOptions.transport =
        () -> new GclientHttpStreamFactory(http.getTransport(), Duration.ofSeconds(20));
    final List<String> authorizationHeader = new ArrayList<>();
    http.mockHttp(
        (method, url, req, resp) -> {
          authorizationHeader.addAll(req.getHeaders().get("authorization"));

          try {
            resp.setStatusCode(200)
                .setContent(createZipFile("test.txt", "hello world\n"))
                .setContentType("application/zip");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.zip",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.ZIP,
            new UsernamePasswordInterceptor(
                UsernamePasswordIssuer.create(
                    ConstantCredentialIssuer.createConstantSecret("username", "testuser"),
                    ConstantCredentialIssuer.createConstantSecret("password", "testpass"))));
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("test.txt", "hello world\n");
    assertThat(authorizationHeader).containsExactly(basicAuth("testuser", "testpass"));
  }

  @Test
  public void checkout_withBasicAuthAndRedirect_resolvesCorrectly() throws Exception {
    MockHttpTester http = new MockHttpTester();
    remoteFileOptions.transport =
        () -> new GclientHttpStreamFactory(http.getTransport(), Duration.ofSeconds(20));
    http.mockHttp(
        (method, url, req, resp) -> {
          if (url.equals("https://foo.zip?access_token=abc%2Fdef%2Fghi")) {
            try {
              resp.setStatusCode(200)
                  .setContent(createZipFile("test.txt", "hello world\n"))
                  .setContentType("application/zip");
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          } else {
            resp.setStatusCode(302)
                .addHeader("Location", "https://foo.zip?access_token=abc%2Fdef%2Fghi");
          }
        });
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of(""));
    when(versionList.list()).thenReturn(ImmutableSet.of());
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.zip",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.ZIP,
            null);
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);

    reader.checkout(underTest.resolve(null), workdir);

    assertThatPath(workdir).containsFile("test.txt", "hello world\n");
  }

  @Test
  public void resolve_withVersionResolution_resolvesCorrectly() throws Exception {
    generalOptions.setVersionSelectorUseCliRefForTest(true);
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of("v0.1.1"));
    when(versionList.list()).thenReturn(ImmutableSet.of("v0.1.1"));
    when(transport.open(new URL("https://v0.1.1.tar"), null))
        .thenReturn(new ByteArrayInputStream(createTarFile("test/test.txt", "hello world")));
    when(versionResolver.resolve(eq("v0.1.1"), any()))
        .thenReturn(
            new RemoteArchiveRevision(new RemoteArchiveVersion("https://v0.1.1.tar", "v0.1.1")));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://${VERSION}.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);

    RemoteArchiveRevision rev = underTest.resolve("v0.1.1");

    assertThat(rev.contextReference()).isEqualTo("v0.1.1");
    assertThat(rev.fixedReference()).isEqualTo("v0.1.1");
  }

  @Test
  public void describe_returnsCorrectDescription() throws Exception {
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);

    ImmutableSetMultimap<String, String> description =
        underTest.describe(
            Glob.createGlob(ImmutableList.of("path/to/file.txt"), ImmutableList.of()));

    assertThat(description).containsEntry("url", "https://foo.tar");
    assertThat(description).containsEntry("type", "remotefiles.origin");
    assertThat(description).containsEntry("root", "path/to");
  }

  @Test
  public void changes_withUpgradeRef_returnsCorrectChanges() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of("v0.1.2"));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);

    ChangesResponse<RemoteArchiveRevision> changesResponse =
        underTest
            .newReader(Glob.ALL_FILES, authoring)
            .changes(
                new RemoteArchiveRevision(new RemoteArchiveVersion("https://foo.tar", "v0.1.1")),
                new RemoteArchiveRevision(new RemoteArchiveVersion("https://foo.tar", "v0.1.2")));

    assertThat(changesResponse.getChanges().get(0).getRevision().fixedReference())
        .isEqualTo("v0.1.2");
  }

  @Test
  public void change_withDownGradeRef_defaultsToBaseline() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of("v0.1.2"));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);

    ChangesResponse<RemoteArchiveRevision> changesResponse =
        underTest
            .newReader(Glob.ALL_FILES, authoring)
            .changes(
                new RemoteArchiveRevision(new RemoteArchiveVersion("https://foo.tar", "v0.1.2")),
                new RemoteArchiveRevision(new RemoteArchiveVersion("https://foo.tar", "v0.1.1")));

    assertThat(changesResponse.getChanges()).isEmpty();
  }

  @Test
  public void change_withNoFixedRefProvided_defaultsToIncomingRef() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of("v0.1.2"));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);

    ChangesResponse<RemoteArchiveRevision> changesResponse =
        underTest
            .newReader(Glob.ALL_FILES, authoring)
            .changes(
                new RemoteArchiveRevision(new RemoteArchiveVersion("", "")),
                new RemoteArchiveRevision(new RemoteArchiveVersion("", "")));

    assertThat(changesResponse.getChanges().get(0).getRevision().fixedReference()).isEqualTo("");
    ((TestingConsole) generalOptions.console())
        .assertThat()
        .onceInLog(
            MessageType.WARNING,
            "Either the baseline ref\\[\\] or the incoming ref\\[\\] form as a fixed ref were not"
                + " known, not performing downgrade validation.");
  }

  @Test
  public void change_withFromRefNull_defaultsToIncomingRef() throws Exception {
    when(versionSelector.select(any(), any(), any())).thenReturn(Optional.of("v0.1.2"));
    RemoteArchiveOrigin underTest =
        getRemoteArchiveOriginUnderTest(
            "https://foo.tar",
            versionList,
            versionSelector,
            versionResolver,
            RemoteFileType.TAR,
            null);

    ChangesResponse<RemoteArchiveRevision> changesResponse =
        underTest
            .newReader(Glob.ALL_FILES, authoring)
            .changes(
                null,
                new RemoteArchiveRevision(new RemoteArchiveVersion("https://foo.tar", "v0.1.2")));

    assertThat(changesResponse.getChanges().get(0).getRevision().fixedReference())
        .isEqualTo("v0.1.2");
    ((TestingConsole) generalOptions.console())
        .assertThat()
        .onceInLog(
            MessageType.WARNING,
            "The baseline revision could not be detected, not performing downgrade"
                + " validation.");
  }

  private byte[] createZipFile(String path, String content) throws Exception {
    return createZipFile(ImmutableList.of(new AbstractMap.SimpleEntry<>(path, content)));
  }

  private byte[] createZipFile(List<Map.Entry<String, String>> entries) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos)) {
      for (Map.Entry<String, String> entryData : entries) {
        ZipArchiveEntry entry = new ZipArchiveEntry(entryData.getKey());
        zos.putArchiveEntry(entry);
        zos.write(entryData.getValue().getBytes(UTF_8));
        zos.closeArchiveEntry();
      }
    }
    return bos.toByteArray();
  }

  private byte[] createTarFile(String path, String content) throws Exception {
    return createTarFile(ImmutableList.of(new AbstractMap.SimpleEntry<>(path, content)));
  }

  private byte[] createTarFile(List<Map.Entry<String, String>> entries) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (TarArchiveOutputStream tos = new TarArchiveOutputStream(bos)) {
      for (Map.Entry<String, String> entryData : entries) {
        TarArchiveEntry entry = new TarArchiveEntry(entryData.getKey());
        byte[] contentBytes = entryData.getValue().getBytes(UTF_8);
        entry.setSize(contentBytes.length);
        tos.putArchiveEntry(entry);
        tos.write(contentBytes);
        tos.closeArchiveEntry();
      }
    }
    return bos.toByteArray();
  }

  private byte[] createJarFile(String path, String content) throws Exception {
    return createJarFile(ImmutableList.of(new AbstractMap.SimpleEntry<>(path, content)));
  }

  private byte[] createJarFile(List<Map.Entry<String, String>> entries) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (JarArchiveOutputStream jos = new JarArchiveOutputStream(bos)) {
      for (Map.Entry<String, String> entryData : entries) {
        ZipArchiveEntry entry = new ZipArchiveEntry(entryData.getKey());
        jos.putArchiveEntry(entry);
        jos.write(entryData.getValue().getBytes(UTF_8));
        jos.closeArchiveEntry();
      }
    }
    return bos.toByteArray();
  }

  private String basicAuth(String username, String password) {
    return String.format(
        "Basic %s",
        BaseEncoding.base64().encode(String.format("%s:%s", username, password).getBytes(UTF_8)));
  }
}
