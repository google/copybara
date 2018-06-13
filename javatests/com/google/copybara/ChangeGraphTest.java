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
import static junit.framework.TestCase.fail;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ChangeGraphTest {

  @Test
  public void testGraphOrder() {
    ChangeGraph<String> graph = ChangeGraph.<String>builder()
        .addChange("bar")
        .addChange("baz")
        .addChange("foo")
        .addParent("foo", "baz")
        .addParent("foo", "bar")
        .build();

    // Order of insertion is important: bar is the first (oldest) change, foo is the latest.
    assertThat(ImmutableList.copyOf(graph.nodes()))
        .isEqualTo(ImmutableList.of("bar", "baz", "foo"));

    // Order of insertion is important. 'baz' is the first parent
    assertThat(ImmutableList.copyOf(graph.predecessors("foo")))
        .isEqualTo(ImmutableList.of("baz", "bar"));

    // No unwanted predecessors
    assertThat(ImmutableList.copyOf(graph.predecessors("bar"))).isEmpty();
    assertThat(ImmutableList.copyOf(graph.predecessors("baz"))).isEmpty();

    graph = ChangeGraph.<String>builder()
        .addChange("before")
        .addAll(graph)
        .addChange("after")
        .addParent("foo", "after")
        .addParent("foo", "before")
        .build();

    // Order of insertion is important: bar is the first (oldest) change, foo is the latest.
    assertThat(ImmutableList.copyOf(graph.nodes()))
        .isEqualTo(ImmutableList.of("before", "bar", "baz", "foo", "after"));

    // Order of insertion is important. 'baz' is the first parent
    assertThat(ImmutableList.copyOf(graph.predecessors("foo")))
        .isEqualTo(ImmutableList.of("baz", "bar", "after", "before"));
  }

  @Test
  public void testSuccessor() {
    ChangeGraph<String> graph = ChangeGraph.<String>builder()
        .addChange("bar")
        .addChange("baz")
        .addChange("foo")
        .addParent("bar", "foo")
        .addParent("baz", "foo")
        .build();

    // We don't care about order here
    assertThat(graph.successors("foo")).containsExactly("baz", "bar");
  }

  @Test
  public void noUnknownEdges() {
    try {
      ChangeGraph.builder().addChange("bar").addParent("foo", "bar");
      fail("Should fail");
    } catch (NullPointerException e) {
      assertThat(e).hasMessageThat().contains("foo not present");
    }

    try {
      ChangeGraph.builder().addChange("foo").addParent("foo", "bar");
      fail("Should fail");
    } catch (NullPointerException e) {
      assertThat(e).hasMessageThat().contains("bar not present");
    }
  }
}
