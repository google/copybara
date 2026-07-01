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

using Starlark.Syntax;

namespace Starlark.Eval;

/// <summary>
/// A StarlarkFunction is a user-defined function (<c>def</c> or <c>lambda</c>) that may be called
/// from Starlark. Port of <c>net.starlark.java.eval.StarlarkFunction</c>.
/// </summary>
public sealed class StarlarkFunction : IStarlarkCallable
{
    /// <summary>A Cell is a boxed reference to a variable shared by nested functions (closures).</summary>
    public sealed class Cell
    {
        public object? X;

        public Cell(object? x) => X = x;
    }

    /// <summary>Sentinel marking a mandatory parameter slot in the defaults tuple.</summary>
    internal static readonly object MANDATORY = new MandatoryMarker();

    private sealed class MandatoryMarker { }

    private readonly Resolver.Function rfn;
    private readonly Module module;
    private readonly int[] globalIndex;
    private readonly Tuple defaultValues;
    private readonly Tuple freevars;

    internal StarlarkFunction(
        Resolver.Function rfn,
        Module module,
        int[] globalIndex,
        Tuple defaultValues,
        Tuple freevars)
    {
        this.rfn = rfn;
        this.module = module;
        this.globalIndex = globalIndex;
        this.defaultValues = defaultValues;
        this.freevars = freevars;
    }

    /// <summary>The resolved function metadata.</summary>
    public Resolver.Function Rfn => rfn;

    /// <summary>The module in which the function was defined.</summary>
    public Module Module => module;

    internal int[] GlobalIndex => globalIndex;

    public string Name => rfn.GetName();

    public Location Location => rfn.GetLocation();

    public bool IsImmutable()
    {
        // Only correct if defaults and freevars are immutable, which we assume.
        return true;
    }

    /// <summary>Returns the value of the ith global variable, mapping via the module.</summary>
    internal object? GetGlobal(int index) => module.GetGlobalByIndex(globalIndex[index]);

    internal void SetGlobal(int index, object value) =>
        module.SetGlobalByIndex(globalIndex[index], value);

    internal Cell GetFreeVar(int index) => (Cell)freevars[index]!;

    /// <summary>Returns the names of parameters, in run-time order.</summary>
    public IReadOnlyList<string> GetParameterNames() => rfn.GetParameterNames();

    public bool HasVarargs() => rfn.HasVarargs();

    public bool HasKwargs() => rfn.HasKwargs();

    public object? Fastcall(StarlarkThread thread, object?[] positional, object?[] named)
    {
        object?[] locals = ProcessArgs(thread, positional, named);

        // Spill cells.
        foreach (int index in rfn.GetCellIndices())
        {
            locals[index] = new Cell(locals[index]);
        }

        // Recursion check.
        if (!thread.IsRecursionAllowed() && IsRecursiveCall(thread))
        {
            throw Starlark.Errorf("function '{0}' called recursively", Name);
        }

        return Eval.ExecFunctionBody(thread, this, locals, rfn.GetBody());
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

    private bool IsRecursiveCall(StarlarkThread thread)
    {
        // The top frame (depth 0) is this function, just pushed by Starlark.Fastcall. Search the
        // enclosing frames for the same resolved definition.
        int n = thread.GetCallStackSize();
        for (int i = 1; i < n; i++)
        {
            if (thread.FrameFnAt(i) is StarlarkFunction sf && ReferenceEquals(sf.rfn, rfn))
            {
                return true;
            }
        }
        return false;
    }

    // Binds positional and named arguments to a fresh locals array of the correct size.
    private object?[] ProcessArgs(StarlarkThread thread, object?[] positional, object?[] named)
    {
        int nlocals = rfn.GetLocals().Count;
        object?[] locals = new object?[nlocals];

        int numOrdinary = rfn.GetNumOrdinaryParameters();
        int numPositional = rfn.GetNumNonResidualParameters(); // ordinary + keyword-only
        int nparams = rfn.GetParameters().Count;

        var varargsList = HasVarargs() ? new List<object?>() : null;

