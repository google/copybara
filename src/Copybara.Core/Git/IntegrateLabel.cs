/*
 * Copyright (C) 2017 Google Inc.
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

using Copybara.Common;

namespace Copybara.Git;

/// <summary>
/// A label value that describes what to integrate. Port of
/// <c>com.google.copybara.git.IntegrateLabel</c>.
/// </summary>
public interface IIntegrateLabel
{
    /// <summary>Get the merge message.</summary>
    string MergeMessage(IReadOnlyList<LabelFinder> labelsToAdd);

    /// <summary>Get the revision to integrate.</summary>
    GitRevision GetRevision();

    static IIntegrateLabel GenericGitRevision(GitRevision revision)
    {
        Preconditions.CheckNotNull(revision);
        return new GenericGitRevisionLabel(revision);
    }

    static string WithLabels(string msg, IReadOnlyList<LabelFinder> labelsToAdd)
    {
        var result = ChangeMessage.ParseMessage(msg);
        foreach (var labelFinder in labelsToAdd)
        {
            result = result.WithLabel(
                labelFinder.GetName(), labelFinder.GetSeparator(), labelFinder.GetValue());
        }
        return result.ToString();
    }

    private sealed class GenericGitRevisionLabel : IIntegrateLabel
    {
        private readonly GitRevision _revision;

        public GenericGitRevisionLabel(GitRevision revision) => _revision = revision;

        public string MergeMessage(IReadOnlyList<LabelFinder> labelsToAdd) =>
            IIntegrateLabel.WithLabels("Merge of " + _revision.GetHash(), labelsToAdd);

        public GitRevision GetRevision() => _revision;

        public override string ToString() => "Merge of " + _revision.GetHash();
    }
}
