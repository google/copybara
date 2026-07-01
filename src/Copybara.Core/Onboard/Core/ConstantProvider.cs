/*
 * Copyright (C) 2022 Google Inc.
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

namespace Copybara.Onboard.Core;

/// <summary>
/// A simple provider that can provide a value for a single <see cref="Input{T}"/>.
///
/// <para>The value is nullable on purpose so that we can return "no value".</para>
/// </summary>
public class ConstantProvider<V> : IInputProvider
    where V : class
{
    private readonly Input<V> _input;
    private readonly V? _value;
    private readonly int _priority;

    public ConstantProvider(Input<V> input, V? value)
        : this(input, value, IInputProvider.DefaultPriority)
    {
    }

    public ConstantProvider(Input<V> input, V? value, int priority)
    {
        _input = input;
        _value = value;
        _priority = priority;
    }

    public T? Resolve<T>(Input<T> input, IInputProviderResolver db)
        where T : class
    {
        Preconditions.CheckArgument(
            ReferenceEquals(input, _input),
            "Requested input {0} different of the provided {1}. This shouldn't happen",
            input,
            _input);
        return (T?)(object?)_value;
    }

    public IReadOnlyDictionary<IInput, int> Provides() =>
        ImmutableDictionary.CreateRange(new[] { new KeyValuePair<IInput, int>(_input, _priority) });
}
