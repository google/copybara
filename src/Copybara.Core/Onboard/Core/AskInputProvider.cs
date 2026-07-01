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

using Console = Copybara.Util.Console.Console;

namespace Copybara.Onboard.Core;

/// <summary>
/// The different modes to ask the user for input given a result from an <see cref="IInputProvider"/>.
/// Port of <c>com.google.copybara.onboard.core.AskInputProvider.Mode</c>.
/// </summary>
public enum AskMode
{
    /// <summary>Fail if it requires asking the user for input.</summary>
    Fail,

    /// <summary>
    /// Use the delegate but confirm the selection with the user by using the result as the default.
    /// </summary>
    Confirm,

    /// <summary>Only ask the user for input if the delegate cannot find a value for an input.</summary>
    Auto,
}

/// <summary>
/// An <see cref="IInputProvider"/> that first tries to use a delegate <see cref="IInputProvider"/>,
/// then uses the default, and then maybe asks the user for a value as a last resort. Port of
/// <c>com.google.copybara.onboard.core.AskInputProvider</c>.
/// </summary>
public class AskInputProvider : IInputProvider
{
    // We could use empty string but then we wouldn't allow the user to use the empty string when
    // the default is not empty.
    public const string DefaultPlaceHolder = "PLEASE_USE_THE_DEFAULT";

    private readonly IInputProvider _delegate;
    private readonly AskMode _mode;
    private readonly Console _console;

    public AskInputProvider(IInputProvider @delegate, AskMode mode, Console console)
    {
        _delegate = @delegate;
        _mode = mode;
        _console = console;
    }

    public T? Resolve<T>(Input<T> input, IInputProviderResolver resolver)
        where T : class
    {
        T? res = _delegate.Resolve(input, resolver);
        return HandleInput(_mode, input, res, _console, resolver);
    }

    public IReadOnlyDictionary<IInput, int> Provides() => _delegate.Provides();

    private static T? HandleInput<T>(
        AskMode mode, Input<T> input, T? res, Console console, IInputProviderResolver resolver)
        where T : class
    {
        switch (mode)
        {
            case AskMode.Fail:
            {
                T? result = InputOrDefault(input, res);
                if (result != null)
                {
                    return result;
                }

                throw new CannotProvideException(
                    $"Couldn't infer a value for {input.Name}({input.Description})");
            }

            case AskMode.Confirm:
                return AskUser(input, InputOrDefault(input, res), console, resolver);

            case AskMode.Auto:
            {
                T? defaultVal = InputOrDefault(input, res);
                if (defaultVal != null)
                {
                    console.InfoFmt(
                        "Inferred value for '%s(%s)': %s",
                        input.Description, input.Name, defaultVal);
                    return defaultVal;
                }

                return AskUser(input, defaultVal, console, resolver);
            }

            default:
                throw new InvalidOperationException("Unknown mode " + mode);
        }
    }

    private static T? AskUser<T>(
        Input<T> input, T? defaultVal, Console console, IInputProviderResolver resolver)
        where T : class
    {
        try
        {
            string defaultLabel = defaultVal != null ? $"'{defaultVal}'" : "none";
            string askResult =
                console.Ask(
                    string.Format(
                        "{0}({1})? [default: {2}] ",
                        input.Description,
                        input.Name,
                        defaultLabel),
                    defaultVal != null ? DefaultPlaceHolder : null,
                    s =>
                    {
                        if (s.Equals(DefaultPlaceHolder, StringComparison.Ordinal) && defaultVal != null)
                        {
                            return true;
                        }

                        try
                        {
                            _ = input.Convert(s, resolver);
                            return true;
                        }
                        catch (InvalidOperationException)
                        {
                            // Don't ignore internal errors.
                            throw;
                        }
                        catch (Exception e)
                        {
                            console.Error(e.Message);
                            return false;
                        }
                    });

            if (DefaultPlaceHolder.Equals(askResult, StringComparison.Ordinal) && defaultVal != null)
            {
                return defaultVal;
            }

            return input.Convert(askResult, resolver);
        }
        catch (IOException e)
        {
            // We only throw IO on user cancellation. We need to fix that.
            throw new ThreadInterruptedException(e.Message);
        }
        catch (CannotConvertException e)
        {
            throw new InvalidOperationException(
                $"Error processing {input}."
                    + " This is a copybara error. It should be catch by the validator",
                e);
        }
    }

    private static T? InputOrDefault<T>(Input<T> input, T? res)
        where T : class
    {
        if (res != null)
        {
            return res;
        }

        return input.DefaultValue;
    }

    public override string ToString() => "AskInput(" + _delegate + ')';
}
