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

package com.google.copybara.git.github.api;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.base.Preconditions;

/**
 * An object that represents the update of a reference
 * https://developer.github.com/v3/git/refs/#update-a-reference
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class UpdateReferenceRequest extends GenericJson {

  @Key
  private final String sha;
  @Key
  private final boolean force;

  public String getSha1() {
    return sha;
  }

  public boolean getForce() {
    return force;
  }

  public UpdateReferenceRequest(String sha, boolean force) {
    this.sha = Preconditions.checkNotNull(sha);
    this.force = force;
  }
}
