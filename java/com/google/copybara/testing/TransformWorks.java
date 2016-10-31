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
package com.google.copybara.testing;

import com.google.copybara.authoring.Author;
import com.google.copybara.Change;
import com.google.copybara.Changes;
import com.google.copybara.Metadata;
import com.google.copybara.MigrationInfo;
import com.google.copybara.TransformWork;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.nio.file.Path;

/**
 * Utility methods related to {@link TransformWork}.
 */
public class TransformWorks {

  /**
   * Creates an instance with reasonable defaults for testing.
   */
  public static TransformWork of(Path checkoutDir, String msg, Console console) {
    return new TransformWork(checkoutDir,
        new Metadata(msg, new Author("foo", "foo@foo.com")),
        new Changes() {
          @Override
          public SkylarkList<? extends Change<?>> getCurrent() {
            throw new UnsupportedOperationException();
          }

          @Override
          public SkylarkList<? extends Change<?>> getMigrated() {
            throw new UnsupportedOperationException();
          }
          // TODO(malcon): Pass this from test.
        }, console, new MigrationInfo(DummyOrigin.LABEL_NAME, /*destinationReader=*/ null));
  }

}
