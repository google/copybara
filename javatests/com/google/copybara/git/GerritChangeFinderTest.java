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

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.hash.Hashing;
import com.google.copybara.authoring.Author;
import com.google.copybara.git.GerritChangeFinder.GerritChange;
import com.google.copybara.util.console.Console;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritChangeFinderTest {

  private static final String WORKFLOW_ID = "workflowId";
  private static final Author COMMITTER = new Author("Copy Bara", "copy@bara.org");

  private static final String CHANGE_ID_1 = "I"
      + Hashing.sha1()
      .newHasher()
      .putString(WORKFLOW_ID, StandardCharsets.UTF_8)
      .putString(COMMITTER.getEmail(), StandardCharsets.UTF_8)
      .putInt(0)
      .hash();
  private static final String CHANGE_ID_2 = "I"
      + Hashing.sha1()
      .newHasher()
      .putString(WORKFLOW_ID, StandardCharsets.UTF_8)
      .putString(COMMITTER.getEmail(), StandardCharsets.UTF_8)
      .putInt(1)
      .hash();

  GerritChangeFinder underTest;
  Console console;

  @Before
  public void setUp() throws Exception {
    underTest = mock(GerritChangeFinder.class);
    console = mock(Console.class);
    when(underTest.find(anyString(), anyString(), any(Author.class), any(Console.class)))
        .thenCallRealMethod();
    when(underTest.computeChangeId(anyString(), anyString(), anyInt())).thenCallRealMethod();
  }

  @Test
  public void testUsesExpectedHash() throws Exception {
    GerritChange expected = new GerritChange(CHANGE_ID_1, "NEW", /*found*/ true);
    when(underTest.query(anyString(), eq(CHANGE_ID_1), any(Console.class))).thenReturn(expected);
    assertThat(underTest.find("url", WORKFLOW_ID, COMMITTER, console)).isEqualTo(expected);
    verify(underTest).query("url", CHANGE_ID_1, console);
  }

  @Test
  public void testUsesNewChange() throws Exception {
    GerritChange first = new GerritChange(CHANGE_ID_1, "MERGED", /*found*/ true);
    GerritChange second = new GerritChange(CHANGE_ID_2, "NEW", /*found*/ true);
    when(underTest.query(anyString(), eq(CHANGE_ID_1), any(Console.class))).thenReturn(first);
    when(underTest.query(anyString(), eq(CHANGE_ID_2), any(Console.class))).thenReturn(second);
    assertThat(underTest.find("url", WORKFLOW_ID, COMMITTER, console)).isEqualTo(second);
    verify(underTest).query("url", CHANGE_ID_1, console);
    verify(underTest).query("url", CHANGE_ID_2, console);
  }

  @Test
  public void testReturnsNewChangeId() throws Exception {
    GerritChange first = new GerritChange(CHANGE_ID_1, "MERGED", /*found*/ true);
    GerritChange second = new GerritChange(CHANGE_ID_2, null, /*found*/ false);
    when(underTest.query(anyString(), eq(CHANGE_ID_1), any(Console.class))).thenReturn(first);
    when(underTest.query(anyString(), eq(CHANGE_ID_2), any(Console.class))).thenReturn(second);

    assertThat(underTest.find("url", WORKFLOW_ID, COMMITTER, console)).isEqualTo(second);
    verify(underTest).query("url", CHANGE_ID_1, console);
    verify(underTest).query("url", CHANGE_ID_2, console);
  }
}