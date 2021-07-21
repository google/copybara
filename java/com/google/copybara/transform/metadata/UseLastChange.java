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

package com.google.copybara.transform.metadata;

import com.google.common.base.Preconditions;
import com.google.copybara.Change;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import java.io.IOException;
import javax.annotation.Nullable;
import net.starlark.java.syntax.Location;

/**
 * Use metadata (i.e. message/author) from the last change being migrated. Useful when using
 * 'SQUASH' mode but user only cares about the last change.
 */
public class UseLastChange implements Transformation {

  private final boolean useMessage;
  private final boolean useAuthor;
  @Nullable
  private final String defaultMessage;
  private final boolean useMerge;
  private final Location location;

  UseLastChange(boolean useAuthor, boolean useMessage,
      @Nullable String defaultMessage, boolean useMerge,
      Location location) {
    this.useAuthor = useAuthor;
    this.useMessage = useMessage;
    this.defaultMessage = defaultMessage;
    this.useMerge = useMerge;
    this.location = Preconditions.checkNotNull(location);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    Change<?> lastChange = getLastChange(work);
    if (lastChange == null) {
      if (useMessage && defaultMessage != null) {
        work.setMessage(defaultMessage);
      }
      return TransformationStatus.success();
    }
    if (useMessage) {
      work.setMessage(lastChange.getMessage());
    }
    if (useAuthor) {
      work.setAuthor(lastChange.getMappedAuthor());
    }
    return TransformationStatus.success();
  }

  @Nullable
  private Change<?> getLastChange(TransformWork work) {
    for (Change<?> change : work.getChanges().getCurrent()) {
      if (!useMerge && change.isMerge()) {
        continue;
      }
      return change;
    }
    return null;
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return "Use last change metadata";
  }

  @Override
  public Location location() {
    return location;
  }
}
