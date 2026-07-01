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

using Console = Copybara.Util.Console.Console;

namespace Copybara.Onboard.Core;

/// <summary>
/// Delegates to the proper input provider to resolve <see cref="Input{T}"/>s recursively. Uses an
/// internal set of input names to detect loops. Port of
/// <c>com.google.copybara.onboard.core.InputProviderResolverImpl</c>.
/// </summary>
public sealed class InputProviderResolverImpl : IInputProviderResolver
{
    private readonly IConverter<object> _starlarkConverter;
    private readonly AskMode _askMode;
    private readonly Console _console;
    private readonly ImmutableHashSet<string> _loopDetector;
    private readonly Dictionary<IInput, IInputProvider> _inputProviders;

    /// <exception cref="CannotProvideException"/>
    public static IInputProviderResolver Create(
        IReadOnlyCollection<IInputProvider> providers,
        IConverter<object> starlarkConverter,
        AskMode askMode,
        Console console)
    {
        var map = new Dictionary<IInput, List<IInputProvider>>();
        foreach (IInputProvider provider in providers)
        {
            foreach (IInput provides in provider.Provides().Keys)
            {
                if (!map.TryGetValue(provides, out List<IInputProvider>? list))
                {
                    list = new List<IInputProvider>();
                    map[provides] = list;
                }

                list.Add(provider);
            }
        }

        var providersMap = new Dictionary<IInput, IInputProvider>();
        foreach (KeyValuePair<IInput, List<IInputProvider>> entry in map)
        {
            // Resolve in priority order.
            var provider = new PrioritizedInputProvider(entry.Key, entry.Value);
            providersMap[entry.Key] =
                new CachedInputProvider(
                    entry.Key.InferOnly
                        ? provider
                        // Ask user for input depending on the mode.
                        : new AskInputProvider(provider, askMode, console));
        }

        return new InputProviderResolverImpl(
            providersMap,
            starlarkConverter,
            askMode,
            console,
            ImmutableHashSet<string>.Empty);
    }

    private InputProviderResolverImpl(
        Dictionary<IInput, IInputProvider> inputProviders,
        IConverter<object> starlarkConverter,
        AskMode askMode,
        Console console,
        ImmutableHashSet<string> loopDetector)
    {
        _inputProviders = inputProviders;
        _starlarkConverter = starlarkConverter;
        _askMode = askMode;
        _console = console;
        _loopDetector = loopDetector;
    }

    /// <summary>Resolve the value for an <see cref="Input{T}"/> object.</summary>
    /// <exception cref="System.Threading.ThreadInterruptedException"/>
    /// <exception cref="CannotProvideException"/>
    public T Resolve<T>(Input<T> input)
        where T : class
    {
        if (_loopDetector.Contains(input.Name))
        {
            throw new InvalidOperationException(
                "Loop detected trying to resolver input: "
                    + string.Join(" -> ", _loopDetector)
                    + " -> *"
                    + input.Name);
        }

        if (!_inputProviders.TryGetValue(input, out IInputProvider? inputProvider))
        {
            // Register an on-demand provider for an Input that is not provided by any InputProvider.
            // This means we ask the user for the value (if the mode allows it).
            var newProvider =
                new CachedInputProvider(
                    new AskInputProvider(new ConstantProvider<T>(input, null), _askMode, _console));
            _inputProviders[input] = newProvider;
            return ResolveAndCheck(input, newProvider, this);
        }

        if (!inputProvider.Provides().ContainsKey(input))
        {
            throw new InvalidOperationException(
                $"Something went wrong, InputProvider {inputProvider} doesn't provide {input}");
        }

        return ResolveAndCheck(
            input,
            inputProvider,
            new InputProviderResolverImpl(
                _inputProviders,
                _starlarkConverter,
                _askMode,
                _console,
                _loopDetector.Add(input.Name)));
    }

    private static T ResolveAndCheck<T>(
        Input<T> input, IInputProvider provider, IInputProviderResolver resolver)
        where T : class
    {
        T? result = provider.Resolve(input, resolver);
        if (result == null)
        {
            throw new CannotProvideException(
                $"Cannot find a value for '{input.Description}' ({input.Name})");
        }

        if (!input.Type.IsInstanceOfType(result))
        {
            throw new InvalidOperationException(
                $"Input provider {provider} returned an object of type {result.GetType()}, but"
                    + $" {input} requires an object of type {input.Type}");
        }

        return result;
    }

    public T ParseStarlark<T>(string starlark)
        where T : class
    {
        object convert = _starlarkConverter.Convert(starlark, this);
        if (!typeof(T).IsInstanceOfType(convert))
        {
            throw new CannotConvertException(
                $"Invalid input: {starlark}. Not of type {typeof(T).FullName}");
        }

        return (T)convert;
    }
}
