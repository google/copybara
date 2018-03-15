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

package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * A class that allows to run a list of things in parallel batches.
 */
public class LocalParallelizer {

  private final int threads;
  private final int minSize;
  private final ListeningExecutorService executor;

  public LocalParallelizer(int threads, int minSize) {
    this.threads = threads;
    this.minSize = minSize;
    Preconditions.checkState(threads >= 1, "Threads need to be positive");
    Preconditions.checkState(threads < 1000, "Too many threads (max: 1000)");
    executor = threads == 1
        ? null
        : MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(threads));
  }

  /**
   * Run a list of things in batches, calling {@code func} for each batch.
   */
  public <K, V> List<V> run(Iterable<K> list, TransformFunc<K, V> func)
      throws IOException, ValidationException {
    if (threads == 1 || Iterables.size(list) < minSize) {
      return ImmutableList.of(func.run(list));
    }
    List<ListenableFuture<V>> results = new ArrayList<>(threads);
    List<K> newList = Lists.newArrayList(list);
    for (List<K> batch : Lists.partition(newList, Math.max(1, newList.size() / threads))) {
      results.add(executor.submit(() -> func.run(batch)));
    }
    try {
      return Futures.allAsList(results).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      //TODO We cannot do much here. We might expose InterruptedException all the way up to Main...
      throw new RuntimeException("Interrupted", e);
    } catch (ExecutionException e) {
      Throwables.propagateIfPossible(e.getCause(), IOException.class, ValidationException.class);
      throw new RuntimeException("Unhandled error", e.getCause());

    }
  }

  /** Transforms a collection of K elements into T. */
  public interface TransformFunc<K, T> {

    /**
     * Execute oen batch. The number of elements is undefined.
     */
    T run(Iterable<K> elements) throws IOException, ValidationException;
  }
}
