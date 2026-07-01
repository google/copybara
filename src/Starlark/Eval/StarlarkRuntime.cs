// Copyright 2014 The Bazel Authors. All rights reserved.
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

using System.Collections.Immutable;
using Starlark.Syntax;
using FileOptions = Starlark.Syntax.FileOptions;

namespace Starlark.Eval;

/// <summary>
/// The dynamic entry points of the Starlark interpreter: calling, attribute access, and the
/// exec/eval file drivers. Port of the corresponding members of
/// <c>net.starlark.java.eval.Starlark</c>.
/// </summary>
public static partial class Starlark
{
    // ---- Calling ----

    /// <summary>
    /// Calls the callable <paramref name="fn"/> with positional and keyword arguments.
    /// <paramref name="named"/> is a flat array of alternating name/value pairs.
    /// </summary>
    public static object? Fastcall(
        StarlarkThread thread, object? fn, object?[] positional, object?[] named)
    {
        IStarlarkCallable callable = GetStarlarkCallable(thread, fn);
        thread.Push(callable);
        try
        {
            return callable.Fastcall(thread, positional, named);
        }
        finally
        {
            thread.Pop();
        }
    }

    /// <summary>Calls the callable with a list of positional args and a map of keyword args.</summary>
    public static object? Call(
        StarlarkThread thread, object? fn, IReadOnlyList<object?> args, IReadOnlyDictionary<string, object?> kwargs)
    {
        object?[] positional = args.ToArray();
        var named = new object?[kwargs.Count * 2];
        int i = 0;
        foreach (var e in kwargs)
        {
            named[i++] = e.Key;
            named[i++] = CheckValid(e.Value);
        }
        return Fastcall(thread, fn, positional, named);
    }

    /// <summary>Returns the value as a StarlarkCallable, or throws if it is not callable.</summary>
    internal static IStarlarkCallable GetStarlarkCallable(StarlarkThread thread, object? fn)
    {
        if (fn is IStarlarkCallable callable)
        {
            return callable;
        }
        if (fn != null)
        {
            MethodDescriptor? desc = CallUtils.GetSelfCallMethodDescriptor(fn.GetType());
            if (desc != null)
            {
                return BuiltinFunction.Of(fn, desc);
            }
        }
        throw Errorf("'{0}' object is not callable", Type(fn));
    }

    // ---- Attribute access ----

    /// <summary>
    /// Returns the named field or method of value <paramref name="x"/>, as if by <c>x.name</c> /
    /// <c>getattr(x, name, defaultValue)</c>. If absent and no default is given, throws.
    /// </summary>
    public static object? GetAttr(StarlarkThread thread, object? x, string name, object? defaultValue)
    {
        StarlarkSemantics semantics = thread.GetSemantics();
        Mutability mu = thread.Mutability;

        if (x != null)
        {
            ImmutableDictionary<string, MethodDescriptor> methods =
                CallUtils.GetAnnotatedMethods(x.GetType());
            if (methods.TryGetValue(name, out MethodDescriptor? method))
            {
                if (method.IsStructField)
                {
                    // Invoke the field getter (no Starlark args).
                    object recv = x is string ? StringModule.INSTANCE : x;
                    object?[] vector = new object?[method.Parameters.Length];
                    // String struct fields take self as first param.
                    if (x is string s && vector.Length > 0)
                    {
                        vector[0] = s;
                    }
                    return method.Call(recv, vector, null, null, thread);
                }
                return BuiltinFunction.Of(x, method);
            }
        }

        if (x is IStructure structure)
        {
            object? field = structure.GetValue(semantics, name);
            if (field != null)
            {
                return CheckValid(field);
            }
            if (defaultValue != null)
            {
                return defaultValue;
            }
            string? error = structure.GetErrorMessageForUnknownField(name);
            if (error != null)
            {
                throw Errorf("{0}", error);
            }
        }
        else if (defaultValue != null)
        {
            return defaultValue;
        }

        throw Errorf("'{0}' value has no field or method '{1}'", Type(x), name);
    }

    /// <summary>Reports whether value <paramref name="x"/> has a field or method named <paramref name="name"/>.</summary>
    public static bool HasAttr(StarlarkThread thread, object? x, string name)
    {
        if (x is IStructure structure && structure.GetFieldNames().Contains(name))
        {
            return true;
        }
        return x != null && CallUtils.GetAnnotatedMethods(x.GetType()).ContainsKey(name);
    }

