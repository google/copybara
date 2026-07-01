/*
 * Copyright (C) 2024 Google LLC.
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

using Starlark.Annot;
using Starlark.Eval;

using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara;

/// <summary>A Starlark module for randomization-related functions.</summary>
[StarlarkBuiltin("random", Doc = "A module for randomization-related functions.")]
public sealed class StarlarkRandomModule : IStarlarkValue
{
    private static readonly Random Rng = new();

    [StarlarkMethod("sample",
        Doc = "Returns a list of k unique elements randomly sampled from the list.")]
    public StarlarkList SampleStarlarkList(
        [Param(Name = "population", Named = true,
            Doc = "The list to sample from.",
            AllowedTypes = new[] { typeof(StarlarkList) })]
        StarlarkList population,
        [Param(Name = "k", Named = true,
            Doc = "The number of elements to sample from the population list.",
            AllowedTypes = new[] { typeof(StarlarkInt) })]
        StarlarkInt k)
    {
        // A StarlarkList might be immutable. Make a mutable copy that we can shuffle and return.
        var mutableList = new List<object?>(population);
        Shuffle(mutableList);
        int kInt = k.ToInt("k");
        if (kInt < 0 || kInt > mutableList.Count)
        {
            throw new EvalException(string.Format(
                "k is out of bounds. Must be >= 0 and <= {0}. Current value: {1}",
                population.Count, kInt));
        }

        return StarlarkList.ImmutableCopyOf(mutableList.GetRange(0, kInt));
    }

    private static void Shuffle<T>(IList<T> list)
    {
        for (int i = list.Count - 1; i > 0; i--)
        {
            int j = Rng.Next(i + 1);
            (list[i], list[j]) = (list[j], list[i]);
        }
    }
}
