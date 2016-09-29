package com.google.copybara;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
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
abstract class Info {

  static final Info EMPTY = create();

  static Info create(MigrationReference... migrationReferences) {
    return new AutoValue_Info(Arrays.asList(migrationReferences));
  }

  abstract List<MigrationReference> migrationReferences();

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("migrationReferences", migrationReferences())
        .toString();
  }

  @AutoValue
  static abstract class MigrationReference {

    static MigrationReference create(
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
