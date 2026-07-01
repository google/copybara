/*
 * Copyright (C) 2016 Google LLC
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
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.TreeState;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>
/// Contains information related to an on-going process of repository transformation.
///
/// <para>This object is passed to the user defined functions in Skylark so that they can personalize
/// the commit message, change the author or run custom transformations.</para>
/// </summary>
[StarlarkBuiltin(
    "TransformWork",
    Doc =
        "Data about the set of changes that are being migrated. It includes information about"
        + " changes like: the author to be used for commit, change message, etc. You receive a"
        + " TransformWork object as an argument when defining a <a"
        + " href='#core.dynamic_transform'><code>dynamic transform</code></a>.")]
public sealed class TransformWork : CheckoutFileSystem, ISkylarkContext<TransformWork>
{
    internal const string CopybaraContextReferenceLabel = "COPYBARA_CONTEXT_REFERENCE";
    internal const string ContextReferenceLabel = "CONTEXT_REFERENCE";
    internal const string CopybaraLastRev = "COPYBARA_LAST_REV";
    internal const string CopybaraCurrentRev = "COPYBARA_CURRENT_REV";
    internal const string CopybaraCurrentRevDateTime = "COPYBARA_CURRENT_REV_DATE_TIME";
    internal const string CopybaraCurrentMessage = "COPYBARA_CURRENT_MESSAGE";
    internal const string CopybaraAuthor = "COPYBARA_AUTHOR";
    internal const string CopybaraCurrentMessageTitle = "COPYBARA_CURRENT_MESSAGE_TITLE";
    internal const string CopybaraConfigPathLabel = "COPYBARA_CONFIG_PATH";
    internal const string CopybaraWorkflowNameLabel = "COPYBARA_WORKFLOW_NAME";

    private Metadata _metadata;
    private readonly Changes _changes;
    private readonly Console _console;
    private readonly MigrationInfo _migrationInfo;
    private readonly IRevision _resolvedReference;
    private readonly TreeState.TreeState _treeState;
    private readonly bool _insideExplicitTransform;
    private readonly IRevision? _lastRev;
    private readonly IRevision? _currentRev;
    private readonly Dict _skylarkTransformParams;
    private readonly LazyResourceLoader<IEndpoint> _originApi;
    private readonly LazyResourceLoader<IEndpoint> _destinationApi;
    private readonly IResourceSupplier<DestinationReader> _destinationReader;
    private readonly IDestinationInfo? _destinationInfo;
    private readonly string _mode;

    public TransformWork(
        string checkoutDir,
        Metadata metadata,
        Changes changes,
        Console console,
        MigrationInfo migrationInfo,
        IRevision resolvedReference,
        LazyResourceLoader<IEndpoint> originApi,
        LazyResourceLoader<IEndpoint> destinationApi,
        IResourceSupplier<DestinationReader> destinationReader,
        string mode)
        : this(
            checkoutDir,
            metadata,
            changes,
            console,
            migrationInfo,
            resolvedReference,
            new TreeState.TreeState(checkoutDir),
            insideExplicitTransform: false,
            lastRev: null,
            currentRev: null,
            Dict.Empty(),
            originApi,
            destinationApi,
            destinationReader,
            destinationInfo: null,
            mode)
    {
    }

    private TransformWork(
        string checkoutDir,
        Metadata metadata,
        Changes changes,
        Console console,
        MigrationInfo migrationInfo,
        IRevision resolvedReference,
        TreeState.TreeState treeState,
        bool insideExplicitTransform,
        IRevision? lastRev,
        IRevision? currentRev,
        Dict skylarkTransformParams,
        LazyResourceLoader<IEndpoint> originApi,
        LazyResourceLoader<IEndpoint> destinationApi,
        IResourceSupplier<DestinationReader> destinationReader,
        IDestinationInfo? destinationInfo,
        string mode)
        : base(checkoutDir)
    {
        _metadata = Preconditions.CheckNotNull(metadata);
        _changes = changes;
        _console = console;
        _migrationInfo = migrationInfo;
        _resolvedReference = Preconditions.CheckNotNull(resolvedReference);
        _treeState = treeState;
        _insideExplicitTransform = insideExplicitTransform;
        _lastRev = lastRev;
        _currentRev = currentRev;
        _skylarkTransformParams = skylarkTransformParams;
        _originApi = Preconditions.CheckNotNull(originApi);
        _destinationApi = Preconditions.CheckNotNull(destinationApi);
        _destinationReader = Preconditions.CheckNotNull(destinationReader);
        _destinationInfo = destinationInfo;
        _mode = mode;
    }

    [StarlarkMethod(name: "message", Doc = "Message to be used in the change", StructField = true)]
    public string GetMessage() => _metadata.GetMessage();

    [StarlarkMethod("mode", Doc = "The workflow mode", StructField = true)]
    public string GetMode() => _mode;

    [StarlarkMethod("author", Doc = "Author to be used in the change", StructField = true)]
    public Author GetAuthor() => _metadata.GetAuthor();

    [StarlarkMethod(
        "params",
        Doc = "Parameters for the function if created with core.dynamic_transform",
        StructField = true)]
    public Dict GetParams() => _skylarkTransformParams;

    [StarlarkMethod("set_message", Doc = "Update the message to be used in the change")]
    public void SetMessage([Param(Name = "message")] string message) =>
        _metadata = _metadata.WithMessage(message);

    public Metadata GetMetadata() => _metadata;

    public void AddHiddenLabels(ImmutableListMultimap<string, string> hiddenLabels) =>
        _metadata = _metadata.WithHiddenLabels(hiddenLabels);

    [StarlarkMethod(
        "run",
        Doc =
            "Run a glob or a transform. For example:<br>"
            + "<code>files = ctx.run(glob(['**.java']))</code><br>or<br>"
            + "<code>ctx.run(core.move(\"foo\", \"bar\"))</code><br>or<br>")]
    public object Run(
        [Param(
            Name = "runnable",
            Doc = "When `runnable` is a `glob`, returns a list of files in the workdir which it"
                + " matches. When `runnable` is a `transformation`, runs it in the workdir.",
            AllowedTypes = new[] { typeof(Glob), typeof(ITransformation) })]
        object runnable)
    {
        if (runnable is Glob glob)
        {
            return List(glob);
        }
        if (runnable is ITransformation transformation)
        {
            // Can never trust the cache when inside a dynamic transform.
            _treeState.ClearCache();
            return transformation.Transform(this);
        }

        throw StarlarkRt.Errorf(
            "Only globs or transforms can be run, but '{0}' is of type {1}",
            runnable, runnable.GetType());
    }

    [StarlarkMethod("success", Doc = "The status returned by a successful Transformation")]
    public TransformationStatus SuccessStatus() => TransformationStatus.Success();

    [StarlarkMethod("noop", Doc = "The status returned by a no-op Transformation")]
    public TransformationStatus NoopStatus([Param(Name = "message")] string message) =>
        TransformationStatus.Noop(message);

    [StarlarkMethod("add_label", Doc = "Add a label to the end of the description")]
    public void AddLabel(
        [Param(Name = "label", Doc = "The label to add")] string label,
        [Param(Name = "value", Doc = "The new value for the label")] string value,
        [Param(Name = "separator", Doc = "The separator to use for the label", DefaultValue = "\"=\"")]
        string separator,
        [Param(
            Name = "hidden",
            Doc = "Don't show the label in the message but only keep it internally",
            Named = true,
            Positional = false,
            DefaultValue = "False")]
        bool hidden)
    {
        if (hidden)
        {
            var builder = ImmutableListMultimap<string, string>.CreateBuilder();
            builder.Put(label, value);
            AddHiddenLabels(builder.Build());
        }
        else
        {
            SetMessage(ChangeMessage.ParseMessage(GetMessage())
                .WithLabel(label, separator, value)
                .ToString());
        }
    }

    [StarlarkMethod(
        "add_or_replace_label",
        Doc = "Replace an existing label or add it to the end of the description")]
    public void AddOrReplaceLabel(
        [Param(Name = "label", Doc = "The label to add/replace")] string label,
        [Param(Name = "value", Doc = "The new value for the label")] string value,
        [Param(Name = "separator", Doc = "The separator to use for the label", DefaultValue = "\"=\"")]
        string separator) =>
        SetMessage(ChangeMessage.ParseMessage(GetMessage())
            .WithNewOrReplacedLabel(label, separator, value)
            .ToString());

    [StarlarkMethod(
        "add_text_before_labels",
        Doc = "Add a text to the description before the labels paragraph")]
    public void AddTextBeforeLabels([Param(Name = "text")] string text)
    {
        var message = ChangeMessage.ParseMessage(GetMessage());
        message = message.WithText(message.GetText() + '\n' + text);
        SetMessage(message.ToString());
    }

    [StarlarkMethod("replace_label", Doc = "Replace a label if it exist in the message")]
    public void ReplaceLabel(
        [Param(Name = "label", Doc = "The label to replace")] string labelName,
        [Param(Name = "value", Doc = "The new value for the label")] string value,
        [Param(Name = "separator", Doc = "The separator to use for the label", DefaultValue = "\"=\"")]
        string separator,
        [Param(
            Name = "whole_message",
            Doc = "By default Copybara only looks in the last paragraph for labels. This flag makes"
                + " it replace labels in the whole message.",
            DefaultValue = "False")]
        bool wholeMessage) =>
        SetMessage(ParseMessage(wholeMessage)
            .WithReplacedLabel(labelName, separator, value)
            .ToString());

    [StarlarkMethod("remove_label", Doc = "Remove a label from the message if present")]
    public void RemoveLabel(
        [Param(Name = "label", Doc = "The label to delete")] string label,
        [Param(
            Name = "whole_message",
            Doc = "By default Copybara only looks in the last paragraph for labels. This flag makes"
                + " it replace labels in the whole message.",
            DefaultValue = "False")]
        bool wholeMessage) =>
        SetMessage(ParseMessage(wholeMessage).WithRemovedLabelByName(label).ToString());

    public void RemoveLabelWithValue(string label, string value, bool wholeMessage) =>
        SetMessage(ParseMessage(wholeMessage).WithRemovedLabelByNameAndValue(label, value).ToString());

    [StarlarkMethod("now_as_string", Doc = "Get current date as a string")]
    public string FormatDate(
        [Param(Name = "format", Doc = "The format to use.", DefaultValue = "\"yyyy-MM-dd\"")]
        string format,
        [Param(Name = "zone", Doc = "The timezone id to use. By default UTC", DefaultValue = "\"UTC\"")]
        string zone)
    {
        TimeZoneInfo tz = zone == "UTC" ? TimeZoneInfo.Utc : TimeZoneInfo.FindSystemTimeZoneById(zone);
        var now = TimeZoneInfo.ConvertTime(DateTimeOffset.UtcNow, tz);
        return now.ToString(JavaToNetDateFormat(format), CultureInfo.InvariantCulture);
    }

    private static string JavaToNetDateFormat(string format) =>
        // Java and .NET share the common y/M/d/H/m/s tokens used in Copybara configs.
        format;

    private ChangeMessage ParseMessage(bool wholeMessage) =>
        wholeMessage
            ? ChangeMessage.ParseAllAsLabels(GetMessage())
            : ChangeMessage.ParseMessage(GetMessage());

    private const string FindLabelDetails =
        "First it looks at the generated message (that is, labels that might have been added by"
        + " previous transformations), then it looks in all the commit messages being imported"
        + " and finally in the resolved reference passed in the CLI.";

    [StarlarkMethod(
        "find_label",
        Doc = "Tries to find a label. " + FindLabelDetails
            + " Returns the first such label value found this way.",
        AllowReturnNones = true)]
    public string? GetLabel([Param(Name = "label", Doc = "The label to find")] string label)
    {
        var labelValues = FindLabelValues(label, all: false);
        return labelValues.Count == 0 ? null : labelValues[labelValues.Count - 1];
    }

    [StarlarkMethod(
        "find_all_labels",
        Doc = "Tries to find all the values for a label. " + FindLabelDetails)]
    public IReadOnlyList<string> GetAllLabels(
        [Param(Name = "label", Doc = "The label to find")] string label) =>
        FindLabelValues(label, all: true);

    [StarlarkMethod(
        "origin_api",
        Doc = "Returns an api handle for the origin repository.")]
    public IEndpoint GetOriginApi() => _originApi.Load(_console);

    [StarlarkMethod(
        "destination_api",
        Doc = "Returns an api handle for the destination repository.")]
    public IEndpoint GetDestinationApi() => _destinationApi.Load(_console);

    [StarlarkMethod(
        "destination_reader",
        Doc = "Returns a handle to read files from the destination, if supported by the destination.")]
    public DestinationReader GetDestinationReader() => _destinationReader.Get();

    [StarlarkMethod(
        "destination_info",
        Doc = "Returns an object to store additional configuration and data for the destination.",
        AllowReturnNones = true)]
    public IDestinationInfo? GetDestinationInfo() => _destinationInfo;

    private IReadOnlyList<string> FindLabelValues(string label, bool all)
    {
        var coreLabels = GetCoreLabels();
        if (coreLabels.TryGetValue(label, out var coreValue))
        {
            return coreValue;
        }
        var result = new List<string>();
        var msgLabel = GetLabelInMessage(label);
        if (msgLabel.Count != 0)
        {
            result.AddRange(msgLabel.Select(l => l.GetValue()));
            if (!all)
            {
                return result;
            }
        }
        var values = _metadata.GetHiddenLabels().Get(label);
        if (values.Length != 0)
        {
            if (!all)
            {
                return new List<string> { values[values.Length - 1] };
            }
            result.AddRange(values);
        }

        // Try to find the label in the current changes migrated.
        var currentChanges = _changes.GetCurrent();
        foreach (var changeObj in currentChanges)
        {
            var change = (Change<IRevision>)changeObj;
            var allForSkylark = change.GetLabelsAllForSkylark();
            if (allForSkylark.TryGetValue(label, out var val))
            {
                result.AddRange(val);
                if (!all)
                {
                    return result;
                }
            }
            var revVal = change.GetRevision().AssociatedLabel(label);
            if (revVal.Count != 0)
            {
                result.AddRange(revVal);
                if (!all)
                {
                    return result;
                }
            }
        }

        if (currentChanges.Count == 0 && _currentRev != null)
        {
            var currentRevLabel = _currentRev.AssociatedLabels().Get(label);
            if (currentRevLabel.Length != 0)
            {
                result.AddRange(currentRevLabel);
                if (!all)
                {
                    return result;
                }
            }
        }

        // Try to find the label in the resolved reference.
        var resolvedRefLabel = _resolvedReference.AssociatedLabels().Get(label);
        if (resolvedRefLabel.Length != 0)
        {
            result.AddRange(resolvedRefLabel);
            if (!all)
            {
                return result;
            }
        }

        return result;
    }

    private IReadOnlyList<LabelFinder> GetLabelInMessage(string name) =>
        ParseMessage(wholeMessage: true).GetLabels().Where(label => label.IsLabel(name)).ToList();

    [StarlarkMethod("set_author", Doc = "Update the author to be used in the change")]
    public void SetAuthor([Param(Name = "author")] Author author) =>
        _metadata = _metadata.WithAuthor(author);

    [StarlarkMethod("changes", Doc = "List of changes that will be migrated", StructField = true)]
    public Changes GetChanges() => _changes;

    [StarlarkMethod(
        "console",
        Doc = "Get an instance of the console to report errors or warnings",
        StructField = true)]
    public Console GetConsole() => _console;

    [StarlarkMethod(
        "fill_template",
        Doc = "Replaces variables in templates with the values from this revision.")]
    public string FillTemplate(
        [Param(Name = "template", Doc = "The template to use", Named = true)] string template) =>
        LabelFinder.MapLabels(GetAllLabels, template);

    public MigrationInfo GetMigrationInfo() => _migrationInfo;

    public IRevision GetResolvedReference() => _resolvedReference;

    public IRevision? GetCurrentRev() => _currentRev;

    public bool IsInsideExplicitTransform() => _insideExplicitTransform;

    /// <summary>Create a clone of the transform work but use a different console.</summary>
    public TransformWork WithConsole(Console newConsole) =>
        new(
            GetCheckoutDir(),
            _metadata,
            _changes,
            Preconditions.CheckNotNull(newConsole),
            _migrationInfo,
            _resolvedReference,
            _treeState,
            _insideExplicitTransform,
            _lastRev,
            _currentRev,
            _skylarkTransformParams,
            _originApi,
            _destinationApi,
            _destinationReader,
            _destinationInfo,
            _mode);

    /// <summary>Clear the TreeState cache, unless we can confirm that it is up-to-date.</summary>
    public void ValidateTreeStateCache() => _treeState.MaybeClearCache();

    public TransformWork WithParams(Dict @params) =>
        new(
            GetCheckoutDir(),
            _metadata,
            _changes,
            _console,
            _migrationInfo,
            _resolvedReference,
            _treeState,
            _insideExplicitTransform,
            _lastRev,
            _currentRev,
            Preconditions.CheckNotNull(@params),
            _originApi,
            _destinationApi,
            _destinationReader,
            _destinationInfo,
            _mode);

    public TransformWork WithChanges(Changes changes) =>
        new(
            GetCheckoutDir(),
            _metadata,
            Preconditions.CheckNotNull(changes),
            _console,
            _migrationInfo,
            _resolvedReference,
            _treeState,
            _insideExplicitTransform,
            _lastRev,
            _currentRev,
            _skylarkTransformParams,
            _originApi,
            _destinationApi,
            _destinationReader,
            _destinationInfo,
            _mode);

    public TransformWork WithLastRev(IRevision? previousRef) =>
        new(
            GetCheckoutDir(),
            _metadata,
            _changes,
            _console,
            _migrationInfo,
            _resolvedReference,
            _treeState,
            _insideExplicitTransform,
            previousRef,
            _currentRev,
            _skylarkTransformParams,
            _originApi,
            _destinationApi,
            _destinationReader,
            _destinationInfo,
            _mode);

    public TransformWork WithResolvedReference(IRevision resolvedReference) =>
        new(
            GetCheckoutDir(),
            _metadata,
            _changes,
            _console,
            _migrationInfo,
            Preconditions.CheckNotNull(resolvedReference),
            _treeState,
            _insideExplicitTransform,
            _lastRev,
            _currentRev,
            _skylarkTransformParams,
            _originApi,
            _destinationApi,
            _destinationReader,
            _destinationInfo,
            _mode);

    public TransformWork InsideExplicitTransform() =>
        new(
            GetCheckoutDir(),
            _metadata,
            _changes,
            _console,
            _migrationInfo,
            _resolvedReference,
            _treeState,
            insideExplicitTransform: true,
            _lastRev,
            _currentRev,
            _skylarkTransformParams,
            _originApi,
            _destinationApi,
            _destinationReader,
            _destinationInfo,
            _mode);

    public TransformWork WithCurrentRev(IRevision currentRev) =>
        new(
            GetCheckoutDir(),
            _metadata,
            _changes,
            _console,
            _migrationInfo,
            _resolvedReference,
            _treeState,
            _insideExplicitTransform,
            _lastRev,
            Preconditions.CheckNotNull(currentRev),
            _skylarkTransformParams,
            _originApi,
            _destinationApi,
            _destinationReader,
            _destinationInfo,
            _mode);

    public TransformWork WithDestinationInfo(IDestinationInfo? newDestinationInfo) =>
        new(
            GetCheckoutDir(),
            _metadata,
            _changes,
            _console,
            _migrationInfo,
            _resolvedReference,
            _treeState,
            _insideExplicitTransform,
            _lastRev,
            _currentRev,
            _skylarkTransformParams,
            _originApi,
            _destinationApi,
            _destinationReader,
            newDestinationInfo,
            _mode);

    /// <summary>Update mutable state from another worker data.</summary>
    public void UpdateFrom(TransformWork skylarkWork) => _metadata = skylarkWork._metadata;

    public TreeState.TreeState GetTreeState() => _treeState;

    private IReadOnlyDictionary<string, IReadOnlyList<string>> GetCoreLabels()
    {
        var labels = new Dictionary<string, IReadOnlyList<string>>();
        string? ctxRef = !string.IsNullOrEmpty(_resolvedReference.ContextReference())
            ? _resolvedReference.ContextReference()
            : _resolvedReference.FixedReference();
        labels[CopybaraContextReferenceLabel] =
            ctxRef == null ? Array.Empty<string>() : new[] { ctxRef };
        labels[ContextReferenceLabel] = labels[CopybaraContextReferenceLabel];

        labels[CopybaraLastRev] = _lastRev == null
            ? Array.Empty<string>()
            : new[] { System.Text.RegularExpressions.Regex.Replace(_lastRev.AsString(), " .*", "") };

        labels[CopybaraCurrentRev] = _currentRev == null
            ? Array.Empty<string>()
            : new[] { System.Text.RegularExpressions.Regex.Replace(_currentRev.AsString(), " .*", "") };

        SetDateForCurrentRev(labels);

        labels[CopybaraCurrentMessage] = new[] { GetMessage() };
        labels[CopybaraAuthor] = new[] { GetAuthor().Name, GetAuthor().Email };
        labels[CopybaraCurrentMessageTitle] =
            new[] { Change<IRevision>.ExtractFirstLine(_metadata.GetMessage()) };
        return labels;
    }

    private void SetDateForCurrentRev(Dictionary<string, IReadOnlyList<string>> labels)
    {
        if (_currentRev == null)
        {
            labels[CopybaraCurrentRevDateTime] = Array.Empty<string>();
            return;
        }
        DateTimeOffset? time = null;
        try
        {
            time = _currentRev.ReadTimestamp();
        }
        catch (RepoException e)
        {
            _console.Warn("Cannot access date for change " + _currentRev.AsString() + ": " + e.Message);
        }
        labels[CopybaraCurrentRevDateTime] = time != null
            ? new[] { time.Value.ToString("yyyy-MM-ddTHH:mm:sszzz", CultureInfo.InvariantCulture) }
            : Array.Empty<string>();
    }

    public void OnFinish(object? result, object context) =>
        ValidationException.CheckCondition(
            result == null || result.Equals(StarlarkRt.None),
            "Transform work cannot return any result but returned: {0}",
            result);

    /// <summary>Supplier of a resource that may throw validation/repo exceptions.</summary>
    public interface IResourceSupplier<out T>
    {
        T Get();
    }
}
