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

package com.google.copybara.util;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.StandardSystemProperty;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;

/** Utility methods for Identity, which computes an identity based on workflowName. contextReference,
 * configPath, and workflowIdentityUser and allows us to reuse the destination changes*/
public final class Identity {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private Identity() {}

  public static String computeIdentity(
      String type,
      String ref,
      String workflowName,
      String configPath,
      String workflowIdentityUser) {
    ToStringHelper helper =
        MoreObjects.toStringHelper(type)
            .add("type", "workflow")
            .add("config_path", configPath)
            .add("workflow_name", workflowName)
            .add("context_ref", ref);
    return hashIdentity(helper, workflowIdentityUser);
  }

  public static String hashIdentity(ToStringHelper helper, String workflowIdentityUser) {
    helper.add(
        "user",
        workflowIdentityUser != null
            ? workflowIdentityUser
            : StandardSystemProperty.USER_NAME.value());
    String identity = helper.toString();
    String hash =
        BaseEncoding.base16()
            .encode(Hashing.md5().hashString(identity, StandardCharsets.UTF_8).asBytes());
    // Important to log the source of the hash and the hash for debugging purposes.
    logger.atInfo().log("Computed migration identity hash for %s as %s ", identity, hash);
    return hash;
  }
}
