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

package com.google.copybara.rust;

import com.google.api.client.util.Key;
import com.google.api.client.util.Value;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A data class that represents a version returned from a Rust crate registry, such as crates.io. <a
 * href="https://github.com/rust-lang/rfcs/blob/master/text/2141-alternative-registries.md#registry-index-format-specification">...</a>
 */
public class RustRegistryVersionObject {
  @Key private String name;
  @Key private String vers;
  @Key private List<Deps> deps;
  @Key private String cksum;
  @Key private Map<String, List<String>> features;
  @Key private boolean yanked;

  public RustRegistryVersionObject() {}

  public String getName() {
    return name;
  }

  public String getVers() {
    return vers;
  }

  public List<Deps> getDeps() {
    return deps;
  }

  public Map<String, List<String>> getFeatures() {
    return features;
  }

  public boolean isYanked() {
    return yanked;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("vers", vers)
        .add("deps", deps)
        .add("cksum", cksum)
        .add("features", features)
        .add("yanked", yanked)
        .toString();
  }

  /** A class that represents a crate dependency. */
  public static class Deps {
    @Key private String name;
    @Key private String req;
    @Key private String registry;
    @Key private List<String> features;
    @Key private boolean optional;

    @Key("default_features")
    private boolean defaultFeatures;

    @Key @Nullable private String target;
    @Key private DepsKinds kind;

    public Deps() {}

    enum DepsKinds {
      @Value("normal")
      NORMAL,
      @Value("build")
      BUILD,
      @Value("dev")
      DEV
    }

    public String getName() {
      return name;
    }

    public List<String> getFeatures() {
      return features;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("name", name)
          .add("req", req)
          .add("registry", registry)
          .add("features", features)
          .add("optional", optional)
          .add("defaultFeatures", defaultFeatures)
          .add("target", target)
          .add("kind", kind)
          .toString();
    }
  }
}
