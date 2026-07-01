// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

using Starlark.Syntax;

namespace Starlark.Eval;

/// <summary>
/// A BuiltinFunction is a callable Starlark value that reflectively invokes a
/// <see cref="StarlarkMethodAttribute"/>-annotated method of a receiver. Port of
/// <c>net.starlark.java.eval.BuiltinFunction</c>.
/// </summary>
public sealed class BuiltinFunction : IStarlarkCallable
{
    private readonly object receiver;
    private readonly string name;
    private readonly MethodDescriptor desc;

    /// <summary>Constructs a BuiltinFunction bound to a receiver and descriptor.</summary>
    public BuiltinFunction(object receiver, string name, MethodDescriptor desc)
    {
        this.receiver = receiver;
        this.name = name;
        this.desc = desc;
    }

    /// <summary>Constructs a BuiltinFunction from a receiver and descriptor (name from descriptor).</summary>
    public static BuiltinFunction Of(object receiver, MethodDescriptor desc) =>
        new(receiver, desc.Name, desc);

    public string Name => name;

    public MethodDescriptor Descriptor => desc;

    public bool IsImmutable() => true;

    public Location Location => Location.BUILTIN;

    public object? Fastcall(StarlarkThread thread, object?[] positional, object?[] named)
    {
        // String methods have StringModule.INSTANCE as the true receiver and the string as the
        // first (self) positional parameter.
        object recv = receiver;
        if (receiver is string self)
        {
            recv = StringModule.INSTANCE;
            var pos2 = new object?[positional.Length + 1];
            pos2[0] = self;
            Array.Copy(positional, 0, pos2, 1, positional.Length);
            positional = pos2;
        }

        ParamDescriptor[] parameters = desc.Parameters;
        int nparams = parameters.Length;
        object?[] vector = new object?[nparams];

        // Assign positional arguments.
        int argIndex = 0;
        int paramIndex = 0;
        var varargs = new List<object?>();
        for (; argIndex < positional.Length && paramIndex < nparams; paramIndex++)
        {
            ParamDescriptor param = parameters[paramIndex];
            if (!param.IsPositional)
            {
                break;
            }
            object? value = positional[argIndex++];
            CheckParamValue(param, value);
            vector[paramIndex] = value;
        }

        // Surplus positionals.
        if (desc.AcceptsExtraArgs)
        {
            for (; argIndex < positional.Length; argIndex++)
            {
                varargs.Add(positional[argIndex]);
            }
        }
        else if (argIndex < positional.Length)
        {
            if (argIndex == 0)
            {
                throw Starlark.Errorf("{0}() got unexpected positional argument", name);
            }
            throw Starlark.Errorf(
                "{0}() accepts no more than {1} positional argument{2} but got {3}",
                name, argIndex, Plural(argIndex), positional.Length);
        }

        // Assign named arguments.
        Dict.Builder? kwargsBuilder = desc.AcceptsExtraKwargs ? new Dict.Builder() : null;
        for (int i = 0; i < named.Length; i += 2)
        {
            string key = (string)named[i]!;
            object? value = named[i + 1];
            int found = -1;
            for (int p = 0; p < nparams; p++)
            {
                if (parameters[p].IsNamed && parameters[p].Name == key)
                {
                    found = p;
                    break;
                }
            }
            if (found >= 0)
            {
                if (vector[found] != null)
                {
                    throw Starlark.Errorf("{0}() got multiple values for argument '{1}'", name, key);
                }
                CheckParamValue(parameters[found], value);
                vector[found] = value;
            }
            else if (kwargsBuilder != null)
            {
                kwargsBuilder.Put(key, value);
            }
            else
            {
                throw Starlark.Errorf("{0}() got unexpected keyword argument '{1}'", name, key);
            }
        }

        // Apply defaults / report missing.
        List<string>? missingPositional = null;
        List<string>? missingNamed = null;
        for (int p = 0; p < nparams; p++)
        {
            if (vector[p] != null)
            {
                continue;
            }
            ParamDescriptor param = parameters[p];
            object? def = param.DefaultValue;
            if (def != null)
            {
                vector[p] = def;
            }
            else
            {
                if (param.IsPositional)
                {
                    (missingPositional ??= new List<string>()).Add(param.Name);
                }
                else
                {
                    (missingNamed ??= new List<string>()).Add(param.Name);
                }
            }
        }
        if (missingPositional != null)
        {
            throw Starlark.Errorf(
                "{0}() missing {1} required positional argument{2}: {3}",
                name, missingPositional.Count, Plural(missingPositional.Count),
                string.Join(", ", missingPositional));
        }
        if (missingNamed != null)
        {
            throw Starlark.Errorf(
                "{0}() missing {1} required named argument{2}: {3}",
                name, missingNamed.Count, Plural(missingNamed.Count),
                string.Join(", ", missingNamed));
        }

        Tuple? varargsTuple = desc.AcceptsExtraArgs ? Tuple.CopyOf(varargs) : null;
        Dict? kwargsDict = kwargsBuilder?.Build(thread.Mutability);

        return desc.Call(recv, vector, varargsTuple, kwargsDict, thread);
    }

    public object? Call(StarlarkThread thread, Tuple args, Dict kwargs)
    {
        object?[] positional = args.ToArray();
        var named = new List<object?>();
        foreach (var e in kwargs.Entries)
        {
            named.Add(e.Key);
            named.Add(e.Value);
        }
        return Fastcall(thread, positional, named.ToArray());
    }

    private void CheckParamValue(ParamDescriptor param, object? value)
    {
        IReadOnlyList<Type>? allowed = param.AllowedClasses;
        if (allowed == null)
        {
            return;
        }
        if (param.IsNoneable && ReferenceEquals(value, Starlark.None))
        {
            return;
        }
        foreach (Type t in allowed)
        {
            if (value != null && t.IsInstanceOfType(value))
            {
                return;
            }
            if (t == typeof(NoneType) && ReferenceEquals(value, Starlark.None))
            {
                return;
            }
        }
        throw Starlark.Errorf(
            "in call to {0}(), parameter '{1}' got value of type '{2}', want one of the allowed types",
            name, param.Name, Starlark.Type(value));
    }

    private static string Plural(int n) => n == 1 ? "" : "s";

    public override string ToString() => name;
}
