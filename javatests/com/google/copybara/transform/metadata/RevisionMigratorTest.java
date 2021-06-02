/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.transform.metadata;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Change;
import com.google.copybara.ChangeVisitable;
import com.google.copybara.Changes;
import com.google.copybara.DestinationReader;
import com.google.copybara.Metadata;
import com.google.copybara.MigrationInfo;
import com.google.copybara.TransformWork;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyEndpoint;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.re2j.Pattern;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.starlark.java.syntax.Location;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RevisionMigratorTest {

  private DummyOrigin origin;
  private ChangeVisitable<?> destinationReader;
  private ReferenceMigrator referenceMigrator;
  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private Path checkoutDir;
  private Location location;

  @Before
  public void setUp() throws Exception {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    origin = new DummyOrigin();
    destinationReader = new MockReader();
    location = Location.fromFileLineColumn("file", 1, 2);
    referenceMigrator = ReferenceMigrator.create(
        "http://internalReviews.com/${reference}",
        "http://externalreviews.com/view?${reference}",
        Pattern.compile("[0-9]+"),
        Pattern.compile("[0-9a-f]+"),
        ImmutableList.of(),
        location);
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);

  }

  private TransformWork getTransformWork(String msg) {
    return new TransformWork(checkoutDir, new Metadata(msg, new Author("foo", "foo@foo.com"),
        ImmutableSetMultimap.of()),
        Changes.EMPTY, console, new MigrationInfo(DummyOrigin.LABEL_NAME, destinationReader),
        new DummyRevision("1234567890"), c -> origin.getEndpoint(),
        c -> new DummyEndpoint(), () -> DestinationReader.NOT_IMPLEMENTED);
  }

  @Test
  public void testReferenceGetsUpdated() throws Exception {
    String desc = "This is an awesome change, building on http://internalReviews.com/123";
    TransformWork work = getTransformWork(desc);
    referenceMigrator.transform(work);
    assertThat(work.getMessage())
        .isEqualTo("This is an awesome change, building on http://externalreviews.com/view?7b");
  }

  @Test
  public void testUndefinedReferenceGetsNotUpdated() throws Exception {
    String desc = "This is an awesome change, building on http://internalReviews.com/5";
    TransformWork work = getTransformWork(desc);
    referenceMigrator.transform(work);
    assertThat(work.getMessage())
        .isEqualTo("This is an awesome change, building on http://internalReviews.com/5");
  }

  @Test
  public void testOldReferenceGetsNotUpdated() throws Exception {
    String desc = "This is an awesome change, building on http://internalReviews.com/110";
    TransformWork work = getTransformWork(desc);
    referenceMigrator.transform(work);
    assertThat(work.getMessage())
        .isEqualTo("This is an awesome change, building on http://internalReviews.com/110");
  }

  @Test
  public void testMultipleGetUpdated() throws Exception {
    String desc = "This is an awesome change, building on http://internalReviews.com/5005, "
        + "http://internalReviews.com/53, http://internalReviews.com/5, "
        + "http://internalReviews.com/14 and stuff.";
    TransformWork work = getTransformWork(desc);
    referenceMigrator.transform(work);
    assertThat(work.getMessage())
        .isEqualTo("This is an awesome change, building on http://internalReviews.com/5005, "
            + "http://externalreviews.com/view?35, http://internalReviews.com/5, "
            + "http://externalreviews.com/view?e and stuff.");
  }

  @Test
  public void testLegacyLabel() throws Exception {
    referenceMigrator = ReferenceMigrator.create(
        "http://internalReviews.com/${reference}",
        "http://externalreviews.com/view?${reference}",
        Pattern.compile("[0-9]+"),
        Pattern.compile("[0-9a-f]+"),
        ImmutableList.of("LegacyImporter"),
        location);
    String desc = "This is an awesome change, building on http://internalReviews.com/123";
    TransformWork work = getTransformWork(desc);
    referenceMigrator.transform(work);
    assertThat(work.getMessage())
        .isEqualTo("This is an awesome change, building on http://externalreviews.com/view?7b");
  }


  @Test
  public void testReverseRegexEnforced() throws Exception {
    String desc = "This is an awesome change, building on http://internalReviews.com/123";
    referenceMigrator = ReferenceMigrator.create(
        "http://internalReviews.com/${reference}",
        "http://externalreviews.com/view?${reference}",
        Pattern.compile("[0-9]+"),
        Pattern.compile("[xyz]+"),
        ImmutableList.of(),
        location);
    TransformWork work = getTransformWork(desc);
    ValidationException thrown =
        assertThrows(ValidationException.class, () -> referenceMigrator.transform(work));
    assertThat(thrown).hasMessageThat().contains("Reference 7b does not match regex '[xyz]+'");
  }

  @Test
  public void testMigratorParses() throws Exception {
    ReferenceMigrator migrator = skylark.eval("result", ""
        + "result = metadata.map_references(\n"
        + "    before = r'origin/\\${reference}',\n"
        + "    after =  r'destination/\\${reference}',\n"
        + "    regex_groups = {"
        + "        'before_ref': '[0-9a-f]+',\n"
        + "    },\n"
        + ")");
    assertThat(migrator).isNotNull();
  }

  @Test
  public void testBeforeRefRequired() throws Exception {
    skylark.evalFails(""
            + "metadata.map_references(\n"
            + "    before = r'origin/\\${other}',\n"
            + "    after = r'destination/\\${reference}',\n"
            + "    regex_groups = {"
            + "        'after_ref': '[0-9a-f]+',\n"
            + "    },\n"
            + ")",
        "Invalid 'regex_groups' - Should only contain 'before_ref' and optionally 'after_ref'. "
            + "Was: \\[after_ref\\]");
  }

  @Test
  public void testAfterRefParses() throws Exception {
    ReferenceMigrator migrator = skylark.eval("result", ""
        + "result = metadata.map_references(\n"
        + "    before = r'origin/\\${reference}',\n"
        + "    after = r'destination/\\${reference}',\n"
        + "    regex_groups = {"
        + "        'before_ref': '[0-9a-f]+',\n"
        + "        'after_ref': '[0-9a-f]+',\n"
        + "    },\n"
        + ")");
    assertThat(migrator).isNotNull();
  }

  @Test
  public void testAdditionalGroupFails() throws Exception {
    skylark.evalFails(""
            + "metadata.map_references(\n"
            + "    before = r'origin/\\${other}',\n"
            + "    after = r'destination/\\${reference}',\n"
            + "    regex_groups = {"
            + "        'after_ref': '[0-9a-f]+',\n"
            + "        'I_do_not_belong_here': '[0-9a-f]+',\n"
            + "    },\n"
            + ")",
        "Should only contain 'before_ref' and optionally 'after_ref'. "
            + "Was: \\[.*I_do_not_belong_here.*\\].");
  }

  @Test
  public void testOriginPatternNeedsGroup() throws Exception {
    skylark.evalFails(""
            + "metadata.map_references(\n"
            + "    before = r'origin/\\${other}',\n"
            + "    after = r'destination/\\${reference}',\n"
            + "    regex_groups = {"
            + "        'before_ref': '[0-9a-f]+',\n"
            + "    },\n"
            + ")",
        "Interpolation is used but not defined: other");
  }

  @Test
  public void testOriginPatternHasMultipleGroup() throws Exception {
    skylark.evalFails(""
            + "metadata.map_references(\n"
            + "    before = r'origin/\\${reference}${other}',\n"
            + "    after = r'destination/\\${reference}',\n"
            + "    regex_groups = {"
            + "        'before_ref': '[0-9a-f]+',\n"
            + "    },\n"
            + ")",
        "Interpolation is used but not defined.");
  }

  @Test
  public void testDestinationFormatFailsMultipleGroup() throws Exception {
    skylark.evalFails(""
            + "metadata.map_references(\n"
            + "    before = r'origin/\\${reference}',\n"
            + "    after = r'destination/\\${reference}\\${other}',\n"
            + "    regex_groups = {"
            + "        'before_ref': '[0-9a-f]+',\n"
            + "    },\n"
            + ")",
        "Interpolation is used but not defined: other");
  }

  @Test
  public void testDestinationFormatNeedsGroup() throws Exception {
    skylark.evalFails(""
            + "metadata.map_references(\n"
            + "    before = r'origin/\\${reference}\\${other}',\n"
            + "    after = r'destination/\\${other}',\n"
            + "    regex_groups = {"
            + "        'before_ref': '[0-9a-f]+',\n"
            + "    },\n"
            + ")",
        "Interpolation is used but not defined: other");
  }

  @Test
  public void testDestinationFormatBannedToken() throws Exception {
    skylark.evalFails(""
            + "metadata.map_references(\n"
            + "    before = r'origin/\\${reference}',\n"
            + "    after = r'destination/\\${reference}$$1',\n"
            + "    regex_groups = {"
            + "        'before_ref': '[0-9a-f]+',\n"
            + "    },\n"
            + ")",
        " uses the reserved token");
  }

  class MockReader implements ChangeVisitable<DummyRevision> {

    @Override
    public void visitChanges(DummyRevision start, ChangesVisitor visitor)
        throws RepoException {
      int changeNumber = 0;
      Change<DummyRevision> change;
      do {
        changeNumber++;
        ImmutableListMultimap.Builder<String, String> labels = ImmutableListMultimap.builder();
        String destinationId = Integer.toHexString(changeNumber);

        if (changeNumber % 5 != 0) {
          labels.put(origin.getLabelName(), "" + changeNumber);
        }
        if (changeNumber % 11 == 0) {
          labels.put("LegacyImporter", "" + changeNumber);
        }
        change =
            new Change<>(
                new DummyRevision(destinationId),
                new Author("Foo", "Bar"),
                "Lorem Ipsum",
                ZonedDateTime.now(ZoneId.systemDefault()),
                labels.build());

      } while (visitor.visit(change) != VisitResult.TERMINATE);
    }
  }
}
