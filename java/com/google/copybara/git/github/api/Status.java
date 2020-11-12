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

import static com.google.common.collect.Iterables.transform;

import com.google.api.client.util.Key;
import com.google.api.client.util.Value;
import com.google.common.collect.ImmutableSet;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * A commit status object
 *
 * <p>https://developer.github.com/v3/repos/statuses
 */
@StarlarkBuiltin(
    name = "github_api_status_obj",
    doc =
        "Information about a commit status as defined in"
            + " https://developer.github.com/v3/repos/statuses. This is a subset of the available"
            + " fields in GitHub")
public class Status implements StarlarkValue {

  @Nullable
  @Key("target_url")
  private String targetUrl;

  @Nullable
  @Key("description")
  private String description;

  @Key("context") private String context;
  @Key private State state;
  @Key("created_at") private String createdAt;
  @Key("updated_at") private String updatedAt;
  @Nullable @Key private User creator;

  public ZonedDateTime getCreatedAt() {
    return ZonedDateTime.parse(createdAt);
  }

  public ZonedDateTime getUpdatedAt() {
    return ZonedDateTime.parse(updatedAt);
  }

  public State getState() {
    return state;
  }

  @StarlarkMethod(
    name = "state",
    doc = "The state of the commit status: success, failure, pending or error",
    structField = true
  )
  public String getStateForSkylark() {
    return state.toString().toLowerCase();
  }

  @StarlarkMethod(
    name = "target_url",
    doc = "Get the target url of the commit status. Can be None.",
    structField = true,
    allowReturnNones = true
  )
  @Nullable
  public String getTargetUrl() {
    return targetUrl;
  }

  @StarlarkMethod(
    name = "description",
    doc = "Description of the commit status. Can be None.",
    structField = true,
    allowReturnNones = true
  )
  @Nullable
  public String getDescription() {
    return description;
  }

  @StarlarkMethod(
      name = "context",
      doc = "Context of the commit status. This is a relatively stable id",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getContext() {
    return context;
  }

  /**
   * Not set in combined status
   */
  @Nullable
  public User getCreator() {
    return creator;
  }

  /**
   * State of the commit status
   */
  public enum State {
    @Value("error") ERROR,
    @Value("failure") FAILURE,
    @Value("pending") PENDING,
    @Value("success") SUCCESS;

    public static final ImmutableSet<String> VALID_VALUES =
        ImmutableSet.copyOf(transform(EnumSet.allOf(State.class), e -> e.toString().toLowerCase()));
  }
}