        // Positional arguments.
        int p = 0;
        for (; p < positional.Length && p < numOrdinary; p++)
        {
            locals[p] = positional[p];
        }
        int numNonSurplusPositional = p;
        if (p < positional.Length)
        {
            if (varargsList != null)
            {
                for (; p < positional.Length; p++)
                {
                    varargsList.Add(positional[p]);
                }
            }
            else
            {
                if (numOrdinary > 0)
                {
                    throw Starlark.Errorf(
                        "{0}() accepts no more than {1} positional argument{2} but got {3}",
                        Name, numOrdinary, Plural(numOrdinary), positional.Length);
                }
                throw Starlark.Errorf(
                    "{0}() does not accept positional arguments, but got {1}", Name, positional.Length);
            }
        }

        // Named arguments.
        IReadOnlyList<string> paramNames = rfn.GetParameterNames();
        Dict.Builder? kwargsBuilder = HasKwargs() ? new Dict.Builder() : null;
        for (int i = 0; i < named.Length; i += 2)
        {
            string key = (string)named[i]!;
            object? value = named[i + 1];
            int found = -1;
            for (int j = 0; j < numPositional; j++)
            {
                if (paramNames[j] == key)
                {
                    found = j;
                    break;
                }
            }
            if (found >= 0)
            {
                if (locals[found] != null)
                {
                    throw Starlark.Errorf("{0}() got multiple values for parameter '{1}'", Name, key);
                }
                locals[found] = value;
            }
            else if (kwargsBuilder != null)
            {
                kwargsBuilder.Put(key, value);
            }
            else
            {
                throw Starlark.Errorf("{0}() got unexpected keyword argument '{1}'", Name, key);
            }
        }

        // Residual parameters.
        if (HasVarargs())
        {
            int idx = numPositional; // *args slot follows ordinary+keyword-only params
            locals[idx] = varargsList!.Count == 0 ? Tuple.Empty() : Tuple.CopyOf(varargsList);
        }
        if (HasKwargs())
        {
            int idx = numPositional + (HasVarargs() ? 1 : 0);
            locals[idx] = kwargsBuilder!.Build(thread.Mutability);
        }

        // Apply defaults / report missing.
        ApplyDefaultsReportMissing(locals, numPositional);

        return locals;
    }

    private void ApplyDefaultsReportMissing(object?[] locals, int numPositional)
    {
        // defaultValues covers the trailing suffix of the non-residual parameters.
        int nparams = numPositional;
        int ndefaults = defaultValues.Count;
        int firstDefaulted = nparams - ndefaults;

        List<string>? missingPositional = null;
        List<string>? missingNamed = null;
        int numOrdinary = rfn.GetNumOrdinaryParameters();
        IReadOnlyList<string> paramNames = rfn.GetParameterNames();

        for (int i = 0; i < nparams; i++)
        {
            if (locals[i] != null)
            {
                continue;
            }
            object? def = i >= firstDefaulted ? defaultValues[i - firstDefaulted] : null;
            if (def != null && !ReferenceEquals(def, MANDATORY))
            {
                locals[i] = def;
            }
            else
            {
                if (i < numOrdinary)
                {
                    (missingPositional ??= new List<string>()).Add(paramNames[i]);
                }
                else
                {
                    (missingNamed ??= new List<string>()).Add(paramNames[i]);
                }
            }
        }
        if (missingPositional != null)
        {
            throw Starlark.Errorf(
                "{0}() missing {1} required positional argument{2}: {3}",
                Name, missingPositional.Count, Plural(missingPositional.Count),
                string.Join(", ", missingPositional));
        }
        if (missingNamed != null)
        {
            throw Starlark.Errorf(
                "{0}() missing {1} required keyword-only argument{2}: {3}",
                Name, missingNamed.Count, Plural(missingNamed.Count),
                string.Join(", ", missingNamed));
        }
    }

    private static string Plural(int n) => n == 1 ? "" : "s";

    public void Repr(Printer printer, StarlarkSemantics semantics) =>
        printer.Append("<function " + Name + ">");

    public override string ToString() => "<function " + Name + ">";
}
