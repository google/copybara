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
/// Shared, non-generic registry of all <see cref="Input{T}"/> instances.
///
/// <para><b>Port note:</b> Java keeps a single <c>static Map&lt;String, Input&lt;?&gt;&gt;</c> on the
/// raw <c>Input</c> type. In C#, a <c>static</c> field declared inside the generic
/// <see cref="Input{T}"/> would be per closed generic type (a separate map for
/// <c>Input&lt;string&gt;</c>, <c>Input&lt;Uri&gt;</c>, …). To preserve the "one global registry"
/// semantics we host the map on this non-generic class.</para>
/// </summary>
internal static class InputRegistry
{
    internal static readonly Dictionary<string, IInput> Inputs = new();
    internal static readonly object Lock = new();

    internal static IReadOnlyDictionary<string, IInput> RegisteredInputs()
    {
        lock (Lock)
        {
            return Inputs.ToImmutableDictionary();
        }
    }
}

/// <summary>
/// An <see cref="Input{T}"/> object represents a named object that can be populated by calling an
/// <see cref="IInputProvider"/> or by asking the user in the console to give a value.
/// </summary>
public sealed class Input<T> : IInput
    where T : class
{
    private readonly T? _defaultValue;
    private readonly IConverter<T> _converter;

    private Input(
        string name,
        string description,
        T? defaultValue,
        IConverter<T> converter,
        bool inferOnly)
    {
        Name = Preconditions.CheckNotNull(name, nameof(name));
        Description = Preconditions.CheckNotNull(description, nameof(description));
        _defaultValue = defaultValue;
        _converter = converter;
        InferOnly = inferOnly;
    }

    /// <summary>
    /// Create an Input object. This factory method ensures that there is only one instance of the
    /// same Input for the same name. equals/hashcode is not implemented on purpose.
    /// </summary>
    public static Input<T> Create(
        string name,
        string description,
        T? defaultValue,
        IConverter<T> converter)
    {
        var result = new Input<T>(name, description, defaultValue, converter, inferOnly: false);
        Register(name, result);
        return result;
    }

    /// <summary>
    /// Create an Input object that can only be inferred, never asked to the user.
    /// </summary>
    public static Input<T> CreateInfer(string name, string description, T? defaultValue)
    {
        var result = new Input<T>(
            name,
            description,
            defaultValue,
            new InferOnlyConverter(name, description),
            inferOnly: true);
        Register(name, result);
        return result;
    }

    private static void Register(string name, Input<T> result)
    {
        lock (InputRegistry.Lock)
        {
            if (InputRegistry.Inputs.ContainsKey(name))
            {
                throw new InvalidOperationException(
                    "Two calls for the same Input name '" + name + "'");
            }

            InputRegistry.Inputs[name] = result;
        }
    }

    /// <summary>Name of the Input object.</summary>
    public string Name { get; }

    /// <summary>Description of the Input object. Can be used to give context to users.</summary>
    public string Description { get; }

    /// <summary>
    /// Default value if any. We keep the default value on purpose, so that it can be represented
    /// when printed to ask the user for a value, as the Converter might not be bidirectional.
    /// </summary>
    public T? DefaultValue => _defaultValue;

    /// <inheritdoc/>
    public object? DefaultValueObject => _defaultValue;

    /// <inheritdoc/>
    public Type Type => typeof(T);

    /// <inheritdoc/>
    public bool InferOnly { get; }

    /// <exception cref="CannotConvertException"/>
    public T Convert(string value, IInputProviderResolver resolver) =>
        _converter.Convert(value, resolver);

    /// <inheritdoc/>
    object IInput.ConvertObject(string value, IInputProviderResolver resolver) =>
        Convert(value, resolver);

    /// <summary>Return all registered inputs.</summary>
    public static IReadOnlyDictionary<string, IInput> RegisteredInputs()
    {
        lock (InputRegistry.Lock)
        {
            return InputRegistry.Inputs.ToImmutableDictionary();
        }
    }

    /// <summary>
    /// Validate that the value is of the Input type and cast it. Used mainly to validate that the
    /// type is the correct one and provide a convenient and safe way of doing the cast required by
    /// the provider interface.
    /// </summary>
    public T AsValue(T value)
    {
        if (value == null)
        {
            throw new InvalidOperationException("Null value for " + this);
        }

        Preconditions.CheckArgument(
            typeof(T).IsAssignableFrom(value.GetType()),
            "Incorrect type for Input {0}: expecting {1} type but got {2}",
            Name,
            typeof(T).FullName,
            value.GetType().FullName);
        return value;
    }

    public override string ToString() =>
        $"Input{{name={Name}, description={Description}, defaultValue={_defaultValue}, type={typeof(T)}}}";

    private sealed class InferOnlyConverter : IConverter<T>
    {
        private readonly string _name;
        private readonly string _description;

        public InferOnlyConverter(string name, string description)
        {
            _name = name;
            _description = description;
        }

        public T Convert(string value, IInputProviderResolver resolver) =>
            throw new CannotConvertException(
                string.Format(
                    "Input of type '{0}' ({1}) could not be inferred. Cannot convert user input: {2}",
                    _name,
                    _description,
                    value));
    }
}
