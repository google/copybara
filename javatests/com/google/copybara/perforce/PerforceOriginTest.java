/*
 * Copyright (C) 2026 Google Inc.
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

package com.google.copybara.perforce;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.ChangeVisitable.VisitResult;
import com.google.copybara.Options;
import com.google.copybara.Origin.Reader;
import com.google.copybara.Origin.Reader.ChangesResponse;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Change;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.Glob;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.server.IOptionsServer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PerforceOriginTest {

  private static final String STREAM = "//stream/main";

  private final Authoring authoring =
      new Authoring(
          new Author("Copy", "copy@bara.com"), AuthoringMappingMode.PASS_THRU, ImmutableSet.of());

  private IOptionsServer server;
  private Options options;

  @Before
  public void setUp() {
    server = mock(IOptionsServer.class);
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    PerforceOptions perforceOptions = new PerforceOptions(optionsBuilder.general);
    perforceOptions.setServerForTest(new PerforceServer(server));
    options = new Options(ImmutableList.of(optionsBuilder.general, perforceOptions));
  }

  private PerforceOrigin origin() {
    return PerforceOrigin.newPerforceOrigin(options, STREAM, "head");
  }

  private Reader<PerforceRevision> reader() {
    return origin().newReader(Glob.ALL_FILES, authoring);
  }

  @Test
  public void resolveHead() throws Exception {
    stubChangelistsReturn(summary(100));

    assertThat(origin().resolve("head").asString()).isEqualTo("100");
  }

  @Test
  public void resolveExplicitChangelist() throws Exception {
    stubChangelistsReturn(summary(42));

    PerforceRevision rev = origin().resolve("42");

    assertThat(rev.asString()).isEqualTo("42");
    assertThat(rev.contextReference()).isEqualTo(STREAM);
  }

  @Test
  public void resolveUnknownChangelistFails() throws Exception {
    stubChangelistsReturn(); // empty: the changelist does not affect the stream

    assertThrows(ValidationException.class, () -> origin().resolve("42"));
  }

  @Test
  public void resolveNonNumericFails() {
    assertThrows(ValidationException.class, () -> origin().resolve("not-a-number"));
  }

  @Test
  public void resolveEmptyStreamFails() throws Exception {
    stubChangelistsReturn(); // no submitted changelists at all

    assertThrows(ValidationException.class, () -> origin().resolve("head"));
  }

  @Test
  public void changeMapsChangelistMetadata() throws Exception {
    IChangelist cl = changelist(42, "alice", "Fix bug\n", STREAM + "/src/a.txt");
    when(server.getChangelist(42)).thenReturn(cl);
    when(server.getUser("alice")).thenReturn(null); // no email on record -> username fallback

    Change<PerforceRevision> change = reader().change(new PerforceRevision(42));

    assertThat(change.getRevision().asString()).isEqualTo("42");
    assertThat(change.getAuthor().getName()).isEqualTo("alice");
    assertThat(change.getMessage()).isEqualTo("Fix bug\n");
    // The stream prefix is stripped so paths are relative to the checkout root.
    assertThat(change.getChangeFiles()).containsExactly("src/a.txt");
  }

  @Test
  public void changesReturnedOldestFirst() throws Exception {
    // 'p4 changes' lists newest first; the origin must flip this to chronological order.
    stubChangelistsReturn(summary(102), summary(101), summary(100));
    stubDescribe(100, 101, 102);

    ImmutableList<Change<PerforceRevision>> changes =
        reader().changes(/* fromRef= */ null, new PerforceRevision(102)).getChanges();

    assertThat(changes.stream().map(c -> c.getRevision().asString()))
        .containsExactly("100", "101", "102")
        .inOrder();
  }

  @Test
  public void changesEmptyWhenToIsAncestorOfFrom() throws Exception {
    ChangesResponse<PerforceRevision> response =
        reader().changes(new PerforceRevision(5), new PerforceRevision(5));

    assertThat(response.isEmpty()).isTrue();
    assertThat(response.getEmptyReason()).isEqualTo(EmptyReason.TO_IS_ANCESTOR);
  }

  @Test
  public void visitChangesWalksNewestFirstAndTerminates() throws Exception {
    stubChangelistsReturn(summary(102), summary(101), summary(100));
    stubDescribe(100, 101, 102);

    List<String> visited = new ArrayList<>();
    reader()
        .visitChanges(
            new PerforceRevision(102),
            change -> {
              visited.add(change.getRevision().asString());
              return VisitResult.TERMINATE;
            });

    assertThat(visited).containsExactly("102");
  }

  private void stubChangelistsReturn(IChangelistSummary... summaries) throws Exception {
    when(server.getChangelists(anyList(), any())).thenReturn(ImmutableList.copyOf(summaries));
  }

  private void stubDescribe(int... ids) throws Exception {
    // Build each mock fully before handing it to thenReturn: stubbing one mock while another's
    // stubbing is still open trips Mockito's UnfinishedStubbingException.
    for (int id : ids) {
      IChangelist cl = changelist(id, "a", "c" + id, STREAM + "/f" + id);
      when(server.getChangelist(id)).thenReturn(cl);
    }
  }

  private static IChangelistSummary summary(int id) {
    IChangelistSummary summary = mock(IChangelistSummary.class);
    when(summary.getId()).thenReturn(id);
    return summary;
  }

  private static IChangelist changelist(int id, String user, String description, String... files)
      throws Exception {
    IChangelist changelist = mock(IChangelist.class);
    when(changelist.getId()).thenReturn(id);
    when(changelist.getUsername()).thenReturn(user);
    when(changelist.getDescription()).thenReturn(description);
    when(changelist.getDate()).thenReturn(new Date(0L));
    List<IFileSpec> specs = new ArrayList<>();
    for (String depotPath : files) {
      IFileSpec spec = mock(IFileSpec.class);
      when(spec.getDepotPathString()).thenReturn(depotPath);
      specs.add(spec);
    }
    when(changelist.getFiles(anyBoolean())).thenReturn(specs);
    return changelist;
  }
}
