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

using System.Text.Json.Serialization;
using Starlark.Eval;

namespace Copybara.Git.GerritApi;

/// <summary>
/// See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#abandon-input
///
/// <para>NotifyInfo (notify_details) not included for now.</para>
/// </summary>
public class AbandonInput : IStarlarkPrintableValue
{
    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("notify")]
    public string? Notify { get; set; }

    private AbandonInput(string? message, NotifyType? notify)
    {
        Message = message;
        Notify = notify?.ToWireValue();
    }

    public static AbandonInput Create(string? message, NotifyType? notify) =>
        new(message, notify);

    public static AbandonInput CreateWithoutComment() => new(null, null);

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() => $"AbandonInput{{message={Message}, notify={Notify}}}";
}
