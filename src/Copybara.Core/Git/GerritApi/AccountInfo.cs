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
using System.Globalization;
using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GerritApi;

/// <summary>https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-info</summary>
[StarlarkBuiltin("gerritapi.AccountInfo", Doc = "Gerrit account information.")]
public class AccountInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("_account_id")]
    public long AccountId { get; set; }

    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("email")]
    public string? Email { get; set; }

    [JsonPropertyName("secondary_emails")]
    public IReadOnlyList<string>? SecondaryEmails { get; set; }

    [JsonPropertyName("username")]
    public string? Username { get; set; }

    public AccountInfo()
    {
    }

    public AccountInfo(long accountId, string? email)
    {
        AccountId = accountId;
        Email = email;
    }

    public long GetAccountId() => AccountId;

    [StarlarkMethod(
        "account_id",
        Doc = "The numeric ID of the account.",
        StructField = true)]
    public string GetAccountIdAsString() => AccountId.ToString(CultureInfo.InvariantCulture);

    [StarlarkMethod(
        "name",
        Doc =
            "The full name of the user.\n"
            + "Only set if detailed account information is requested.\n"
            + "See option DETAILED_ACCOUNTS for change queries\n"
            + "and option DETAILS for account queries.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetName() => Name;

    [StarlarkMethod(
        "email",
        Doc =
            "The email address the user prefers to be contacted through.\n"
            + "Only set if detailed account information is requested.\n"
            + "See option DETAILED_ACCOUNTS for change queries\n"
            + "and options DETAILS and ALL_EMAILS for account queries.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetEmail() => Email;

    [StarlarkMethod(
        "secondary_emails",
        Doc =
            "A list of the secondary email addresses of the user.\n"
            + "Only set for account queries when the ALL_EMAILS option or the suggest "
            + "parameter is set.\n"
            + "Secondary emails are only included if the calling user has the Modify Account, "
            + "and hence is allowed to see secondary emails of other users.",
        StructField = true)]
    public IReadOnlyList<string> GetSecondaryEmails() =>
        SecondaryEmails is null ? ImmutableArray<string>.Empty : SecondaryEmails.ToImmutableArray();

    [StarlarkMethod(
        "username",
        Doc =
            "The username of the user.\n"
            + "Only set if detailed account information is requested.\n"
            + "See option DETAILED_ACCOUNTS for change queries\n"
            + "and option DETAILS for account queries.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetUsername() => Username;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"AccountInfo{{accountId={AccountId}, name={Name}, email={Email}, "
        + $"secondaryEmails={FormatList(SecondaryEmails)}, username={Username}}}";

    private protected static string FormatList<T>(IEnumerable<T>? items) =>
        items is null ? "null" : "[" + string.Join(", ", items) + "]";
}
