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

package com.google.copybara.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Change;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.Glob;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DummyOriginTest {
  @Test
  public void testResolveNullReturnsHead() throws Exception {
    DummyOrigin origin = new DummyOrigin()
        .addSimpleChange(/*timestamp*/ 4242);

    Instant timestamp = origin.resolve(null).readTimestamp().toInstant();
    assertThat(timestamp).isNotNull();
    assertThat(timestamp).isEqualTo(Instant.ofEpochSecond(4242));

    origin.addSimpleChange(/*timestamp*/ 42424242);
    timestamp = origin.resolve(null).readTimestamp().toInstant();
    assertThat(timestamp).isNotNull();
    assertThat(timestamp).isEqualTo(Instant.ofEpochSecond(42424242));
  }

  @Test
  public void testCanSpecifyMessage() throws Exception {
    DummyOrigin origin = new DummyOrigin()
        .addSimpleChange(/*timestamp*/ 4242, "foo msg");

    Authoring authoring = new Authoring(new Author("foo", "default.example.com"),
        AuthoringMappingMode.OVERWRITE, ImmutableSet.of());
    Reader<DummyRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);
    ImmutableList<Change<DummyRevision>> changes =
        reader.changes(/*fromRef*/ null, /*toRef*/ origin.resolve("0")).getChanges();
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).getMessage()).isEqualTo("foo msg");
  }

  @Test
  public void exceptionWhenParsingNonNumericReference() throws Exception {
    DummyOrigin origin = new DummyOrigin();

    RepoException thrown = assertThrows(RepoException.class, () -> origin.resolve("foo"));
    assertThat(thrown).hasMessageThat().contains("Not a well-formatted reference");
  }

  @Test
  public void exceptionWhenParsingOutOfRangeReference() throws Exception {
    DummyOrigin origin = new DummyOrigin()
        .addSimpleChange(/*timestamp*/ 9)
        .addSimpleChange(/*timestamp*/ 98);

    CannotResolveRevisionException thrown =
        assertThrows(CannotResolveRevisionException.class, () -> origin.resolve("42"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot find any change for 42. Only 2 changes exist");
  }

  @Test
  public void canSetAuthorOfIndividualChanges() throws Exception {
    DummyOrigin origin = new DummyOrigin()
        .setAuthor(new Author("Dummy Origin", "dummy_origin@google.com"))
        .addSimpleChange(/*timestamp*/ 42)
        .setAuthor(new Author("Wise Origin", "wise_origin@google.com"))
        .addSimpleChange(/*timestamp*/ 999);

    Authoring authoring = new Authoring(new Author("foo", "default.example.com"),
        AuthoringMappingMode.PASS_THRU, ImmutableSet.of());
    ImmutableList<Change<DummyRevision>> changes = origin.newReader(Glob.ALL_FILES, authoring)
        .changes(/*fromRef*/ null, /*toRef*/ origin.resolve("1")).getChanges();

    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).getAuthor())
        .isEqualTo(new Author("Dummy Origin", "dummy_origin@google.com"));
    assertThat(changes.get(1).getAuthor())
        .isEqualTo(new Author("Wise Origin", "wise_origin@google.com"));
  }
}
