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
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Represents the information about a {@link Migration}.
 *
 * <p>A migration can have one or more {@link MigrationReference}s.
 */
@AutoValue
public abstract class Info<O extends Reference> {

  static final Info<? extends Reference> EMPTY = create(ImmutableList.of());

  public static <O extends Reference> Info<O> create(
      Iterable<MigrationReference<O>> migrationReferences) {
    return new AutoValue_Info<O>(ImmutableList.copyOf(migrationReferences));
  }

  abstract Iterable<MigrationReference<O>> migrationReferences();

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("migrationReferences", migrationReferences())
        .toString();
  }

  @AutoValue
  public static abstract class MigrationReference<O extends Reference> {

    public static <O extends Reference> MigrationReference<O> create(
        String label,
        @Nullable O lastMigrated,
        Iterable<Change<O>> availableToMigrate) {
      return new AutoValue_Info_MigrationReference<O>(
          label, lastMigrated, ImmutableList.copyOf(availableToMigrate));
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
    abstract O getLastMigrated();

    /**
     * Returns the last available {@link Reference} to migrate from the origin.
     *
     * <p>There might be more available changes to migrate, but this is the reference of the most
     * recent change available at this moment.
     */
    @Nullable
    O getLastAvailableToMigrate() {
      Optional<O> lastAvailable =
          getAvailableToMigrate()
              .stream()
              .map(Change::getReference)
              .reduce((first, second) -> second);
      return lastAvailable.isPresent() ? lastAvailable.get() : null;
    }

    /**
     * Returns a list of the next available {@link Change}s to migrate from the origin.
     */
    abstract ImmutableList<Change<O>> getAvailableToMigrate();

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("label", getLabel())
          .add("lastMigrated", getLastMigrated())
          .add("availableToMigrate", getAvailableToMigrate())
          .toString();
    }
  }
}
