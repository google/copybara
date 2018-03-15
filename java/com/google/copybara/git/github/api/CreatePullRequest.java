/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git.github.api;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.base.Preconditions;

/**
 * An object that represents the creation of a Pull Request
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class CreatePullRequest extends GenericJson {

  @Key
  private String title;
  @Key
  private String body;

  /**
   * Branch to use for the pull request, can be a reference to another
   * github repository if somerepo:branch format is used.
   */
  @Key
  private String head;

  /** Base of the pull request, usually something like 'master' */
  @Key
  private String base;

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getHead() {
    return head;
  }

  public String getBase() {
    return base;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public CreatePullRequest(String title, String body, String head, String base) {
    this.title = Preconditions.checkNotNull(title);
    this.body = Preconditions.checkNotNull(body);
    this.head = Preconditions.checkNotNull(head);
    this.base = Preconditions.checkNotNull(base);
  }
}
