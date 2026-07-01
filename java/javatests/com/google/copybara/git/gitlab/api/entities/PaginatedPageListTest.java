/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.git.gitlab.api.entities;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.Key;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.copybara.json.GsonParserUtil;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PaginatedPageListTest {
  @Test
  public void testLinkHeaderNextRelParsing_simple() {
    String linkHeader = "<https://copy.bara/api/v1/get_objects/12345?page=2>; rel=\"next\"";

    PaginatedPageList<TestObject> underTest =
        new PaginatedPageList<TestObject>()
            .withPaginatedInfo(
                "https://copy.bara/api/v1/",
                new HttpHeaders().set("link", ImmutableList.of(linkHeader)));

    assertThat(underTest.getNextUrl()).hasValue("get_objects/12345?page=2");
  }

  @Test
  public void testLinkHeaderNextRelParsing_complexHeader() {
    String linkHeader =
        "<https://copy.bara/api/v1/get_objects/12345?page=2>; rel=\"prev\","
            + " <https://copy.bara/api/v1/get_objects/12345?page=4>; rel=\"next\"";

    PaginatedPageList<TestObject> underTest =
        new PaginatedPageList<TestObject>()
            .withPaginatedInfo(
                "https://copy.bara/api/v1/",
                new HttpHeaders().set("link", ImmutableList.of(linkHeader)));

    assertThat(underTest.getNextUrl()).hasValue("get_objects/12345?page=4");
  }

  @Test
  public void testLinkHeaderNextRelParsing_noNextLink() {
    String linkHeader = "<https://copy.bara/api/v1/get_objects/12345?page=2>; rel=\"prev\"";

    PaginatedPageList<TestObject> underTest =
        new PaginatedPageList<TestObject>()
            .withPaginatedInfo(
                "https://copy.bara/api/v1/",
                new HttpHeaders().set("link", ImmutableList.of(linkHeader)));

    assertThat(underTest.getNextUrl()).isEmpty();
  }

  @Test
  public void testLinkHeaderNextRelParsing_badHeaderFormatThrowsException() {
    String linkHeader = "incorrect_format; abcdef,";

    VerifyException e =
        assertThrows(
            VerifyException.class,
            () ->
                new PaginatedPageList<>()
                    .withPaginatedInfo(
                        "https://copy.bara/api/v1/",
                        new HttpHeaders().set("link", ImmutableList.of(linkHeader))));

    assertThat(e)
        .hasMessageThat()
        .contains("'incorrect_format; abcdef' does not match link header regex.");
  }

  @Test
  public void testLinkHeaderNextRelParsing_emptyLinkHeaderString() {
    PaginatedPageList<TestObject> underTest =
        new PaginatedPageList<TestObject>()
            .withPaginatedInfo(
                "https://copy.bara/api/v1/", new HttpHeaders().set("link", ImmutableList.of("")));

    assertThat(underTest.getNextUrl()).isEmpty();
  }

  @Test
  public void testLinkHeaderNextRelParsing_missingHeader() {
    PaginatedPageList<Object> underTest =
        new PaginatedPageList<>().withPaginatedInfo("https://copy.bara/api/v1/", new HttpHeaders());

    assertThat(underTest.getNextUrl()).isEmpty();
  }

  @Test
  public void testGsonParsing() throws Exception {
    MockHttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse()
                    .setContent("[{ \"text\": \"hello\" }, { \"text\": \"world\" }]")
                    .setHeaderNames(ImmutableList.of("link"))
                    .setHeaderValues(
                        ImmutableList.of(
                            "<https://copy.bara/api/v1/get_objects/12345?page=2>;"
                                + " rel=\"next\"")))
            .build();
    HttpResponse response =
        transport
            .createRequestFactory()
            .buildGetRequest(new GenericUrl("https://copy.bara/api/v1/get_objects/12345"))
            .execute();
    Type responseType =
        TypeToken.getParameterized(PaginatedPageList.class, TestObject.class).getType();

    PaginatedPageList<TestObject> underTest =
        GsonParserUtil.parseHttpResponse(response, responseType, false);
    PaginatedPageList<TestObject> annotated =
        underTest.withPaginatedInfo("https://copy.bara/api/v1/", response.getHeaders());

    assertThat(underTest.stream().map(o -> o.text).toList()).containsExactly("hello", "world");
    assertThat(underTest).containsExactlyElementsIn(annotated);
    assertThat(annotated.getNextUrl()).hasValue("get_objects/12345?page=2");
  }

  public static class TestObject implements GitLabApiEntity {
    @Key public String text;

    @SuppressWarnings("unused")
    public TestObject() {}
  }
}
