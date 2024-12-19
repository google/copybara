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

package com.google.copybara.util;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.re2j.Pattern;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;

/** A wrapper around a single String literal passed to the Starlark `glob(...)` function. */
public final class GlobAtom {

  private final AtomType type;
  private final String pattern;

  private GlobAtom(String pattern, AtomType type) {
    this.pattern = Preconditions.checkNotNull(pattern);
    this.type = Preconditions.checkNotNull(type);
  }

  public static GlobAtom of(String pattern, AtomType type) {
    Preconditions.checkArgument(!pattern.isEmpty(), "unexpected empty string in glob list");
    FileUtil.checkNormalizedRelative(pattern);
    if (type == AtomType.JAVA_GLOB) {
      // Try to create a PathMatcher to check if the glob pattern is correct.
      var unused = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    }
    return new GlobAtom(pattern, type);
  }

  public static Iterable<GlobAtom> ofIterable(Iterable<String> patterns, AtomType type) {
    return Iterables.transform(patterns, (pattern) -> GlobAtom.of(pattern, type));
  }

  public PathMatcher matcher(Path root) {
    return type.matcher(root, pattern);
  }

  public String root(boolean allowFiles) {
    return type.root(pattern, allowFiles).getRoot();
  }

  public Root annotatedRoot(boolean allowFiles) {
    return type.root(pattern, allowFiles);
  }

  public String pattern() {
    return pattern;
  }

  @Override
  public String toString() {
    return pattern;
  }

  public AtomType getType() {
    return this.type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GlobAtom)) {
      return false;
    }
    GlobAtom that = (GlobAtom) o;
    return Objects.equals(type, that.type) && Objects.equals(pattern, that.pattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, pattern);
  }

  /** The format that the pattern takes. */
  public static enum AtomType {
    JAVA_GLOB {

      @Override
      PathMatcher matcher(Path root, String pattern) {
        return ReadablePathMatcher.relativeGlob(root, pattern);
      }

      @Override
      Root root(String pattern, boolean allowFiles) {
        String root;
        boolean isSingleFile = true;
        boolean isRecursive = pattern.contains("**");
        ArrayDeque<String> components = new ArrayDeque<>();
        Iterator<String> iterator = Splitter.on('/').split(pattern).iterator();
        while (iterator.hasNext()) {
          String component = iterator.next();
          components.add(unescape(component));
          if (isMeta(component)) {
            isSingleFile = false;
            isRecursive = component.contains("**") || iterator.hasNext();
            break;
          }
        }
        if (!(allowFiles && isSingleFile)) {
          components.removeLast();
        }
        if (components.isEmpty()) {
          root = "";
        } else {
          root = Joiner.on('/').join(components);
        }
        return new Root(isRecursive, root);
      }
    },

    SINGLE_FILE {
      @Override
      PathMatcher matcher(Path root, String pattern) {
        return new PathMatcher() {
          final Path relativePath = getRelativePath(root, pattern);

          @Override
          public boolean matches(Path path) {
            return root.resolve(path).equals(relativePath);
          }

          private Path getRelativePath(Path root, String filePath) {
            FileSystem fs = root.getFileSystem();
            String rootStr = root.normalize().toString();
            String separator = fs.getSeparator();

            if (!rootStr.endsWith(separator)) {
              rootStr += separator;
            }

            return fs.getPath(rootStr + filePath);
          }
        };
      }

      @Override
      Root root(String pattern, boolean allowFiles) {
        int lastSlash = pattern.lastIndexOf('/');
        if (lastSlash == -1) {
          return new Root(false, "");
        }
        return new Root(false, pattern.substring(0, lastSlash));
      }
    };

    private static final Pattern UNESCAPE = Pattern.compile("\\\\(.)");

    private static String unescape(String pathComponent) {
      return UNESCAPE.matcher(pathComponent).replaceAll("$1");
    }

    static boolean isMeta(String pathComponent) {
      int c = 0;
      while (c < pathComponent.length()) {
        switch (pathComponent.charAt(c)) {
          case '*':
          case '{':
          case '[':
          case '?':
            return true;
          case '\\':
            c++;
            break;
          default: // fall out
        }
        c++;
      }
      return false;
    }

    abstract PathMatcher matcher(Path root, String pattern);

    abstract Root root(String pattern, boolean allowFiles);
  }

  static class Root {
    private final boolean isRecursive;
    private final String root;

  Root(boolean isRecursive, String root) {
    this.isRecursive = isRecursive;
    this.root = root;
  }

    public boolean isRecursive() {
      return isRecursive;
    }


    public String getRoot() {
      return root;
    }

    @Override
    public int hashCode() {
      return Objects.hash(root, isRecursive);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Root)) {
        return false;
      }
      Root that = (Root) o;
      return Objects.equals(root, that.root)
          && isRecursive == that.isRecursive;
    }
  }
}