    /// <summary>Returns a sorted list of the field and method names of <paramref name="x"/>.</summary>
    public static StarlarkList Dir(StarlarkThread thread, object? x)
    {
        var fields = new SortedSet<string>(StringComparer.Ordinal);
        if (x is IStructure structure)
        {
            foreach (string f in structure.GetFieldNames())
            {
                fields.Add(f);
            }
        }
        if (x != null)
        {
            foreach (string m in CallUtils.GetAnnotatedMethods(x.GetType()).Keys)
            {
                fields.Add(m);
            }
        }
        return StarlarkList.CopyOf(thread.Mutability, fields.Cast<object?>());
    }

    // ---- File / expression execution ----

    /// <summary>
    /// Parses, resolves, compiles, and executes a Starlark file in the given module and thread.
    /// Returns the value of the file's final expression statement, if any, else None.
    /// </summary>
    public static object? ExecFile(
        ParserInput input, FileOptions options, Module module, StarlarkThread thread)
    {
        StarlarkFile file = StarlarkFile.Parse(input, options);
        Program prog = Program.CompileFile(file, ModuleAsResolverModule(module));
        return ExecFileProgram(prog, module, thread);
    }

    /// <summary>Executes a previously compiled Program in the given module and thread.</summary>
    public static object? ExecFileProgram(Program prog, Module module, StarlarkThread thread)
    {
        Resolver.Function rfn = prog.GetResolvedFunction();
        int[] globalIndex = module.GetIndicesOfGlobals(rfn.GetGlobals());

        if (module.GetDocumentation() == null)
        {
            string? documentation = rfn.GetDocumentation();
            if (documentation != null)
            {
                module.SetDocumentation(documentation);
            }
        }

        var toplevel = new StarlarkFunction(rfn, module, globalIndex, Tuple.Empty(), Tuple.Empty());
        return Fastcall(thread, toplevel, Array.Empty<object?>(), Array.Empty<object?>());
    }

    /// <summary>Parses, resolves, compiles, and evaluates an expression, returning its value.</summary>
    public static object? Eval(
        ParserInput input, FileOptions options, Module module, StarlarkThread thread)
    {
        Expression expr = Expression.Parse(input, options);
        Program prog = Program.CompileExpr(expr, ModuleAsResolverModule(module), options);
        Resolver.Function rfn = prog.GetResolvedFunction();
        int[] globalIndex = module.GetIndicesOfGlobals(rfn.GetGlobals());
        var fn = new StarlarkFunction(rfn, module, globalIndex, Tuple.Empty(), Tuple.Empty());
        return Fastcall(thread, fn, Array.Empty<object?>(), Array.Empty<object?>());
    }

    /// <summary>Parses and executes a series of statements (discarding the result).</summary>
    public static void Exec(
        ParserInput input, FileOptions options, Module module, StarlarkThread thread)
    {
        ExecFile(input, options, module, thread);
    }

    /// <summary>Wraps a Module so it can serve as the resolver's static module environment.</summary>
    public static Resolver.IModule ModuleAsResolverModule(Module module) => new ModuleResolverAdapter(module);

    /// <summary>Adapts a <see cref="Module"/> to <see cref="Resolver.IModule"/> for resolution.</summary>
    private sealed class ModuleResolverAdapter : Resolver.IModule
    {
        private readonly Module module;

        internal ModuleResolverAdapter(Module module) => this.module = module;

        public Resolver.Scope Resolve(string name)
        {
            if (module.GetGlobal(name) != null)
            {
                return Resolver.Scope.GLOBAL;
            }
            object? v = module.GetPredeclared(name);
            if (v != null)
            {
                if (v is IGuardedValue gv)
                {
                    throw new Resolver.Undefined(gv.GetErrorFromAttemptingAccess(name));
                }
                return Resolver.Scope.PREDECLARED;
            }
            if (UNIVERSE.ContainsKey(name))
            {
                return Resolver.Scope.UNIVERSAL;
            }
            var candidates = new HashSet<string>();
            candidates.UnionWith(module.GetGlobals().Keys);
            candidates.UnionWith(module.GetPredeclaredBindings().Keys);
            candidates.UnionWith(UNIVERSE.Keys);
            throw new Resolver.Undefined(string.Format("name '{0}' is not defined", name), candidates);
        }

        public TypeConstructor? GetTypeConstructor(string name) => null;

        public StarlarkType? GetListFieldType(string name) => null;

        public StarlarkType? GetDictFieldType(string name) => null;

        public StarlarkType? GetSetFieldType(string name) => null;
    }
}
