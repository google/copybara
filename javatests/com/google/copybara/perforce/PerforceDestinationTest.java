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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Options;
import com.google.copybara.WriterContext;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.Glob;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.server.IOptionsServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PerforceDestinationTest {

  private static final String STREAM = "//copybara/main";
  private static final String LABEL = "GitOrigin-RevId";

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

  private PerforceDestination destination(boolean submitAsAuthor) {
    return PerforceDestination.newPerforceDestination(options, STREAM, submitAsAuthor);
  }

  private Writer<PerforceRevision> writer() {
    WriterContext context =
        new WriterContext(
            "workflow",
            /* workflowIdentityUser= */ null,
            /* dryRun= */ false,
            new PerforceRevision(1),
            ImmutableSet.of());
    return destination(/* submitAsAuthor= */ false).newWriter(context);
  }

  @Test
  public void destinationMetadata() {
    PerforceDestination destination = destination(/* submitAsAuthor= */ false);
    assertThat(destination.getType()).isEqualTo("perforce.destination");
    assertThat(destination.getLabelNameWhenOrigin())
        .isEqualTo(PerforceOrigin.PERFORCE_ORIGIN_REV_ID);
    assertThat(destination.describe(Glob.ALL_FILES)).containsEntry("stream", STREAM);
  }

  @Test
  public void writerSupportsHistory() {
    assertThat(writer().supportsHistory()).isTrue();
  }

  @Test
  public void destinationStatusReadsBaselineFromChangelistLabel() throws Exception {
    stubChangelists(summary("Some migrated change\n\n" + LABEL + ": abcdef123"));

    DestinationStatus status = writer().getDestinationStatus(Glob.ALL_FILES, LABEL);

    assertThat(status).isNotNull();
    assertThat(status.getBaseline()).isEqualTo("abcdef123");
  }

  @Test
  public void destinationStatusPicksLabelAmongOtherLabels() throws Exception {
    stubChangelists(
        summary("Title line\n\nChange-Id: I123\nNO_BUG: cleanup\n" + LABEL + ": deadbeef"));

    DestinationStatus status = writer().getDestinationStatus(Glob.ALL_FILES, LABEL);

    assertThat(status.getBaseline()).isEqualTo("deadbeef");
  }

  @Test
  public void destinationStatusUsesMostRecentChangelistWithLabel() throws Exception {
    // getChangelists returns newest first; the first match wins.
    stubChangelists(
        summary("Newest\n\n" + LABEL + ": newsha"),
        summary("Older\n\n" + LABEL + ": oldsha"));

    DestinationStatus status = writer().getDestinationStatus(Glob.ALL_FILES, LABEL);

    assertThat(status.getBaseline()).isEqualTo("newsha");
  }

  @Test
  public void destinationStatusNullWhenNoLabelPresent() throws Exception {
    stubChangelists(summary("A change with no origin label"));

    assertThat(writer().getDestinationStatus(Glob.ALL_FILES, LABEL)).isNull();
  }

  @Test
  public void destinationStatusNullWhenStreamEmpty() throws Exception {
    stubChangelists(); // no submitted changelists at all

    assertThat(writer().getDestinationStatus(Glob.ALL_FILES, LABEL)).isNull();
  }

  private void stubChangelists(IChangelistSummary... summaries) throws Exception {
    when(server.getChangelists(anyList(), any())).thenReturn(ImmutableList.copyOf(summaries));
  }

  private static IChangelistSummary summary(String description) {
    IChangelistSummary summary = mock(IChangelistSummary.class);
    when(summary.getDescription()).thenReturn(description);
    return summary;
  }
}
