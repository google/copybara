// Copyright 2015 The Bazel Authors. All rights reserved.
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

using System.Runtime.CompilerServices;

namespace Starlark.Eval;

/// <summary>
/// An object that manages the capability to mutate Starlark objects and their
/// <see cref="StarlarkThread"/>s. Port of <c>net.starlark.java.eval.Mutability</c>.
///
/// <para>Once a thread is done evaluating, its Mutability is irreversibly closed ("frozen"). At that
/// point it is no longer possible to change the state of its objects.</para>
/// </summary>
public sealed class Mutability : IDisposable
{
    // Maps each temporarily frozen Freezable value to the (positive) count of active iterators over
    // the value. Null once permanently frozen.
    private Dictionary<IFreezable, int>? iteratorCount = new(ReferenceEqualityComparer.Instance);

    private readonly object?[] annotation;

    private readonly bool allowsUnsafeShallowFreeze;

    private Mutability(object?[] annotation, bool allowsUnsafeShallowFreeze)
    {
        this.annotation = annotation;
        this.allowsUnsafeShallowFreeze = allowsUnsafeShallowFreeze;
    }

    /// <summary>Creates a Mutability.</summary>
    public static Mutability Create(params object?[] annotation) => new(annotation, false);

    /// <summary>Creates a Mutability whose objects can be individually frozen.</summary>
    public static Mutability CreateAllowingShallowFreeze(params object?[] annotation) =>
        new(annotation, true);

    /// <summary>Returns the Mutability's "annotation", an internal name describing its purpose.</summary>
    public string GetAnnotation() => string.Join(" ", annotation.Select(a => a?.ToString() ?? "null"));

    public override string ToString() =>
        (IsFrozen ? "(" : "[") + GetAnnotation() + (IsFrozen ? ")" : "]");

    public bool IsFrozen => iteratorCount == null;

    // Defines the default behavior of mutable Freezable sequence values.
    internal bool UpdateIteratorCount(IFreezable x, int delta)
    {
        if (IsFrozen)
        {
            return false;
        }
        iteratorCount!.TryGetValue(x, out int i);
        if (delta > 0)
        {
            i++;
            iteratorCount[x] = i;
        }
        else if (delta < 0)
        {
            i--;
            if (i == 0)
            {
                iteratorCount.Remove(x);
            }
            else if (i > 0)
            {
                iteratorCount[x] = i;
            }
            else
            {
                throw new InvalidOperationException("zero value in iteratorCount");
            }
        }
        return i > 0;
    }

    /// <summary>Freezes this Mutability, rendering all Freezables that refer to it immutable.</summary>
    public Mutability Freeze()
    {
        iteratorCount = null;
        return this;
    }

    public void Dispose() => Freeze();

    /// <summary>Whether Freezables having this Mutability allow the unsafe shallow freeze operation.</summary>
    public bool AllowsUnsafeShallowFreeze => allowsUnsafeShallowFreeze;

    /// <summary>Throws if the precondition for unsafeShallowFreeze is violated.</summary>
    public static void CheckUnsafeShallowFreezePrecondition(IFreezable freezable)
    {
        Mutability mutability = freezable.Mutability;
        if (mutability.IsFrozen)
        {
            throw new ArgumentException(
                "cannot call UnsafeShallowFreeze() on an object whose Mutability is already frozen");
        }
        if (!mutability.AllowsUnsafeShallowFreeze)
        {
            throw new ArgumentException(
                "cannot call UnsafeShallowFreeze() on a mutable object whose Mutability's "
                    + "AllowsUnsafeShallowFreeze == false");
        }
    }

    /// <summary>A Mutability indicating that a value is deeply immutable.</summary>
    public static readonly Mutability IMMUTABLE = Create("IMMUTABLE").Freeze();
}

/// <summary>
/// An object that refers to a <see cref="Mutability"/> to decide whether to allow mutation. Port of
/// <c>net.starlark.java.eval.Mutability.Freezable</c>.
/// </summary>
public interface IFreezable
{
    /// <summary>Returns the Mutability associated with this Freezable.</summary>
    Mutability Mutability { get; }

    /// <summary>
    /// Registers a change to this Freezable's iterator count and reports whether it is temporarily
    /// immutable.
    /// </summary>
    bool UpdateIteratorCount(int delta) => Mutability.UpdateIteratorCount(this, delta);

    /// <summary>Freezes this object (and not its contents). Use with care. Optional operation.</summary>
    void UnsafeShallowFreeze() => throw new NotSupportedException();
}
