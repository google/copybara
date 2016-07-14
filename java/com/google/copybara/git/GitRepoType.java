// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.copybara.RepoException;
import com.google.copybara.doc.annotations.DocField;

/**
 * Git repository type. Knowing the repository type allow us to provide better experience, like
 * allowing to import Github PR/Gerrit changes using the web url as the reference.
 */
public enum GitRepoType {
  @DocField(description = "A standard git repository. This is the default")
  GIT {
    @Override
    GitReference resolveRef(GitRepository repository, String repoUrl, String ref)
        throws RepoException {
      repository.simpleCommand("fetch", "-f", repoUrl, ref);
      return repository.resolveReference("FETCH_HEAD");
    }
  },
  @DocField(description = "A git repository hosted in Github")
  GITHUB {
    @Override
    GitReference resolveRef(GitRepository repository, String repoUrl, String ref)
        throws RepoException {
      // TODO(malcon): if ref is github url, resolve it properly
      return GIT.resolveRef(repository, repoUrl, ref);
    }
  },
  @DocField(description = "A Gerrit code review repository")
  GERRIT {
    @Override
    GitReference resolveRef(GitRepository repository, String repoUrl, String ref)
        throws RepoException {
      // TODO(malcon): if ref is gerrit url, resolve it properly
      return GIT.resolveRef(repository, repoUrl, ref);
    }
  };


  abstract GitReference resolveRef(GitRepository repository, String repoUrl, String ref)
      throws RepoException;
}
