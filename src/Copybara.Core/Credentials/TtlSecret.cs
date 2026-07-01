/*
 * Copyright (C) 2023 Google LLC.
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

namespace Copybara.Credentials;

/// <summary>A credential with a limited TTL.</summary>
public class TtlSecret : StaticSecret
{
    private readonly DateTimeOffset _ttl;
    private readonly Func<DateTimeOffset> _clock;

    public TtlSecret(string secret, string name, DateTimeOffset ttl, Func<DateTimeOffset> clock)
        : base(name, secret)
    {
        _ttl = ttl;
        _clock = Preconditions.CheckNotNull(clock);
    }

    public override string PrintableValue() =>
        $"<static secret name {Name} with expiration {_ttl}>";

    public override string ProvideSecret()
    {
        DateTimeOffset now = _clock();
        if (_ttl < now)
        {
            throw new CredentialRetrievalException(
                string.Format(
                    "Credential {0} expired {1} seconds ago.",
                    PrintableValue(), (long)(now - _ttl).TotalSeconds));
        }

        return base.ProvideSecret();
    }

    public override bool Valid() => _ttl > _clock().AddSeconds(/* 10s grace */ 10);

    public override string ToString() => PrintableValue();
}
