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
using System.Globalization;
using System.Reflection;
using Copybara;
using Copybara.Exceptions;

namespace Copybara.Cli;

/// <summary>
/// A lightweight command-line argument parser that replaces JCommander. It discovers
/// <see cref="FlagAttribute"/>-annotated members on a set of option objects and binds
/// <c>--flag=value</c> / <c>--flag value</c> / boolean flags onto them, collecting anything not
/// recognized as a flag into a list of positional (unnamed) arguments.
///
/// <para>This is intentionally minimal: it supports the flag surface Copybara actually uses
/// (string, bool, int, <see cref="TimeSpan"/>, enum-as-string, list and map flags) but does not
/// aim to reproduce every JCommander feature.</para>
/// </summary>
public sealed class ArgParser
{
    /// <summary>A single registered flag: the member it binds to and the object owning it.</summary>
    private sealed class Flag
    {
        public required object Owner { get; init; }
        public required MemberInfo Member { get; init; }
        public required FlagAttribute Attribute { get; init; }

        public Type MemberType =>
            Member is PropertyInfo p ? p.PropertyType : ((FieldInfo)Member).FieldType;

        /// <summary>Whether this is a "switch" flag that takes no value (unless arity forces one).</summary>
        public bool IsBooleanSwitch
        {
            get
            {
                var t = MemberType;
                // A bool flag with explicit arity 1 (e.g. --noprompt=true) still expects a value.
                return (t == typeof(bool) || t == typeof(bool?)) && Attribute.Arity != 1;
            }
        }

        public void SetValue(object? value)
        {
            switch (Member)
            {
                case PropertyInfo p:
                    p.SetValue(Owner, value);
                    break;
                case FieldInfo f:
                    f.SetValue(Owner, value);
                    break;
            }
        }

        public object? GetValue() =>
            Member is PropertyInfo p ? p.GetValue(Owner) : ((FieldInfo)Member).GetValue(Owner);
    }

    private readonly Dictionary<string, Flag> _flagsByName = new(StringComparer.Ordinal);
    private readonly List<Flag> _allFlags = new();

    /// <summary>Registers every <see cref="FlagAttribute"/>-annotated member found on the object.</summary>
    public void AddObject(object optionObject)
    {
        const BindingFlags bindingFlags =
            BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance;

        foreach (MemberInfo member in optionObject.GetType()
                     .GetMembers(bindingFlags)
                     .Where(m => m is PropertyInfo or FieldInfo))
        {
            var attr = member.GetCustomAttribute<FlagAttribute>(inherit: true);
            if (attr == null)
            {
                continue;
            }

            var flag = new Flag { Owner = optionObject, Member = member, Attribute = attr };
            _allFlags.Add(flag);
            foreach (string name in attr.Names)
            {
                _flagsByName[name] = flag;
            }
        }
    }

    /// <summary>Registers every option in the collection.</summary>
    public void AddObjects(IEnumerable<object> optionObjects)
    {
        foreach (var o in optionObjects)
        {
            AddObject(o);
        }
    }

    /// <summary>All registered flag names, used for "did you mean" hints and usage output.</summary>
    public IReadOnlyList<string> AllFlagNames =>
        _allFlags.SelectMany(f => f.Attribute.Names).Distinct().OrderBy(n => n, StringComparer.Ordinal)
            .ToImmutableArray();

    /// <summary>Descriptions of all non-hidden flags for usage output.</summary>
    public IReadOnlyList<(string Names, string Description)> Descriptions =>
        _allFlags.Where(f => !f.Attribute.Hidden)
            .Select(f => (string.Join(", ", f.Attribute.Names), f.Attribute.Description))
            .ToImmutableArray();

    /// <summary>
    /// Parses <paramref name="args"/>, binding recognized flags onto the registered option objects
    /// and returning the positional (unnamed) arguments in order.
    /// </summary>
    /// <exception cref="CommandLineException">on unknown flags or malformed values.</exception>
    public IReadOnlyList<string> Parse(string[] args)
    {
        var unnamed = ImmutableArray.CreateBuilder<string>();
        int i = 0;
        while (i < args.Length)
        {
            string arg = args[i];

            // Only tokens beginning with '-' are candidate flags. Everything else is positional.
            if (arg.Length < 2 || arg[0] != '-')
            {
                unnamed.Add(arg);
                i++;
                continue;
            }

            string name = arg;
            string? inlineValue = null;
            int eq = arg.IndexOf('=');
            if (eq >= 0)
            {
                name = arg.Substring(0, eq);
                inlineValue = arg.Substring(eq + 1);
            }

            if (!_flagsByName.TryGetValue(name, out Flag? flag))
            {
                // Not a known flag. Treat as positional so Main can warn about likely typos, matching
                // upstream's behavior of surfacing "looks like a flag" arguments to the command.
                unnamed.Add(arg);
                i++;
                continue;
            }

            if (flag.IsBooleanSwitch && inlineValue == null)
            {
                flag.SetValue(true);
                i++;
                continue;
            }

            string rawValue;
            if (inlineValue != null)
            {
                rawValue = inlineValue;
                i++;
            }
            else
            {
                if (i + 1 >= args.Length)
                {
                    throw new CommandLineException($"Missing value for flag '{name}'.");
                }
                rawValue = args[i + 1];
                i += 2;
            }

            AssignValue(flag, name, rawValue);
        }

        return unnamed.ToImmutable();
    }

