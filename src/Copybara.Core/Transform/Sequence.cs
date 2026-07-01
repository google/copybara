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
using Copybara.Common;
using Copybara.Profiler;
using Starlark.Eval;

namespace Copybara.Transform;

/// <summary>A transformation that runs a sequence of delegate transformations.</summary>
public class Sequence : ITransformation
{
    private readonly string? _name;
    private readonly Profiler.Profiler _profiler;
    private readonly WorkflowOptions _workflowOptions;
    private readonly ImmutableArray<ITransformation> _sequence;
    private readonly NoopBehavior _noopBehavior;

    internal Sequence(
        Profiler.Profiler profiler,
        string? name,
        WorkflowOptions workflowOptions,
        ImmutableArray<ITransformation> sequence,
        NoopBehavior noopBehavior)
    {
        _profiler = Preconditions.CheckNotNull(profiler);
        _name = name;
        _workflowOptions = workflowOptions;
        _sequence = sequence;
        _noopBehavior = noopBehavior;
    }

    public TransformationStatus Transform(TransformWork work)
    {
        var transformationList = GetTransformations();

        bool someTransformWasSuccess = false;

        if (_name != null)
        {
            if (_workflowOptions.SkipTransforms.Contains(_name))
            {
                work.GetConsole().WarnFmt("Skipping transform block {0}", _name);
                return TransformationStatus.Success();
            }
            work.GetConsole().ProgressFmt("Running transform block {0}", _name);
        }
        for (int i = 0; i < transformationList.Count; i++)
        {
            // Only check the cache in between consecutive Transforms.
            if (i != 0)
            {
                work.ValidateTreeStateCache();
            }

            ITransformation transformation = transformationList[i];
            work.GetConsole().Progress(
                GetTransformMessage(transformation, i, transformationList.Count));
            TransformationStatus status = RunOneTransform(work, transformation);

            if (status.IsNoop())
            {
                if (_noopBehavior == NoopBehavior.FAIL_IF_ANY_NOOP)
                {
                    status.ThrowException(work.GetConsole(), _workflowOptions.IgnoreNoop);
                }
                else if (_noopBehavior == NoopBehavior.NOOP_IF_ANY_NOOP)
                {
                    if (_workflowOptions.IgnoreNoop)
                    {
                        status.Warn(work.GetConsole());
                    }
                    else
                    {
                        return status;
                    }
                }
                else if (work.GetConsole().IsVerbose)
                {
                    status.Warn(work.GetConsole());
                }
            }

            someTransformWasSuccess |= status.IsSuccess();
        }

        if (_noopBehavior == NoopBehavior.NOOP_IF_ALL_NOOP && !someTransformWasSuccess)
        {
            return TransformationStatus.Noop(
                $"{this} was a no-op because all wrapped transforms were no-ops");
        }

        return TransformationStatus.Success();
    }

    private string GetTransformMessage(
        ITransformation transform, int currentTransformIndex, int transformListSize)
    {
        string transformMsg = transform.Describe();
        if (transformListSize > 1)
        {
            transformMsg = _name != null
                ? string.Format(
                    "[{0,2}/{1}] Transform block {2} - {3}",
                    currentTransformIndex + 1, transformListSize, _name, transformMsg)
                : string.Format(
                    "[{0,2}/{1}] Transform {2}",
                    currentTransformIndex + 1, transformListSize, transformMsg);
        }
        return transformMsg;
    }

    private IReadOnlyList<ITransformation> GetTransformations()
    {
        if (!_workflowOptions.JoinTransformations())
        {
            return _sequence;
        }
        var result = new List<ITransformation>(_sequence.Length);
        ITransformation? prev = null;
        foreach (ITransformation transformation in _sequence)
        {
            if (prev != null && prev.CanJoin(transformation))
            {
                prev = prev.Join(transformation);
            }
            else
            {
                if (prev != null)
                {
                    result.Add(prev);
                }
                prev = transformation;
            }
        }
        if (prev != null)
        {
            result.Add(prev);
        }
        return result;
    }

    private TransformationStatus RunOneTransform(TransformWork work, ITransformation transform)
    {
        using (_profiler.Start(transform.Describe().Replace('/', ' ')))
        {
            return transform.Transform(work);
        }
    }

    public ITransformation Reverse()
    {
        var list = ImmutableArray.CreateBuilder<ITransformation>(_sequence.Length);
        foreach (ITransformation element in _sequence)
        {
            list.Add(element.Reverse());
        }
        var reversed = list.ToImmutable();
        return new Sequence(
            _profiler, _name, _workflowOptions,
            ImmutableArray.CreateRange(reversed.Reverse()), _noopBehavior);
    }

    public IReadOnlyList<ITransformation> GetSequence() => _sequence;

    /// <summary>returns a string like "Sequence foobar: [a, b, c]".</summary>
    public override string ToString() =>
        string.Format(
            "Sequence{0}: [{1}]",
            _name != null ? " " + _name : "",
            string.Join(", ", _sequence));

    public string Describe() => "sequence";

    /// <summary>
    /// Create a sequence from a list of native and Skylark transforms.
    /// </summary>
    /// <param name="description">a description of the argument being converted, such as its name.</param>
    public static Sequence FromConfig(
        Profiler.Profiler profiler,
        string? name,
        WorkflowOptions workflowOptions,
        IEnumerable<object?> elements,
        string description,
        StarlarkThread.PrintHandler printHandler,
        Func<ITransformation, ITransformation> transformWrapper,
        NoopBehavior noopBehavior)
    {
        var transformations = ImmutableArray.CreateBuilder<ITransformation>();
        foreach (object? element in elements)
        {
            transformations.Add(
                transformWrapper(
                    Transformations.ToTransformation(element, description, printHandler)));
        }
        return new Sequence(
            profiler, name, workflowOptions, transformations.ToImmutable(), noopBehavior);
    }

    /// <summary>
    /// An enum to specify how a <see cref="Sequence"/> should handle a no-op occurring in one of its
    /// child <see cref="ITransformation"/>s.
    /// </summary>
    public enum NoopBehavior
    {
        /// <summary>
        /// No matter how many of the wrapped Transformation no-op, this Sequence is always considered
        /// to be a successful op.
        /// </summary>
        IGNORE_NOOP,

        /// <summary>
        /// If at least 1 of the wrapped transformations is a no-op, this Sequence will also be
        /// considered a no-op. The remainder of the wrapped transformations will not be run.
        /// </summary>
        NOOP_IF_ANY_NOOP,

        /// <summary>
        /// This Sequence will run all of the wrapped transformations. If all of them are no-ops
        /// (including the case where the transformation list is empty), this Sequence is considered
        /// to be a no-op.
        /// </summary>
        NOOP_IF_ALL_NOOP,

        /// <summary>
        /// If at least 1 of the wrapped transformation is a no-op, this Sequence will fail
        /// immediately, even if another Sequence with <see cref="IGNORE_NOOP"/> is wrapping this one.
        /// </summary>
        FAIL_IF_ANY_NOOP,
    }
}
