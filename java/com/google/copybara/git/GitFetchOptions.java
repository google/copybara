/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.git;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import java.util.Optional;

/**
 * Options for executing a {@code git fetch} command ({@link GitRepository#fetch(String, Iterable,
 * GitFetchOptions)}).
 *
 * @param prune if remotely non-present refs should be deleted locally ({@code -p})
 * @param force force updates even for non fast-forward updates ({@code -f})
 * @param partialFetch if true, passes {@code --filter=blob:none}
 * @param depth if present, passes {@code --depth}
 * @param tags if true, passes {@code --tags}
 * @param recurseSubmodules if false, passes {@code --no-recurse-submodules}
 */
public record GitFetchOptions(
    boolean prune,
    boolean force,
    boolean partialFetch,
    Optional<Integer> depth,
    boolean tags,
    boolean recurseSubmodules) {

  public static final GitFetchOptions DEFAULT = builder().build();

  public GitFetchOptions {
    requireNonNull(depth, "depth");
  }

  /** Returns a builder for {@link GitFetchOptions} initialized with default values. */
  public static Builder builder() {
    return new AutoBuilder_GitFetchOptions_Builder()
        .setPrune(false)
        .setForce(false)
        .setPartialFetch(false)
        .setDepth(Optional.empty())
        .setTags(false)
        .setRecurseSubmodules(true);
  }

  /** Returns a builder initialized with the values of this {@link GitFetchOptions} instance. */
  public Builder toBuilder() {
    return new AutoBuilder_GitFetchOptions_Builder(this);
  }

  /** Builder for {@link GitFetchOptions}. */
  @AutoBuilder
  public abstract static class Builder {

    public abstract Builder setPrune(boolean prune);

    public abstract Builder setForce(boolean force);

    public abstract Builder setPartialFetch(boolean partialFetch);

    public abstract Builder setDepth(Optional<Integer> depth);

    public abstract Builder setDepth(int depth);

    public abstract Builder setTags(boolean tags);

    public abstract Builder setRecurseSubmodules(boolean recurseSubmodules);

    public abstract GitFetchOptions build();
  }
}
