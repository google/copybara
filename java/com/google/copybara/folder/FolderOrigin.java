package com.google.copybara.folder;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Author;
import com.google.copybara.Authoring;
import com.google.copybara.Change;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;

/**
 * Use a folder as the input for the migration.
 */
public class FolderOrigin implements Origin<FolderReference> {

  private static final String LABEL_NAME = "FolderOrigin-RevId";
  private final FileSystem fs;
  private final Author author;
  private final String message;

  FolderOrigin(FileSystem fs, Author author, String message) {
    this.fs = Preconditions.checkNotNull(fs);
    this.author = author;
    this.message = message;
  }

  @Override
  public FolderReference resolve(@Nullable String reference) throws RepoException {
    if (reference == null) {
      throw new RepoException("A path is expected as reference in the command line");
    }
    Path path = fs.getPath(reference);
    if (!Files.exists(path)) {
      throw new RepoException(path + " folder doesn't exist");
    } else if (!Files.isDirectory(path)) {
      throw new RepoException(path + " is not a folder");
    } else if (!Files.isReadable(path) || !Files.isExecutable(path)) {
      throw new RepoException(path + " is not readable/executable");
    }

    return new FolderReference(path, Instant.now(), LABEL_NAME);
  }

  @Override
  public Reader<FolderReference> newReader(Glob originFiles, Authoring authoring)
      throws ValidationException {
    return new Reader<FolderReference>() {
      @Override
      public void checkout(FolderReference ref, Path workdir) throws RepoException {
        try {
          FileUtil.copyFilesRecursively(ref.path, workdir);
        } catch (IOException e) {
          throw new RepoException(String.format("Cannot copy files into the workdir:\n"
                      + "  origin folder: %s\n"
                      + "  workdir: %s",
                  ref.path, workdir), e);
        }
      }

      @Override
      public ImmutableList<Change<FolderReference>> changes(@Nullable FolderReference fromRef,
          FolderReference toRef) throws RepoException {
        // Ignore fromRef since a folder doesn't have history of changes
        return ImmutableList.of(change(toRef));
      }

      @Override
      public Change<FolderReference> change(FolderReference ref) throws RepoException {
        ZonedDateTime time = ZonedDateTime.ofInstant(
            Preconditions.checkNotNull(ref.readTimestamp()), ZoneId.systemDefault());
        return new Change<>(ref, author, message, time, ImmutableMap.of());
      }

      @Override
      public void visitChanges(FolderReference start, ChangesVisitor visitor) throws RepoException {
        visitor.visit(change(start));
      }
    };
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }
}
