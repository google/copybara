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

package com.google.copybara.git;

import com.google.common.base.Preconditions;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.console.Console;

/**
 * Create a GitRepository lazily. This is important since the creation of a local repository
 * might have side effects (Deleting files, creating directories, checking if flags exists, etc.)
 */
public interface LazyGitRepository {

  /**
   * Force the evaluation of the repository.
   */
   GitRepository get(Console console) throws RepoException;

  /**
   * Constructs a {@link LazyGitRepository} object that defers the creation of the repository
   * until {@link #get(Console)} is called and after that always returns the same instance.
   */
  static LazyGitRepository memoized(LazyGitRepository delegate) {

     return new LazyGitRepository() {
       GitRepository repo;

       /**
        * @see LazyGitRepository#get(Console)
        */
       @Override
       public GitRepository get(Console console) throws RepoException {
         if (repo == null) {
           repo = Preconditions.checkNotNull(delegate.get(console));
         }
         return repo;
       }
     };
   }
}
