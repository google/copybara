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
using Copybara.Common;
using Copybara.Revision;

namespace Copybara;

/// <summary>
/// Represents the information about a Migration.
///
/// <para>A migration can have one or more <see cref="MigrationReference{O}"/>s.</para>
/// </summary>
/// <typeparam name="O">the origin revision type.</typeparam>
public sealed class Info<O>
    where O : class, IRevision
{
    private Info(
        ImmutableListMultimap<string, string> originDescription,
        ImmutableListMultimap<string, string> destinationDescription,
        ImmutableArray<MigrationReference<O>> migrationReferences,
        ImmutableArray<Change<O>> versions)
    {
        OriginDescription = originDescription;
        DestinationDescription = destinationDescription;
        MigrationReferences = migrationReferences;
        Versions = versions;
    }

    public static Info<O> Create(
        ImmutableListMultimap<string, string> originDescription,
        ImmutableListMultimap<string, string> destinationDescription,
        IEnumerable<MigrationReference<O>> migrationReferences) =>
        new(
            originDescription,
            destinationDescription,
            migrationReferences.ToImmutableArray(),
            ImmutableArray<Change<O>>.Empty);

    public static Info<O> Create(
        ImmutableListMultimap<string, string> originDescription,
        ImmutableListMultimap<string, string> destinationDescription,
        IEnumerable<MigrationReference<O>> migrationReferences,
        IReadOnlyList<Change<O>> versions) =>
        new(
            originDescription,
            destinationDescription,
            migrationReferences.ToImmutableArray(),
            versions.ToImmutableArray());

    /// <summary>Returns origin description of the migration.</summary>
    public ImmutableListMultimap<string, string> OriginDescription { get; }

    /// <summary>Returns destination description of the migration.</summary>
    public ImmutableListMultimap<string, string> DestinationDescription { get; }

    /// <summary>Returns information about a migration for one reference (like 'master').</summary>
    public IReadOnlyList<MigrationReference<O>> MigrationReferences { get; }

    /// <summary>Returns a list of the upstream versions for an origin.</summary>
    public IReadOnlyList<Change<O>> Versions { get; }
}

/// <summary>Non-generic holder for <see cref="Info{O}"/>'s empty instance factory.</summary>
public static class Info
{
    public static Info<IRevision> Empty { get; } =
        Info<IRevision>.Create(
            ImmutableListMultimap<string, string>.Empty,
            ImmutableListMultimap<string, string>.Empty,
            Array.Empty<MigrationReference<IRevision>>(),
            Array.Empty<Change<IRevision>>());
}

/// <summary>Information about a migration for one reference (like 'master').</summary>
/// <typeparam name="O">the origin revision type.</typeparam>
public sealed class MigrationReference<O>
    where O : class, IRevision
{
    private readonly string _label;
    private readonly ImmutableArray<Change<O>> _availableToMigrate;

    private MigrationReference(
        string label,
        O? lastMigrated,
        Change<O>? lastMigratedChange,
        ImmutableArray<Change<O>> availableToMigrate,
        Change<O>? lastResolvedChange)
    {
        _label = label;
        LastMigrated = lastMigrated;
        LastMigratedChange = lastMigratedChange;
        _availableToMigrate = availableToMigrate;
        LastResolvedChange = lastResolvedChange;
    }

    public static MigrationReference<O> Create(
        string label,
        O? lastMigrated,
        Change<O>? lastMigratedChange,
        IEnumerable<Change<O>> availableToMigrate) =>
        new(label, lastMigrated, lastMigratedChange, availableToMigrate.ToImmutableArray(), null);

    public static MigrationReference<O> Create(
        string label,
        Change<O>? lastMigratedChange,
        IEnumerable<Change<O>> availableToMigrate) =>
        new(
            label,
            lastMigratedChange?.GetRevision(),
            lastMigratedChange,
            availableToMigrate.ToImmutableArray(),
            null);

    public static MigrationReference<O> Create(
        string label,
        O? lastMigrated,
        Change<O>? lastMigratedChange,
        IEnumerable<Change<O>> availableToMigrate,
        Change<O>? lastResolvedChange) =>
        new(
            label,
            lastMigrated,
            lastMigratedChange,
            availableToMigrate.ToImmutableArray(),
            lastResolvedChange);

    /// <summary>The name of this <see cref="MigrationReference{O}"/>.</summary>
    public string GetLabel() => _label;

    /// <summary>Returns the last migrated revision from the origin, or null if none.</summary>
    public O? LastMigrated { get; }

    /// <summary>Returns the last migrated change from the origin, or null.</summary>
    public Change<O>? LastMigratedChange { get; }

    /// <summary>Returns the last available revision to migrate from the origin, or null.</summary>
    public O? GetLastAvailableToMigrate()
    {
        O? last = null;
        foreach (var c in _availableToMigrate)
        {
            last = c.GetRevision();
        }
        return last;
    }

    /// <summary>Returns a list of the next available changes to migrate from the origin.</summary>
    public IReadOnlyList<Change<O>> GetAvailableToMigrate() => _availableToMigrate;

    /// <summary>Returns the most recent change that was resolved by the migration.</summary>
    public Change<O>? LastResolvedChange { get; }
}
