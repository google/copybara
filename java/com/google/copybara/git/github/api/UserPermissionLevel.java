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

import com.google.api.client.util.Key;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;

/**
 *  A user's permission level at a GitHub repos
 */
public class UserPermissionLevel {

  /** Type of User permission level at a GitHub Repos */
  public enum GitHubUserPermission {
    ADMIN,
    WRITE,
    READ,
    NONE
  }
  @Key private User user;
  @Key private String permission;

  public UserPermissionLevel() {}
  public UserPermissionLevel(User user, String permission) {
    this.user = user;
    this.permission = permission;
  }

  public GitHubUserPermission getPermission() {
    return permission == null
        ? GitHubUserPermission.NONE
        : GitHubUserPermission.valueOf(Ascii.toUpperCase(permission));
  }

  public User getUser() {
    return user;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("permission", getPermission())
        .add("user", user)
        .toString();
  }

}
