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

package com.google.copybara;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Origin.Reference;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents the information about a {@link Migration}.
 *
 * <p>A migration can have one or more {@link MigrationReference}s.
 */
@AutoValue
public abstract class Info {

  static final Info EMPTY = create(ImmutableList.of());

  public static Info create(Iterable<MigrationReference> migrationReferences) {
    return new AutoValue_Info(ImmutableList.copyOf(migrationReferences));
  }

  abstract Iterable<MigrationReference> migrationReferences();

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("migrationReferences", migrationReferences())
        .toString();
  }

  @AutoValue
  public static abstract class MigrationReference {

    public static MigrationReference create(
        String label, @Nullable Reference lastMigrated, @Nullable Reference nextToMigrate) {
      return new AutoValue_Info_MigrationReference(label, lastMigrated, nextToMigrate);
    }

    /**
     * The name of this {@link MigrationReference}.
     *
     * <p>For a {@code Workflow} migration, the label is the string "workflow_" followed by the
     * workflow name.
     *
     * <p>For a {@code Mirror} migration, the name is the string "mirror_" followed by the refspec.
     */
    abstract String getLabel();

    /**
     * Returns the last migrated {@link Reference} from the origin.
     */
    @Nullable
    abstract Reference getLastMigrated();

    /**
     * Returns the next available {@link Reference} to migrate from the origin.
     */
    @Nullable
    abstract Reference getNextToMigrate();

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("label", getLabel())
          .add("lastMigrated", getLastMigrated())
          .add("nextToMigrate", getNextToMigrate())
          .toString();
    }
  }
}
