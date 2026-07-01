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

using Copybara.Common;

namespace Copybara.Util;

/// <summary>
/// A <see cref="CommandOutput"/> that also carries a <see cref="Util.TerminationStatus"/>. Port of
/// <c>com.google.copybara.util.CommandOutputWithStatus</c>.
/// </summary>
public sealed class CommandOutputWithStatus : CommandOutput
{
    public CommandOutputWithStatus(TerminationStatus terminationStatus, byte[] stdout, byte[] stderr)
        : base(stdout, stderr)
    {
        TerminationStatus = Preconditions.CheckNotNull(terminationStatus);
    }

    public TerminationStatus TerminationStatus { get; }

    public TerminationStatus GetTerminationStatus() => TerminationStatus;

    public override string ToString() =>
        $"CommandOutputWithStatus{{{base.ToString()}, terminationStatus={TerminationStatus}}}";
}
