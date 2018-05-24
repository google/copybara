/*
 * Copyright (C) 2017 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import javax.annotation.Nullable;

/**
 * Load a resource (repository, API client...) lazily to avoid side effects.
 */
public interface LazyResourceLoader<T> {

  /**
   * Load the resource.
   */
   T load(@Nullable Console console) throws RepoException, ValidationException;

  /**
   * Constructs a {@link LazyResourceLoader} object that defers the loading of the resource
   * until {@link #load(Console)} is called and after that always returns the same instance.
   */
  static <T> LazyResourceLoader<T> memoized(LazyResourceLoader<T> delegate) {

     return new LazyResourceLoader<T>() {
       T resource;

       /**
        * @see LazyResourceLoader#load(Console)
        */
       @Override
       public T load(Console console) throws RepoException, ValidationException {
         if (resource == null) {
           resource = Preconditions.checkNotNull(delegate.load(console));
         }
         return resource;
       }
     };
   }
}
