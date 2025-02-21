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

package com.google.copybara.remotefile;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RemoteFileModuleTest {

  private static final String CAPUTRED_HELLO_WORLD_ZIP_FILE =
      "UEsDBAoAAAAAAGptUVQtOwivDAAAAAwAAAAIABwAdGVzdC50eHRVVAkAA0iXDmJIlw5idXgLAAEE"
          + "Se4JAARTXwEAaGVsbG8gd29ybGQKUEsBAh4DCgAAAAAAam1RVC07CK8MAAAADAAAAAgAGAAAAAAA"
          + "AQAAAKSBAAAAAHRlc3QudHh0VVQFAANIlw5idXgLAAEESe4JAARTXwEAUEsFBgAAAAABAAEATgAA"
          + "AE4AAAAAAA==";

  private static final String CAPTURED_TAR_FILE_WITH_NO_DIRECTORIES =
      "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AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

  private static final String CAPTURED_TAR_GZ_FILE_WITH_NO_DIRECTORIES =
      "H4sIAD68JGQAA+2Xy2vUQBzHpxW1rqKeFHoxqOBBms5MXo11hW137S7uustu6EOFEjfpvpJNTCbutogoehB8nNR/QBFPehMEDxXBq9CTB714EC+KiAjenO276UPR3RY1H0hmMpmZ3y8Jv+/vl4xaj+uqpjvdec9x9CrRSg5oMhBCSRAYIE3DACxIAuaYxvgM9CbieYQQJ0IBMRBDDkmAqTfbkZXwXKI61JV80Sm5edW0LXfFebZTMlVnvOBYnr3S/ZknYebbvwQkMF5JCyPI8xyHQxxkTFIy9TASeyDCMhQFlsO0K/EiDm20swFNh+1uvY25+Ockkbb88vin8bIk/ukFpPEvtN61/z7+2e7MfAZgR4nuEpbUSXNt0Pch8vxa+o/9+o+lQP/XBZ/+Y2m5/mNOoB8sEP9/ErZlUb/AXPyvpv/iMv0XOR4CBrbKocX85/EPNu/ZCtoBSKl5Jp1jhplZGmNgGz0wAG0naUuv2y792pYRRcnO9Bor2jpoxy/lm2bHdwGwN2+ZrGrbhs4qep3EqnlLK1ULM+v30tNuADoX5hiqSzxX1zSV6AcyuVk7h+hpBABpYZ6pE5XOUY9UUtGkelY3Ri3VrFUKpG5VuPGxWsHjeVPQi1L5nEkXe2Ssq6cXcTxGktzDc88Oao2Nrz3aPl2FfLncfvzKYNdBkniY6zU+3Xvz/UEfe/duV9fUq94DL6u3318Md+DRq08+fPv6ePL6jf3p1x/vX/h8bHJq39P0VfZOslM7evPITu/trTPnN0VePK9sedf2ZN/b0R2nf/ezNYvF+b9VOvCT/I8wlPz5n+adIP+vB7+e/5EsMslEXyTbH08Mxti6SojD/l68hXusWH/WOjmUzGVhTi0qaTzhWfKEYFcSnmGPkKSWzKTHMwNnbcUcK8NEyjgcI14Zyye6IyW+VlLkMrHjE7WMG83FJSNT7BcHlT67kklAMVod1spGHUdgPFobcgf4wgCOy2gsetwNIREzOep/cuTP/G+ZHIREea2X7BO/cEqqVU5FGvQnnIKLpruRkCCv9pT+Dfw6FxL5tewvFuiwNpw1k0pBSikpmCrHuLRSgCH6n7eK6SVrfXIbFJcbBdsy1V/gZ/UfRJy//kMYBfXfelDUDcNiapZjaBvtSkBAQEDAOvIDIlPr+AAcAAA=";

  private static final String CAPTURED_TAR_XZ_FILE_WITH_NO_DIRECTORIES =
      "/Td6WFoAAATm1rRGAgAhARYAAAB0L+Wj4BX/AwxdACgYSwSGSBse52ctcOi698TpXuRjrx2AIkY/T5uaJHSSVbIF5x4fWTSxVChuMvU7s0pDUm3f2Gj6UD3L150OmXlLaLScGy1nd6o8L6j9OMcFGR38ZZOaCKAsMiPeTwjFkKz6KSqt3prdwPB1gDfKBOEIZvAegDENxs/h1JqcVZGjhTowSSKPYkY3svqirLljFuOGV5lk7bbiEOoKcGxTVhHkQfP7R93UGbwStvrhOUeFym/oUGzhuWoALZ2o49lXgWwO/SZ7/csGeJ6zObMCqcnVxnMfFv0aOwHyxsJB2xOUieelVARJyi2z2J9cgPavSWLsLXcu/g/XVNk2gYE2eOZ+Ww0wBL5QCbvR0bFFtATpE3Jd1Th6c6NrkuVbKpwH8p1oUSZjacwPakaW4J62ZZUYnHqGo7wenYhBtMNY+eeGtxkg2TMjIyFrIbnxskP60405d1vvC7UwJQQ4zUY+AOHW7o5vxBT2QOYenn1NRMaQwgLd6/eDb5PJX16yAvXfSVB4gxTA4L5FpOtsnW2ExZ5jO+KyPICm8wv2hHQLgA3iFJ/3yh+uoYsSWDtjNZMWft1bRDO7gXE2QxVBFo4cyEno5Cdaf4GY3llw3H+0o/8PCnRMcBTW5VzgZgB1ZDHT5gr1ROtQL4NwPWeOe97khgKij9oWYcQ4o96quO7Z9qcBiZMJ4APSA/WEth/s9morFw9ijG2iLRWlrch3AED+X2sbZW7ci5jyNQRBjZttCnXyZLYwBTEJ3kwenbL0PomKM4qb72yRG8QgsYcb1nv9sftxVMchTXjwVTtPf9669X0RG7WPno4ZLuXbdXsIWlmi/qSD+F7r6apm/u593YnjfhIW0L7Vb6d0vifwp5Oif4wUfhXAEO73ZgH1YrmzSc4wq0RvRCwPpjDi/szg1hJsWjxriFA67msXt2kQH6t9mRsp14MOb3NyNF1PTMCbSZHKP56hiUYykfZaDBGOSkNpG5xizWQ2eILJjSBZieEsyqojVKesXzGDXubuiskmy2ulALgnnQ2IeMinY1v/AACrz/VwHIwFvwABqAaALAAAQbtUZbHEZ/sCAAAAAARZWg==";

  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private Path workdir;
  private Authoring authoring;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock HttpStreamFactory transport;

  @Before
  public void setUp() throws Exception {
    console = new TestingConsole();
    workdir = Files.createTempDirectory("workdir");
    RemoteFileOptions options = new RemoteFileOptions();
    options.transport = () -> transport;
    OptionsBuilder optionsBuilder = new OptionsBuilder().setConsole(console);
    optionsBuilder.remoteFile = options;
    skylark = new SkylarkTestExecutor(optionsBuilder);
    authoring = new Authoring(
                new Author("foo", "default@example.com"),
                AuthoringMappingMode.PASS_THRU,
                ImmutableSet.of());
  }

  @Test
  public void testRemoteArchiveOriginZipFile() throws Exception {
    when(transport.open(argThat((URL url) -> url.toString().equals("https://dirs.zip")), isNull()))
        .thenReturn(
            new ByteArrayInputStream(BaseEncoding.base64().decode(CAPUTRED_HELLO_WORLD_ZIP_FILE)));
    RemoteArchiveOrigin underTest =
        skylark.eval(
            "o",
            "o = remotefiles.origin(unpack_method = 'ZIP', message = 'hello world',"
                + " archive_source = 'https://dirs.zip')");
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);
    RemoteArchiveRevision revision = underTest.resolve(null);
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsFile("test.txt", "hello world\n");
  }

  @Test
  public void testRemoteArchiveOriginTarFile() throws Exception {
    when(transport.open(argThat((URL url) -> url.toString().equals("https://dirs.tar")), isNull()))
        .thenReturn(
            new ByteArrayInputStream(
                BaseEncoding.base64().decode(CAPTURED_TAR_FILE_WITH_NO_DIRECTORIES)));
    RemoteArchiveOrigin underTest =
        skylark.eval(
            "o",
            "o = remotefiles.origin(unpack_method = 'TAR', message = 'hello world',"
                + " archive_source = 'https://dirs.tar')");
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);
    RemoteArchiveRevision revision = underTest.resolve(null);
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsFile("test.txt", "hello world");
  }

  @Test
  public void testRemoteArchiveOriginTarGzFile() throws Exception {
    when(transport.open(
            argThat((URL url) -> url.toString().equals("https://dirs.tar.gz")), isNull()))
        .thenReturn(
            new ByteArrayInputStream(
                BaseEncoding.base64().decode(CAPTURED_TAR_GZ_FILE_WITH_NO_DIRECTORIES)));
    RemoteArchiveOrigin underTest =
        skylark.eval(
            "o",
            "o = remotefiles.origin(unpack_method = 'TAR_GZ', message = 'hello world',"
                + " archive_source = 'https://dirs.tar.gz')");
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);
    RemoteArchiveRevision revision = underTest.resolve(null);
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsFile("test.txt", "hello world");
  }

  @Test
  public void testRemoteArchiveOriginTarXzFile() throws Exception {
    when(transport.open(
            argThat((URL url) -> url.toString().equals("https://dirs.tar.xz")), isNull()))
        .thenReturn(
            new ByteArrayInputStream(
                BaseEncoding.base64().decode(CAPTURED_TAR_XZ_FILE_WITH_NO_DIRECTORIES)));
    RemoteArchiveOrigin underTest =
        skylark.eval(
            "o",
            "o = remotefiles.origin(unpack_method = 'TAR_XZ', message = 'hello world',"
                + " archive_source = 'https://dirs.tar.xz')");
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);
    RemoteArchiveRevision revision = underTest.resolve(null);
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsFile("test.txt", "hello world");
  }

  @Test
  public void testRemoteArchiveOriginAuthenticated() throws Exception {
    ArgumentCaptor<AuthInterceptor> authCaptor = ArgumentCaptor.forClass(AuthInterceptor.class);

    when(transport.open(
            argThat((URL url) -> url.toString().equals("https://dirs.zip")), authCaptor.capture()))
        .thenReturn(
            new ByteArrayInputStream(BaseEncoding.base64().decode(CAPUTRED_HELLO_WORLD_ZIP_FILE)));
    RemoteArchiveOrigin underTest =
        skylark.eval(
            "o",
            "o = remotefiles.origin(unpack_method = 'ZIP', message = 'hello world',"
                + " archive_source = 'https://dirs.zip', auth = http.username_password_auth("
                + " creds = credentials.username_password("
                + "   credentials.static_secret('username', 'testuser'),"
                + "   credentials.static_secret('password', 'testpass'))))");
    Reader<RemoteArchiveRevision> reader = underTest.newReader(Glob.ALL_FILES, authoring);
    RemoteArchiveRevision revision = underTest.resolve(null);
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsFile("test.txt", "hello world\n");

    AuthInterceptor auth = authCaptor.getValue();
    assertThat(auth.describeCredentials().get(0))
        .containsExactly("type", "constant", "name", "username", "open", "false");
    assertThat(auth.describeCredentials().get(1))
        .containsExactly("type", "constant", "name", "password", "open", "false");
  }
}