    private static void AssignValue(Flag flag, string name, string rawValue)
    {
        Type type = flag.MemberType;
        Type target = Nullable.GetUnderlyingType(type) ?? type;

        try
        {
            if (target == typeof(string))
            {
                flag.SetValue(rawValue);
            }
            else if (target == typeof(bool))
            {
                flag.SetValue(ParseBool(rawValue));
            }
            else if (target == typeof(int))
            {
                flag.SetValue(int.Parse(rawValue, CultureInfo.InvariantCulture));
            }
            else if (target == typeof(long))
            {
                flag.SetValue(long.Parse(rawValue, CultureInfo.InvariantCulture));
            }
            else if (target == typeof(TimeSpan))
            {
                flag.SetValue(DurationConverter.Convert(rawValue));
            }
            else if (target.IsEnum)
            {
                flag.SetValue(Enum.Parse(target, rawValue, ignoreCase: true));
            }
            else if (IsListType(target, out Type? elementType))
            {
                AppendToList(flag, rawValue, elementType!);
            }
            else if (IsDictType(target))
            {
                AssignDict(flag, rawValue);
            }
            else
            {
                throw new CommandLineException(
                    $"Unsupported flag type '{type}' for flag '{name}'.");
            }
        }
        catch (CommandLineException)
        {
            throw;
        }
        catch (Exception e)
        {
            throw new CommandLineException($"Invalid value '{rawValue}' for flag '{name}': {e.Message}");
        }
    }

    private static bool ParseBool(string value)
    {
        if (bool.TryParse(value, out bool b))
        {
            return b;
        }
        throw new CommandLineException($"Expected 'true' or 'false' but got '{value}'.");
    }

    private static bool IsListType(Type type, out Type? elementType)
    {
        if (type.IsGenericType)
        {
            Type def = type.GetGenericTypeDefinition();
            if (def == typeof(List<>) || def == typeof(IReadOnlyList<>)
                || def == typeof(IList<>) || def == typeof(ImmutableArray<>))
            {
                elementType = type.GetGenericArguments()[0];
                return true;
            }
        }
        elementType = null;
        return false;
    }

    private static bool IsDictType(Type type) =>
        type.IsGenericType
        && (type.GetGenericTypeDefinition() == typeof(ImmutableDictionary<,>)
            || type.GetGenericTypeDefinition() == typeof(Dictionary<,>));

    private static void AppendToList(Flag flag, string rawValue, Type elementType)
    {
        // List flags accept comma-separated values, matching JCommander's default splitter.
        var pieces = rawValue.Split(',', StringSplitOptions.RemoveEmptyEntries);
        var current = flag.GetValue();
        var listType = typeof(List<>).MakeGenericType(elementType);
        var list = (System.Collections.IList)Activator.CreateInstance(listType)!;
        if (current is System.Collections.IEnumerable existing and not string)
        {
            foreach (var item in existing)
            {
                list.Add(item);
            }
        }
        foreach (var piece in pieces)
        {
            list.Add(elementType == typeof(string)
                ? piece
                : Convert.ChangeType(piece, elementType, CultureInfo.InvariantCulture));
        }
        flag.SetValue(list);
    }

    private static void AssignDict(Flag flag, string rawValue)
    {
        // Map flags are 'k1:v1,k2:v2'. Only <string,string> is used in practice.
        var builder = ImmutableDictionary.CreateBuilder<string, string>();
        if (flag.GetValue() is ImmutableDictionary<string, string> existing)
        {
            foreach (var kv in existing)
            {
                builder[kv.Key] = kv.Value;
            }
        }
        foreach (var entry in rawValue.Split(',', StringSplitOptions.RemoveEmptyEntries))
        {
            int colon = entry.IndexOf(':');
            if (colon < 0)
            {
                throw new CommandLineException(
                    $"Expected 'key:value' pairs but got '{entry}'.");
            }
            builder[entry.Substring(0, colon)] = entry.Substring(colon + 1);
        }
        flag.SetValue(builder.ToImmutable());
    }
}
