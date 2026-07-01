/*
 * Copyright (C) 2022 Google Inc.
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

using Console = Copybara.Util.Console.Console;

namespace Copybara.Version;

/// <summary>
/// Given a requested reference, it returns that reference if it is an exact match with one of the
/// versions from <see cref="IVersionList"/>.
/// </summary>
public class RequestedExactMatchSelector : IVersionSelector
{
    public string? Select(IVersionList versionList, string? requestedRef, Console console)
    {
        if (requestedRef != null && versionList.List().Contains(requestedRef))
        {
            return requestedRef;
        }
        return null;
    }

    public override string ToString() => nameof(RequestedExactMatchSelector);
}
