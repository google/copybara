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
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.TemplateToken;
using Copybara.Transform;
using Starlark.Eval;
using Starlark.Syntax;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Transform.Metadata;

/// <summary>Adjusts textual references in change messages to match the destination.</summary>
public class ReferenceMigrator : ITransformation
{
    internal const int MaxChangesToVisit = 5000;

    private readonly RegexTemplateTokens _before;
    private readonly RegexTemplateTokens _after;
    private readonly ImmutableArray<string> _additionalLabels;
    private readonly Regex? _reversePattern;
    private readonly Location _location;

    private readonly Dictionary<string, string> _knownChanges = new();

    internal ReferenceMigrator(
        RegexTemplateTokens before,
        RegexTemplateTokens after,
        Regex? reversePattern,
        ImmutableArray<string> additionalLabels,
        Location location)
    {
        _before = Preconditions.CheckNotNull(before);
        _after = Preconditions.CheckNotNull(after);
        _additionalLabels = additionalLabels;
        _reversePattern = reversePattern;
        _location = Preconditions.CheckNotNull(location);
    }

    public static ReferenceMigrator Create(
        string before,
        string after,
        Regex forward,
        Regex? backward,
        ImmutableArray<string> additionalLabels,
        Location location)
    {
        var patterns = new Dictionary<string, Regex> { ["reference"] = forward };
        var beforeTokens = new RegexTemplateTokens(before, patterns, repeatedGroups: false, location);
        beforeTokens.ValidateUnused();
        var afterTokens = new RegexTemplateTokens(after, patterns, repeatedGroups: false, location);
        afterTokens.ValidateUnused();
        if (after.LastIndexOf("$1", StringComparison.Ordinal) != -1)
        {
            throw StarlarkRt.Errorf(
                "Destination format '{0}' uses the reserved token '$1'.", after);
        }
        return new ReferenceMigrator(beforeTokens, afterTokens, backward, additionalLabels, location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        ValidationException? thrown = null;
        var replacer = _before.CallbackReplacer(
            _after,
            new Callback(this, work, () => thrown, ex => thrown ??= ex),
            firstOnly: false,
            multiline: false,
            patternsToIgnore: null);
        string replaced = replacer.Replace(work.GetMessage());
        if (thrown != null)
        {
            throw thrown;
        }
        if (!replaced.Equals(work.GetMessage()))
        {
            work.SetMessage(replaced);
        }
        return TransformationStatus.Success();
    }

    private sealed class Callback : RegexTemplateTokens.IAlterAfterTemplate
    {
        private readonly ReferenceMigrator _owner;
        private readonly TransformWork _work;
        private readonly Func<ValidationException?> _getThrown;
        private readonly Action<ValidationException> _setThrown;

        public Callback(
            ReferenceMigrator owner,
            TransformWork work,
            Func<ValidationException?> getThrown,
            Action<ValidationException> setThrown)
        {
            _owner = owner;
            _work = work;
            _getThrown = getThrown;
            _setThrown = setThrown;
        }

        public string Alter(IReadOnlyDictionary<int, string> groupValues, string template)
        {
            groupValues.TryGetValue(0, out var whole);
            if (whole != null)
            {
                try
                {
                    groupValues.TryGetValue(1, out var refValue);
                    string? destinationRef = _owner.FindChange(
                        refValue!,
                        _work.GetMigrationInfo().GetOriginLabel(),
                        _work.GetMigrationInfo().DestinationVisitable());
                    if (destinationRef != null)
                    {
                        // This will not work for the case where the template was "foo\\$1", if this
                        // is an issue, a non-naive implementation might be required.
                        return Regex.Replace(template, "[$]1", destinationRef.Replace("$", "$$"));
                    }
                    return whole;
                }
                catch (ValidationException exception)
                {
                    if (_getThrown() == null)
                    {
                        _setThrown(exception);
                    }
                    return whole;
                }
            }
            return template;
        }
    }

    public ITransformation Reverse() =>
        new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() => "map_references: " + _before + " to " + _after;

    private string? FindChange(
        string refBeingMigrated,
        string originLabel,
        IChangeVisitable<IRevision>? destinationReader)
    {
        int changesVisited = 0;
        var originLabels = new List<string> { originLabel };
        originLabels.AddRange(_additionalLabels);
        ValidationException.CheckCondition(
            destinationReader != null, "Destination does not support reading change history.");
        if (_knownChanges.TryGetValue(refBeingMigrated, out var known))
        {
            return known;
        }
        try
        {
            destinationReader!.VisitChangesWithAnyLabel(
                null,
                originLabels,
                new LabelVisitor(this, refBeingMigrated, () => ++changesVisited));
            _knownChanges.TryGetValue(refBeingMigrated, out var retVal);
            if (_reversePattern != null && retVal != null && !IsFullMatch(_reversePattern, retVal))
            {
                throw new ValidationException(
                    $"Reference {retVal} does not match regex '{_reversePattern}'");
            }
            return retVal;
        }
        catch (RepoException exception)
        {
            throw new ValidationException("Exception finding reference.", exception);
        }
    }

    private static bool IsFullMatch(Regex regex, string input)
    {
        var m = regex.Match(input);
        return m.Success && m.Index == 0 && m.Length == input.Length;
    }

    private sealed class LabelVisitor : IChangesLabelVisitor
    {
        private readonly ReferenceMigrator _owner;
        private readonly string _refBeingMigrated;
        private readonly Func<int> _incrementVisited;

        public LabelVisitor(
            ReferenceMigrator owner, string refBeingMigrated, Func<int> incrementVisited)
        {
            _owner = owner;
            _refBeingMigrated = refBeingMigrated;
            _incrementVisited = incrementVisited;
        }

        public VisitResult Visit(
            Change<IRevision> input, IReadOnlyDictionary<string, string> matchedLabels)
        {
            foreach (var labelValue in matchedLabels.Values)
            {
                if (!_owner._knownChanges.ContainsKey(labelValue))
                {
                    _owner._knownChanges[labelValue] = input.Ref;
                }
                if (labelValue.Equals(_refBeingMigrated))
                {
                    return VisitResult.Terminate;
                }
            }
            return _incrementVisited() > MaxChangesToVisit
                ? VisitResult.Terminate
                : VisitResult.Continue;
        }
    }

    public override string ToString() => $"ReferenceMigrator{{before={_before}, after={_after}}}";

    public override bool Equals(object? other) =>
        other is ReferenceMigrator o
        && Equals(_before, o._before)
        && Equals(_after, o._after);

    public Location Location() => _location;

    public override int GetHashCode() => HashCode.Combine(_before, _after);
}
