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

using System.Collections.Immutable;
using Copybara.Common;
using Copybara.Exceptions;

namespace Copybara;

/// <summary>
/// A class that allows to run a list of things in parallel batches.
/// </summary>
public class LocalParallelizer
{
    private readonly int _threads;
    private readonly int _minSize;

    public LocalParallelizer(int threads, int minSize)
    {
        _threads = threads;
        _minSize = minSize;
        Preconditions.CheckState(threads >= 1, "Threads need to be positive");
        Preconditions.CheckState(threads < 1000, "Too many threads (max: 1000)");
    }

    /// <summary>
    /// Run a list of things in batches, calling <paramref name="func"/> for each batch.
    /// </summary>
    public IReadOnlyList<V> Run<K, V>(IEnumerable<K> list, TransformFunc<K, V> func)
    {
        var newList = list as IReadOnlyList<K> ?? list.ToList();
        if (_threads == 1 || newList.Count < _minSize)
        {
            return ImmutableArray.Create(func.Run(newList));
        }

        var batches = Partition(newList, Math.Max(1, newList.Count / _threads));
        var tasks = new List<Task<V>>(_threads);
        foreach (var batch in batches)
        {
            var localBatch = batch;
            tasks.Add(Task.Run(() => func.Run(localBatch)));
        }

        try
        {
            Task.WaitAll(tasks.ToArray());
        }
        catch (AggregateException e)
        {
            var inner = e.Flatten().InnerExceptions.FirstOrDefault() ?? e;
            switch (inner)
            {
                case IOException io:
                    throw io;
                case ValidationException ve:
                    throw ve;
                default:
                    throw new InvalidOperationException("Unhandled error", inner);
            }
        }

        return tasks.Select(t => t.Result).ToImmutableArray();
    }

    private static IEnumerable<IReadOnlyList<T>> Partition<T>(IReadOnlyList<T> list, int size)
    {
        for (int i = 0; i < list.Count; i += size)
        {
            yield return list.Skip(i).Take(size).ToList();
        }
    }

    /// <summary>Transforms a collection of K elements into T.</summary>
    public interface TransformFunc<K, T>
    {
        /// <summary>
        /// Execute one batch. The number of elements is undefined.
        /// </summary>
        T Run(IEnumerable<K> elements);
    }
}
