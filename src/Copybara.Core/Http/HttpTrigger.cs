/*
 * Copyright (C) 2023 Google Inc.
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
using Copybara.Exceptions;

namespace Copybara.Http;

/// <summary>HttpTrigger helps working with http origins.</summary>
public class HttpTrigger : ITrigger
{
    private readonly IEndpoint _endpoint;

    public HttpTrigger(IEndpoint endpoint)
    {
        _endpoint = Preconditions.CheckNotNull(endpoint);
    }

    public IEndpoint GetEndpoint() => _endpoint;

    public ImmutableListMultimap<string, string> Describe() => _endpoint.Describe();
}
