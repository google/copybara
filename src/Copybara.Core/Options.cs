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

using System.Collections.Immutable;

namespace Copybara;

/// <summary>
/// A class that groups all the options used in the program.
/// </summary>
public class Options
{
    private readonly ImmutableDictionary<Type, IOption> _config;

    public Options(IEnumerable<IOption> options)
    {
        var builder = ImmutableDictionary.CreateBuilder<Type, IOption>();
        foreach (var option in options)
        {
            builder.Add(option.GetType(), option);
        }

        _config = builder.ToImmutable();
    }

    /// <summary>
    /// Get an option for a given type.
    /// </summary>
    /// <exception cref="InvalidOperationException">If the configuration cannot be found.</exception>
    public T Get<T>() where T : IOption
    {
        return (T)Get(typeof(T));
    }

    /// <summary>
    /// Get an option for a given type.
    /// </summary>
    /// <exception cref="InvalidOperationException">If the configuration cannot be found.</exception>
    public IOption Get(Type optionType)
    {
        if (_config.TryGetValue(optionType, out var option))
        {
            return option;
        }

        // If we didn't find the exact type, look for a subclass.
        foreach (var entry in _config)
        {
            if (optionType.IsAssignableFrom(entry.Key))
            {
                return entry.Value;
            }
        }

        throw new InvalidOperationException("No option type found for " + optionType);
    }

    public IReadOnlyCollection<IOption> GetAll() => _config.Values.ToList();
}
