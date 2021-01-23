/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara.transform;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.transform.RegexTemplateTokens.Replacer;
import java.util.concurrent.ExecutionException;

public class ReplaceMapper implements ReversibleFunction<String, String> {

  private final ImmutableList<Replace> replaces;
  private final boolean all;

  public ReplaceMapper(ImmutableList<Replace> replaces, boolean all) {
    this.replaces = Preconditions.checkNotNull(replaces);
    this.all = all;
  }

  private static final ThreadLocal<LoadingCache<Replace, Replacer>> REPLACE_CACHE =
      ThreadLocal.withInitial(
          () -> CacheBuilder.newBuilder().weakKeys().softValues()
              .build(new CacheLoader<Replace, Replacer>() {
                @Override
                public Replacer load(Replace key) {
                  return key.createReplacer();
                }
              }));

  @Override
  public ReversibleFunction<String, String> reverseMapping() throws NonReversibleValidationException {
    ImmutableList.Builder<Replace> builder = ImmutableList.builder();
    for (Replace replace : replaces) {
      builder.add(replace.reverse());
    }
    return new ReplaceMapper(builder.build(), all);
  }

  @Override
  public String apply(String s) {
    LoadingCache<Replace, Replacer> cache = REPLACE_CACHE.get();
    String replacement = s;
    try {
      for (Replace replace : replaces) {
        Replacer replacer = cache.get(replace);
        replacement = replacer.replace(replacement);
        if (all) {
          continue;
        }
        if (replacement.equals(s)) {
          continue;
        }
        return replacement;
      }
    } catch (ExecutionException e) {
      throw new RuntimeException("Shouldn't happen", e);
    }
    return replacement;
  }
}
