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

using System.Reflection;
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// A MethodDescriptor is a wrapper around a C# method (or property) callable from Starlark, together
/// with its <see cref="StarlarkMethodAttribute"/> and parameter descriptors. Port of
/// <c>net.starlark.java.eval.MethodDescriptor</c>.
/// </summary>
public sealed class MethodDescriptor
{
    private readonly MethodInfo method;
    private readonly StarlarkMethodAttribute annotation;
    private readonly string name;
    private readonly ParamDescriptor[] parameters;
    private readonly bool structField;
    private readonly bool useStarlarkThread;
    private readonly bool useStarlarkSemantics;
    private readonly bool selfCall;
    private readonly bool allowReturnNones;
    private readonly bool extraPositionals;
    private readonly bool extraKeywords;

    // The full CLR parameter list of the underlying method, in declaration order. Each entry maps a
    // CLR parameter to how it is filled at call time.
    private readonly ClrParam[] clrParams;

    private enum ClrKind { Param, Thread, Semantics, ExtraPositionals, ExtraKeywords }

    private readonly struct ClrParam
    {
        internal ClrKind Kind { get; init; }

        // Index into `parameters` when Kind == Param.
        internal int ParamIndex { get; init; }
    }

    private MethodDescriptor(
        MethodInfo method,
        StarlarkMethodAttribute annotation,
        string name,
        ParamDescriptor[] parameters,
        bool structField,
        bool useStarlarkThread,
        bool useStarlarkSemantics,
        bool selfCall,
        bool allowReturnNones,
        bool extraPositionals,
        bool extraKeywords,
        ClrParam[] clrParams)
    {
        this.method = method;
        this.annotation = annotation;
        this.name = name;
        this.parameters = parameters;
        this.structField = structField;
        this.useStarlarkThread = useStarlarkThread;
        this.useStarlarkSemantics = useStarlarkSemantics;
        this.selfCall = selfCall;
        this.allowReturnNones = allowReturnNones;
        this.extraPositionals = extraPositionals;
        this.extraKeywords = extraKeywords;
        this.clrParams = clrParams;
    }

    /// <summary>Constructs a MethodDescriptor from a C# method and its annotation.</summary>
    internal static MethodDescriptor Of(MethodInfo method, StarlarkMethodAttribute annotation)
    {
        ParameterInfo[] clr = method.GetParameters();
        var clrParams = new ClrParam[clr.Length];
        var starlarkParams = new List<ParamDescriptor>();
        bool extraPositionals = false;
        bool extraKeywords = false;

        for (int i = 0; i < clr.Length; i++)
        {
            ParameterInfo p = clr[i];
            Type t = p.ParameterType;
            if (t == typeof(StarlarkThread))
            {
                clrParams[i] = new ClrParam { Kind = ClrKind.Thread };
            }
            else if (t == typeof(StarlarkSemantics))
            {
                clrParams[i] = new ClrParam { Kind = ClrKind.Semantics };
            }
            else
            {
                var attr = p.GetCustomAttribute<ParamAttribute>();
                // Heuristic for *args / **kwargs: a trailing Tuple/Sequence parameter with no
                // ParamAttribute becomes extraPositionals; a trailing Dict without a ParamAttribute
                // becomes extraKeywords. To be explicit, callers annotate these with a ParamAttribute
                // whose name signals the residual; here we rely on the annotation being absent.
                bool isResidual = attr == null;
                if (isResidual && (t == typeof(Dict)) && !extraKeywords)
                {
                    clrParams[i] = new ClrParam { Kind = ClrKind.ExtraKeywords };
                    extraKeywords = true;
                }
                else if (isResidual
                    && (t == typeof(Tuple)
                        || typeof(ISequence<object?>).IsAssignableFrom(t)
                        || typeof(System.Collections.IEnumerable).IsAssignableFrom(t) && t != typeof(string))
                    && !extraPositionals)
                {
                    clrParams[i] = new ClrParam { Kind = ClrKind.ExtraPositionals };
                    extraPositionals = true;
                }
                else
                {
                    var pd = ParamDescriptor.Of(p, attr);
                    clrParams[i] = new ClrParam { Kind = ClrKind.Param, ParamIndex = starlarkParams.Count };
                    starlarkParams.Add(pd);
                }
            }
        }

        return new MethodDescriptor(
            method,
            annotation,
            annotation.Name,
            starlarkParams.ToArray(),
            annotation.StructField,
            annotation.UseStarlarkThread,
            annotation.UseStarlarkSemantics,
            annotation.SelfCall,
            annotation.AllowReturnNones,
            extraPositionals,
            extraKeywords,
            clrParams);
    }

    /// <summary>The Starlark name of the method.</summary>
    public string Name => name;

    /// <summary>The declared Starlark parameters (excluding thread/semantics/residuals).</summary>
    public ParamDescriptor[] Parameters => parameters;

    /// <summary>Whether this method is accessed as a struct field.</summary>
    public bool IsStructField => structField;

    /// <summary>Whether the enclosing value is callable via this method.</summary>
    public bool IsSelfCall => selfCall;

    /// <summary>Whether this method accepts extra positional arguments (*args).</summary>
    public bool AcceptsExtraArgs => extraPositionals;

    /// <summary>Whether this method accepts extra keyword arguments (**kwargs).</summary>
    public bool AcceptsExtraKwargs => extraKeywords;

    /// <summary>Whether the StarlarkThread is passed to the method.</summary>
    public bool IsUseStarlarkThread => useStarlarkThread || HasClrKind(ClrKind.Thread);

    private bool HasClrKind(ClrKind kind)
    {
        foreach (ClrParam c in clrParams)
        {
            if (c.Kind == kind)
            {
                return true;
            }
        }
        return false;
    }

    /// <summary>Documentation for the method.</summary>
    public string Doc => annotation.Doc;

    /// <summary>
    /// Invokes the underlying method. <paramref name="vector"/> supplies the values for the declared
    /// Starlark parameters, in <see cref="Parameters"/> order, followed by *args (if accepted),
    /// **kwargs (if accepted). The StarlarkThread/StarlarkSemantics slots are filled from
    /// <paramref name="thread"/>.
    /// </summary>
    internal object? Call(object receiver, object?[] vector, Tuple? varargs, Dict? kwargs, StarlarkThread thread)
    {
        object?[] clrArgs = new object?[clrParams.Length];
        for (int i = 0; i < clrParams.Length; i++)
        {
            ClrParam c = clrParams[i];
            switch (c.Kind)
            {
                case ClrKind.Param:
                    clrArgs[i] = vector[c.ParamIndex];
                    break;
                case ClrKind.Thread:
                    clrArgs[i] = thread;
                    break;
                case ClrKind.Semantics:
                    clrArgs[i] = thread.GetSemantics();
                    break;
                case ClrKind.ExtraPositionals:
                    clrArgs[i] = varargs ?? Tuple.Empty();
                    break;
                case ClrKind.ExtraKeywords:
                    clrArgs[i] = kwargs ?? Dict.Of(thread.Mutability);
                    break;
            }
        }

        object? result;
        try
        {
            result = method.Invoke(receiver, clrArgs);
        }
        catch (TargetInvocationException ex)
        {
            Exception? e = ex.InnerException;
            switch (e)
            {
                case null:
                    throw;
                case EvalException:
                    throw e;
                default:
                    throw new EvalException(e.Message, e);
            }
        }

        if (method.ReturnType == typeof(void))
        {
            return Starlark.None;
        }
        if (result == null)
        {
            if (allowReturnNones)
            {
                return Starlark.None;
            }
            return Starlark.None;
        }
        return result;
    }
}
