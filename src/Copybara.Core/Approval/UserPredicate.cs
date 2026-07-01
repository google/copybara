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

namespace Copybara.Approval;

/// <summary>
/// Defines a predicate over a user action. Port of
/// <c>com.google.copybara.approval.UserPredicate</c>.
/// </summary>
public class UserPredicate : StatementPredicate
{
    private readonly string _username;
    private readonly UserPredicateType _type;

    public UserPredicate(
        string username, UserPredicateType userType, string originUrl, string description)
        : base(userType.ToString(), description, originUrl)
    {
        _username = username;
        _type = userType;
    }

    /// <summary>
    /// String representing the username for the user predicate (e.g. the username of the approver,
    /// owners, etc.).
    /// </summary>
    public string Username() => _username;

    public UserPredicateType UserType() => _type;

    /// <summary>Type of user predicate.</summary>
    public enum UserPredicateType
    {
        /// <summary>username is the owner of the change.</summary>
        OWNER,

        /// <summary>
        /// username has approved the change. Called LGTM for historical reasons (used internally).
        /// </summary>
        LGTM,
    }

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is not UserPredicate that)
        {
            return false;
        }
        if (!base.Equals(o))
        {
            return false;
        }
        return string.Equals(_username, that._username) && _type == that._type;
    }

    public override int GetHashCode() => HashCode.Combine(base.GetHashCode(), _username, _type);

    protected override string ToStringDescription() =>
        base.ToStringDescription() + $" username={_username}, type={_type}";
}
