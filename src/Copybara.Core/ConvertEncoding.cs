/*
 * Copyright (C) 2023 Google LLC
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

using System.Text;
using Copybara.TreeState;
using Copybara.Util;

namespace Copybara;

/// <summary>Convert encoding for a set of files.</summary>
public class ConvertEncoding : ITransformation
{
    private readonly Encoding _before;
    private readonly Encoding _after;
    private readonly Glob _paths;

    public ConvertEncoding(Encoding before, Encoding after, Glob paths)
    {
        _before = before;
        _after = after;
        _paths = paths;
    }

    public TransformationStatus Transform(TransformWork work)
    {
        string checkoutDir = work.GetCheckoutDir();
        var files = new HashSet<TreeState.TreeState.FileState>();
        foreach (var f in work.GetTreeState().Find(_paths.RelativeTo(checkoutDir)))
        {
            byte[] raw = File.ReadAllBytes(f.GetPath());
            string content = _before.GetString(raw);
            File.WriteAllBytes(f.GetPath(), _after.GetBytes(content));
            files.Add(f);
        }
        work.GetTreeState().NotifyModify(files);
        return files.Count == 0
            ? TransformationStatus.Noop("Glob didn't match any file")
            : TransformationStatus.Success();
    }

    public ITransformation Reverse() => new ConvertEncoding(_after, _before, _paths);

    public string Describe() => "convert_encoding";
}
