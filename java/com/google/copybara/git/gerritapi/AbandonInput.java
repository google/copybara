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

package com.google.copybara.git.gerritapi;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/**
 * See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#abandon-input
 *
 * <p>NotifyInfo (notify_details) not included for now
 */
public class AbandonInput implements StarlarkValue {

  @Key String message;
  @Key String notify;

  private AbandonInput(@Nullable String message, @Nullable NotifyType notify) {
    this.message = message;
    this.notify = notify == null ? null : notify.toString();
  }

  public static AbandonInput create(@Nullable String message, @Nullable NotifyType notify) {
    return new AbandonInput(message, notify);
  }

  public static AbandonInput createWithoutComment() {
    return new AbandonInput(/*message=*/null, /*notify=*/null);
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("notify", notify)
        .toString();
  }
}
