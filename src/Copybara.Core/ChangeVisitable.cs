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
using Copybara.Exceptions;
using Copybara.Revision;

namespace Copybara;

/// <summary>The result type for the function passed to visitChanges.</summary>
public enum VisitResult
{
    /// <summary>
    /// Continue. If more changes are available for visiting, the origin will call again the function
    /// with the next changes.
    /// </summary>
    Continue,

    /// <summary>
    /// Stop. Origin will not pass more changes to the visitor function. Usually used because the
    /// function found what it was looking for (for example a commit with a label).
    /// </summary>
    Terminate,
}

/// <summary>
/// A visitor of changes. An implementation of this interface is provided to <c>visitChanges</c>
/// methods to visit changes in Origin or Destination history.
/// </summary>
public interface IChangesVisitor
{
    /// <summary>
    /// Invoked for each change found. The implementation can choose to cancel the visitation by
    /// returning <see cref="VisitResult.Terminate"/>.
    /// </summary>
    VisitResult Visit(Change<IRevision> input);
}

/// <summary>A visitor of changes that only receives changes that match any of the passed labels.</summary>
public interface IChangesLabelVisitor
{
    /// <summary>
    /// Invoked for each change found that matches the labels.
    ///
    /// <para>Note that the <paramref name="matchedLabels"/> can be disjoint with the labels in
    /// <paramref name="input"/>, since labels might be stored with a different string format.</para>
    /// </summary>
    VisitResult Visit(Change<IRevision> input, IReadOnlyDictionary<string, string> matchedLabels);
}

/// <summary>
/// An interface stating that the implementing class accepts child visitors to explore repository
/// state beyond the changes being migrated.
/// </summary>
/// <typeparam name="R">the revision type.</typeparam>
public interface IChangeVisitable<R>
    where R : class, IRevision
{
    /// <summary>
    /// Visit the parents of the <paramref name="start"/> revision and call the visitor for each
    /// change. The visitor can stop the stream of changes at any moment by returning
    /// <see cref="VisitResult.Terminate"/>.
    ///
    /// <para>It is up to the Origin how and what changes it provides to the function.</para>
    /// </summary>
    void VisitChanges(R? start, IChangesVisitor visitor);

    /// <summary>Visit only changes that contain any of the labels in <paramref name="labels"/>.</summary>
    void VisitChangesWithAnyLabel(
        R? start, IReadOnlyCollection<string> labels, IChangesLabelVisitor visitor)
    {
        VisitChanges(start, new AnyLabelAdapter(labels, visitor));
    }

    private sealed class AnyLabelAdapter : IChangesVisitor
    {
        private readonly IReadOnlyCollection<string> _labels;
        private readonly IChangesLabelVisitor _visitor;

        public AnyLabelAdapter(IReadOnlyCollection<string> labels, IChangesLabelVisitor visitor)
        {
            _labels = labels;
            _visitor = visitor;
        }

        public VisitResult Visit(Change<IRevision> input)
        {
            // We could return all the label values, but this is really only used for RevId-like ones
            // and last is good enough for now.
            var labels = input.GetLabels();
            var copy = ImmutableDictionary.CreateBuilder<string, string>();
            foreach (var key in labels.Keys)
            {
                if (_labels.Contains(key))
                {
                    var values = labels.Get(key);
                    copy[key] = values[values.Length - 1];
                }
            }
            if (copy.Count == 0)
            {
                return VisitResult.Continue;
            }
            return _visitor.Visit(input, copy.ToImmutable());
        }
    }
}
