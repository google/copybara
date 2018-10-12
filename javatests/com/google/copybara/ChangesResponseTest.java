/*
 * Copyright (C) 2018 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Origin.Reader.ChangesResponse;
import com.google.copybara.authoring.Author;
import com.google.copybara.testing.DummyRevision;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ChangesResponseTest {

  private static final Author AUTHOR = new Author("foo", "foo@example.com");

  @Test
  public void testConditionalChanges() {
    ChangesResponse<DummyRevision> response = ChangesResponse
        .forChangesWithMerges(ImmutableList.of(
            fakeChange("e"),
            fakeChange("d", "e"),
            fakeChange("b", "e"),
            fakeChange("c", "d"),
            fakeChange("a", "b", "c")));

    assertThat(changesToStringList(response)).containsExactly("e", "d", "b", "c", "a").inOrder();

    ImmutableMap<String, String> conditionalChanges = conditionalChangesToStringMap(response);
    assertThat(conditionalChanges).containsExactly(
        "c", "a",
        "d", "a");
  }

  /**
   * Case that happens when a change is parent of two first-parent changes. Should chose the oldest
   * one
   */
  @Test
  public void testConditionalChangesTwoRoots() {
    ChangesResponse<DummyRevision> response = ChangesResponse
        .forChangesWithMerges(ImmutableList.of(
            fakeChange("e", "f"), // 'f' not present as change just as parent
            fakeChange("d", "e"),
            fakeChange("c", "f", "d"),
            fakeChange("b", "d"),
            fakeChange("a", "c", "b")));

    assertThat(changesToStringList(response)).isEqualTo(ImmutableList.of("e", "d", "c", "b", "a"));

    ImmutableMap<String, String> conditionalChanges = conditionalChangesToStringMap(response);
    assertThat(conditionalChanges).containsExactly(
        "b", "a",
        "d", "c",
        "e", "c");
  }

  private ImmutableMap<String, String> conditionalChangesToStringMap(
      ChangesResponse<DummyRevision> response) {
    return response.getConditionalChanges().entrySet().stream()
        .collect(ImmutableMap
            .toImmutableMap(e -> e.getKey().getRevision().asString(),
                e -> e.getValue().getRevision().asString()));
  }

  private ImmutableList<String> changesToStringList(ChangesResponse<DummyRevision> response) {
    return response.getChanges().stream()
        .map(e1 -> e1.getRevision().asString())
        .collect(ImmutableList.toImmutableList());
  }

  private Change<DummyRevision> fakeChange(String rev, String... parents) {
    return new Change<>(new DummyRevision(rev), AUTHOR, "change " + rev,
        ZonedDateTime.now(ZoneId.systemDefault()),
        ImmutableListMultimap.of(), /*changeFiles=*/null, parents.length > 1,
        Arrays.stream(parents).map(DummyRevision::new).collect(ImmutableList.toImmutableList()));
  }
}
