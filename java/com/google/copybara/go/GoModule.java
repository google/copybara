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

package com.google.copybara.go;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;

import com.google.common.base.Strings;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.version.VersionResolver;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.StarlarkValue;

/** Module used for go related Starklark operations */
@StarlarkBuiltin(name = "go", doc = "Module for Go related starlark operations")
public class GoModule implements StarlarkValue {
  private final RemoteFileOptions remoteFileOptions;

  public GoModule(RemoteFileOptions remoteFileOptions) {
    this.remoteFileOptions = checkNotNull(remoteFileOptions);
  }

  @StarlarkMethod(
      name = "go_proxy_version_list",
      doc = "Returns go proxy version list object",
      documented = false,
      parameters = {
        @Param(
            name = "module",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
            },
            doc =
                "The go module path name. e.g. github.com/google/gopacket. This will automatically"
                    + " normalize uppercase characters to '!{your_uppercase_character}' to escape"
                    + " them."),
        @Param(
            name = "ref",
            named = true,
            allowedTypes = {@ParamType(type = String.class), @ParamType(type = NoneType.class)},
            defaultValue = "None",
            doc =
                "This parameter is primarily used to track versions at specific branches and"
                    + " revisions. If a value is supplied, the returned version list will attempt"
                    + " to extract version data from ${ref}.info found with go proxy at the"
                    + " /@v/${ref}.info endpoint. You can leave off the .info suffix.")
      })
  @Example(
      title = "Create a version list for a given go package",
      before = "Example of how create a version list for github.com/google/gopacket",
      code = "go.go_proxy_version_list(\n" + "        module='github.com/google/gopacket'\n" + ")")
  public GoProxyVersionList getGoProxyVersionList(String module, Object ref) throws EvalException {
    String refConvert = convertFromNoneable(ref, null);
    if (!Strings.isNullOrEmpty(refConvert)) {
      return GoProxyVersionList.forInfo(module, refConvert, remoteFileOptions);
    }
    return GoProxyVersionList.forVersion(module, remoteFileOptions);
  }

  @StarlarkMethod(
      name = "go_proxy_resolver",
      doc = "Go resolver that knows what to do with command line passed refs.",
      documented = false,
      parameters = {
        @Param(
            name = "module",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
            },
            doc =
                "The go module path name. e.g. github.com/google/gopacket. This will automatically"
                    + " normalize uppercase characters to '!{your_uppercase_character}' to escape"
                    + " them.")
      })
  public VersionResolver getResolver(String module) {
    return new GoProxyVersionResolver(module, remoteFileOptions);
  }
}
