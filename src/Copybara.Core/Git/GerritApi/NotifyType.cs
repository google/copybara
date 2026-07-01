/*
 * Copyright (C) 2018 Google Inc.
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

namespace Copybara.Git.GerritApi;

/// <summary>Type of notification to send when abandoning/deleting a review or reviewer.</summary>
/// <remarks>
/// NOTE(port): In the Java original, <c>ALL</c> is annotated with <c>@NullValue</c>, meaning it is
/// serialized as <c>null</c> (i.e. omitted). Callers mirror upstream by converting the enum to its
/// wire representation via <see cref="ToWireValue"/> before assigning it to a string field.
/// </remarks>
public enum NotifyType
{
    NONE,
    OWNER,
    OWNER_REVIEWERS,
    ALL,
}

/// <summary>Helpers for translating <see cref="NotifyType"/> to/from Gerrit wire values.</summary>
public static class NotifyTypeExtensions
{
    /// <summary>
    /// Returns the wire representation of the notify type, or <c>null</c> for <see cref="NotifyType.ALL"/>
    /// which upstream annotates with <c>@NullValue</c> (i.e. omitted from the request).
    /// </summary>
    public static string? ToWireValue(this NotifyType notify) =>
        notify == NotifyType.ALL ? null : notify.ToString();
}
