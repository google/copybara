package com.google.copybara.testing;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.config.skylark.CannotResolveLabel;
import com.google.copybara.config.skylark.ConfigFile;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * A Config file implementation that uses a map for storing the internal data structure.
 */
public class MapConfigFile implements ConfigFile {

  private final ImmutableMap<String, byte[]> configFiles;
  private final String current;

  public MapConfigFile(ImmutableMap<String, byte[]> configFiles, String current) {
    this.configFiles = configFiles;
    this.current = current;
  }

  @Override
  public ConfigFile resolve(String label) throws CannotResolveLabel {
    FileSystem fs = FileSystems.getDefault();
    Path currentAsPath = fs.getPath(current);
    Path child = fs.getPath(label);
    if (child.isAbsolute() || !child.equals(child.normalize())) {
      throw new CannotResolveLabel(
          "Only includes of files in the same directory or subdirectories is allowed. No '..' are allowed: "
              + label);
    }
    String resolved = currentAsPath.resolveSibling(child).toString();
    if (!configFiles.containsKey(resolved)) {
      throw new CannotResolveLabel(
          String.format("Cannot find '%s'. '%s' does not exist.", label, resolved));
    }
    return new MapConfigFile(configFiles, resolved);
  }

  @Override
  public String path() {
    return current;
  }

  @Override
  public byte[] content() throws IOException {
    return configFiles.get(current);
  }
}
