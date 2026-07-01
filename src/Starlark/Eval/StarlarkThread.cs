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

namespace Starlark.Eval;

/// <summary>
/// A StarlarkThread represents a Starlark thread: the stack of active function calls plus per-thread
/// application state. Port of <c>net.starlark.java.eval.StarlarkThread</c>.
///
/// <para>Deferred vs. Java: CPU/wall profiler, debugger hooks, and the SymbolGenerator are omitted
/// pending the evaluator port; the mutability, semantics, thread-locals, call stack, and print/load
/// handlers are complete.</para>
/// </summary>
public sealed class StarlarkThread
{
    private readonly Mutability mutability;
    private readonly StarlarkSemantics semantics;
    private readonly bool allowRecursion;

    private readonly Dictionary<Type, object> threadLocals = new();

    // Stack of active function calls.
    private readonly List<Frame> callstack = new();

    private PrintHandler printHandler = DefaultPrintHandler;
    private Loader? loader;
    private Func<string> uncheckedExceptionContext = () => "";

    internal long steps; // count of logical computation steps executed so far
    internal long stepLimit = long.MaxValue;

    /// <summary>A hook for notifications of assignments at top level.</summary>
    internal PostAssignHook? postAssignHook;

    private StarlarkThread(Mutability mu, StarlarkSemantics semantics, string contextDescription)
    {
        if (mu.IsFrozen)
        {
            throw new ArgumentException("mutability must not be frozen");
        }
        mutability = mu;
        this.semantics = semantics;
        allowRecursion = semantics.GetBool(StarlarkSemantics.ALLOW_RECURSION);
        if (!string.IsNullOrEmpty(contextDescription))
        {
            uncheckedExceptionContext = () => contextDescription;
        }
    }

    /// <summary>Creates a StarlarkThread.</summary>
    public static StarlarkThread Create(
        Mutability mu, StarlarkSemantics semantics, string contextDescription = "") =>
        new(mu, semantics, contextDescription);

    /// <summary>Creates a StarlarkThread with an empty context description.</summary>
    public static StarlarkThread CreateTransient(Mutability mu, StarlarkSemantics semantics) =>
        new(mu, semantics, "");

    /// <summary>Returns the number of Starlark computation steps executed by this thread.</summary>
    public long GetExecutedSteps() => steps;

    public void IncrementExecutedSteps(long delta) => steps += delta;

    public void SetMaxExecutionSteps(long steps) => stepLimit = steps;

    public long GetMaxExecutionSteps() => stepLimit;

    /// <summary>Saves a thread-local value keyed by type.</summary>
    public void SetThreadLocal<T>(T value) where T : notnull => threadLocals[typeof(T)] = value;

    /// <summary>Returns the most recently set thread-local for the key type, or default.</summary>
    public T? GetThreadLocal<T>() =>
        threadLocals.TryGetValue(typeof(T), out object? v) ? (T)v : default;

    /// <summary>Returns the mutability for values created by this thread.</summary>
    public Mutability Mutability => mutability;

    public StarlarkSemantics GetSemantics() => semantics;

    /// <summary>Reports whether this thread is allowed to make recursive calls.</summary>
    internal bool IsRecursionAllowed() => allowRecursion;

    /// <summary>A Frame records information about an active function call.</summary>
    internal sealed class Frame
    {
        internal readonly IStarlarkCallable Fn;
        internal Location Loc;
        internal bool ErrorLocationSet;
        internal object? Result = Starlark.None;
        internal object?[]? Locals;

        internal Frame(IStarlarkCallable fn)
        {
            Fn = fn;
            Loc = fn.Location;
        }

        internal void SetLocation(Location loc) => Loc = loc;

        internal void SetErrorLocation(Location loc)
        {
            if (!ErrorLocationSet)
            {
                ErrorLocationSet = true;
                Loc = loc;
            }
        }

        public override string ToString() => Fn.Name + "@" + Loc;
    }

    /// <summary>Pushes a function onto the call stack.</summary>
    internal void Push(IStarlarkCallable fn) => callstack.Add(new Frame(fn));

    /// <summary>Pops a function off the call stack.</summary>
    internal void Pop() => callstack.RemoveAt(callstack.Count - 1);

    internal Frame FrameAt(int depth) => callstack[callstack.Count - 1 - depth];

