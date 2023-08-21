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

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * A data class that can be used to a json string into an object for response from
 * https://go.dev/ref/mod#goproxy-protocol
 */
public class GoVersionObject implements StarlarkValue {
  @Key("Version")
  private String version;

  @Key("Time")
  private String time;

  @Key("Origin")
  private Origin origin;

  public GoVersionObject() {}

  @StarlarkMethod(name = "version", doc = "The Version value from goproxy", structField = true)
  public String getVersion() {
    return version;
  }

  @StarlarkMethod(name = "time", doc = "The Time value from goproxy", structField = true)
  public String getTime() {
    return time;
  }

  @Nullable
  @StarlarkMethod(
      name = "origin",
      doc = "The Origin value from goproxy, if any",
      structField = true,
      allowReturnNones = true)
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("Version", version).add("Time", time).toString();
  }

  /** A data class that represents the optional Origin field in GoVersionObject. */
  public static class Origin implements StarlarkValue {
    @Key("VCS")
    private String vcs;

    @Key("URL")
    private String url;

    @Key("Ref")
    private String ref;

    @Key("Hash")
    private String hash;

    @Nullable
    @StarlarkMethod(
        name = "vcs",
        doc = "The Origin.VCS value from goproxy",
        structField = true,
        allowReturnNones = true)
    public String getVcs() {
      return vcs;
    }

    @Nullable
    @StarlarkMethod(
        name = "url",
        doc = "The Origin.URL value from goproxy",
        structField = true,
        allowReturnNones = true)
    public String getUrl() {
      return url;
    }

    @Nullable
    @StarlarkMethod(
        name = "ref",
        doc = "The Origin.Ref value from goproxy",
        structField = true,
        allowReturnNones = true)
    public String getRef() {
      return ref;
    }

    @Nullable
    @StarlarkMethod(
        name = "hash",
        doc = "The Origin.Hash value from goproxy",
        structField = true,
        allowReturnNones = true)
    public String getHash() {
      return hash;
    }
  }
}
