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

using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;
using FluentAssertions;
using Xunit;

namespace Copybara.Tests;

/// <summary>
/// Smoke tests exercising a slice of each ported foundation module, to prove the
/// port is wired together and behaves like the Java original for basic cases.
/// </summary>
public class SmokeTests
{
    [Fact]
    public void Preconditions_CheckNotNull_ThrowsOnNull()
    {
        var act = () => Preconditions.CheckNotNull<string>(null);
        act.Should().Throw<ArgumentNullException>();
    }

    [Fact]
    public void ValidationException_CheckCondition_FormatsPrintfStyle()
    {
        var act = () => ValidationException.CheckCondition(false, "bad %s value: %s", "foo", 42);
        act.Should().Throw<ValidationException>().WithMessage("bad foo value: 42");
    }

    [Fact]
    public void Author_ParsesAndFormats()
    {
        var author = AuthorParser.Parse("Foo Bar <foo@bar.com>");
        author.Name.Should().Be("Foo Bar");
        author.Email.Should().Be("foo@bar.com");
        author.ToString().Should().Be("Foo Bar <foo@bar.com>");
    }

    [Fact]
    public void Author_EqualityByEmail()
    {
        var a = new Author("Name One", "same@example.com");
        var b = new Author("Name Two", "same@example.com");
        a.Should().Be(b); // authors with the same non-empty email are equal
    }

    [Fact]
    public void ImmutableListMultimap_PreservesInsertionOrder()
    {
        var map = ImmutableListMultimap<string, int>.CreateBuilder()
            .Put("a", 1).Put("a", 2).Put("b", 3).Build();
        map["a"].Should().Equal(1, 2);
        map["b"].Should().Equal(3);
        map.ContainsEntry("a", 2).Should().BeTrue();
    }

    [Theory]
    [InlineData("foo/bar.java", true)]
    [InlineData("foo/deep/nested/bar.java", true)]
    [InlineData("foo/bar.txt", false)]
    public void Glob_IncludeExclude_Matches(string path, bool expected)
    {
        var glob = Glob.CreateGlob(new[] { "foo/**/*.java", "foo/*.java" });
        glob.RelativeTo("").Matches(path).Should().Be(expected);
    }
}