    /// <summary>Returns the callable of the frame at the given depth (0 == innermost).</summary>
    internal IStarlarkCallable FrameFnAt(int depth) => FrameAt(depth).Fn;

    private bool Toplevel => callstack.Count < 2;

    /// <summary>Returns the location of the program counter in the enclosing call frame.</summary>
    public Location GetCallerLocation() => Toplevel ? Location.BUILTIN : FrameAt(1).Loc;

    /// <summary>
    /// Returns the <paramref name="depth"/>-th innermost enclosing Starlark function on the call
    /// stack, or null if the number of active calls that are Starlark-defined functions is less than
    /// or equal to <paramref name="depth"/>.
    /// </summary>
    internal StarlarkFunction? GetInnermostEnclosingStarlarkFunction(int depth)
    {
        Copybara.Common.Preconditions.CheckArgument(depth >= 0);
        for (int i = callstack.Count - 1; i >= 0; i--)
        {
            if (callstack[i].Fn is StarlarkFunction fn)
            {
                if (depth == 0)
                {
                    return fn;
                }
                depth--;
            }
        }
        return null;
    }

    /// <summary>Returns the size of the callstack.</summary>
    internal int GetCallStackSize() => callstack.Count;

    /// <summary>Determines how a Starlark thread deals with print statements.</summary>
    public delegate void PrintHandler(StarlarkThread thread, string msg);

    internal PrintHandler GetPrintHandler() => printHandler;

    /// <summary>Sets the behavior of Starlark print statements executed by this thread.</summary>
    public void SetPrintHandler(PrintHandler h) =>
        printHandler = h ?? throw new ArgumentNullException(nameof(h));

    private static void DefaultPrintHandler(StarlarkThread thread, string msg) =>
        Console.Error.WriteLine(thread.GetCallerLocation() + ": " + msg);

    /// <summary>Determines the behavior of load statements. Returns the named module, or null.</summary>
    public delegate Module? Loader(string module);

    internal Loader? GetLoader() => loader;

    public void SetLoader(Loader loader) =>
        this.loader = loader ?? throw new ArgumentNullException(nameof(loader));

    public void SetUncheckedExceptionContext(Func<string> context) =>
        uncheckedExceptionContext = context ?? throw new ArgumentNullException(nameof(context));

    public string GetContextDescription() => uncheckedExceptionContext();

    /// <summary>Specifies a hook function to be run after each assignment at top level.</summary>
    public void SetPostAssignHook(PostAssignHook hook) => postAssignHook = hook;

    /// <summary>A hook for notifications of assignments at top level.</summary>
    public delegate void PostAssignHook(string name, Location nameStartLocation, object value);

    /// <summary>The name for the implicit function that executes a file's top-level statements.</summary>
    public const string TOP_LEVEL = "<toplevel>";

    /// <summary>Creates a new CallStackEntry.</summary>
    public static CallStackEntry NewCallStackEntry(string name, Location location) =>
        new(name, location);

    /// <summary>Describes the name and PC location of an active function call.</summary>
    public sealed class CallStackEntry
    {
        public string Name { get; }
        public Location Location { get; }

        internal CallStackEntry(string name, Location location)
        {
            Name = name ?? throw new ArgumentNullException(nameof(name));
            Location = location ?? throw new ArgumentNullException(nameof(location));
        }

        public override string ToString() => Name + "@" + Location;

        public override int GetHashCode() => 31 * Name.GetHashCode() + Location.GetHashCode();

        public override bool Equals(object? o) =>
            o is CallStackEntry that && Name == that.Name && Location.Equals(that.Location);
    }

    /// <summary>Returns this thread's current stack of active function calls, outermost first.</summary>
    public ImmutableArray<CallStackEntry> GetCallStack()
    {
        var stack = ImmutableArray.CreateBuilder<CallStackEntry>(callstack.Count);
        foreach (Frame fr in callstack)
        {
            stack.Add(NewCallStackEntry(fr.Fn.Name, fr.Loc));
        }
        return stack.ToImmutable();
    }

    public override int GetHashCode() => throw new NotSupportedException(); // avoid nondeterminism

    public override bool Equals(object? that) => throw new NotSupportedException();

    public override string ToString() => $"<StarlarkThread{mutability}>";
}
