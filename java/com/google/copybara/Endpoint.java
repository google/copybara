/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.DestinationEffect.OriginRef;
import com.google.copybara.util.console.Console;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/**
 * An origin or destination API in a feedback migration.
 *
 * <p>Endpoints are symmetric, that is, they need to be able to act both as an origin and
 * destination of a feedback migration, which means that they need to support both read and write
 * operations on the API.
 */
@SuppressWarnings("unused")
@StarlarkBuiltin(name = "endpoint", doc = "An origin or destination API in a feedback migration.")
public interface Endpoint extends StarlarkValue {

  /**
   * To be used for core.workflow origin/destinations that don't want to provide an api for giving
   * feedback.
   */
  Endpoint NOOP_ENDPOINT =
      new Endpoint() {
        @Override
        public ImmutableSetMultimap<String, String> describe() {
          throw new IllegalStateException("Instance shouldn't be used for core.feedback");
        }

        @Override
        public void repr(Printer printer) {
          printer.append("noop_endpoint");
        }
      };

  @Override
  default void repr(Printer printer) {
    printer.append(toString());
  }

  /** Returns a key-value ist of the options the endpoint was instantiated with. */
  ImmutableSetMultimap<String, String> describe();

  @StarlarkMethod(
      name = "new_origin_ref",
      doc = "Creates a new origin reference out of this endpoint.",
      parameters = {
        @Param(name = "ref", named = true, doc = "The reference."),
      })
  default OriginRef newOriginRef(String ref) {
    return new OriginRef(ref);
  }

  @StarlarkMethod(
      name = "new_destination_ref",
      doc = "Creates a new destination reference out of this endpoint.",
      parameters = {
        @Param(name = "ref", named = true, doc = "The reference."),
        @Param(name = "type", named = true, doc = "The type of this reference."),
        @Param(
            name = "url",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc = "The url associated with this reference, if any.",
            defaultValue = "None"),
      })
  default DestinationRef newDestinationRef(String ref, String type, Object urlObj) {
    String url = Starlark.isNullOrNone(urlObj) ? null : (String) urlObj;
    return new DestinationRef(ref, type, url);
  }

  @StarlarkMethod(
      name = "url",
      doc = "Return the URL of this endpoint.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  default String getUrl() {
    return null;
  }

  /**
   * Returns an instance of this endpoint with the given console.
   */
  default Endpoint withConsole(Console console) {
    return this;
  }
}
