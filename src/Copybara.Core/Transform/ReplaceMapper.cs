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

using System.Collections.Immutable;
using System.Runtime.CompilerServices;
using Copybara.Common;
using Replacer = Copybara.TemplateToken.RegexTemplateTokens.Replacer;

namespace Copybara.Transform;

public class ReplaceMapper : IReversibleFunction<string, string>
{
    private readonly ImmutableArray<Replace> _replaces;
    private readonly bool _all;

    public ReplaceMapper(ImmutableArray<Replace> replaces, bool all)
    {
        _replaces = replaces;
        _all = all;
    }

    public ReplaceMapper(IReadOnlyList<Replace> replaces, bool all)
        : this(replaces.ToImmutableArray(), all)
    {
    }

    // Cache of the (relatively expensive) Replacer instances keyed by their Replace, mirroring the
    // ThreadLocal weak/soft LoadingCache in the Java original.
    [ThreadStatic]
    private static ConditionalWeakTable<Replace, Replacer>? _replaceCache;

    private static Replacer GetReplacer(Replace replace)
    {
        _replaceCache ??= new ConditionalWeakTable<Replace, Replacer>();
        return _replaceCache.GetValue(replace, r => r.CreateReplacer());
    }

    public IReversibleFunction<string, string> ReverseMapping()
    {
        var builder = ImmutableArray.CreateBuilder<Replace>(_replaces.Length);
        foreach (Replace replace in _replaces)
        {
            builder.Add((Replace)replace.Reverse());
        }
        return new ReplaceMapper(builder.ToImmutable(), _all);
    }

    public string Apply(string s)
    {
        string replacement = s;
        foreach (Replace replace in _replaces)
        {
            Replacer replacer = GetReplacer(replace);
            replacement = replacer.Replace(replacement);
            if (_all)
            {
                continue;
            }
            if (replacement.Equals(s))
            {
                continue;
            }
            return replacement;
        }
        return replacement;
    }
}
