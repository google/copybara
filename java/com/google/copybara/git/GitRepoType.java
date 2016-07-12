package com.google.copybara.git;

import com.google.copybara.doc.annotations.DocField;

/**
 * Git repository type. Knowing the repository type allow us to provide better experience, like
 * allowing to import Github PR/Gerrit changes using the web url as the reference.
 */
public enum GitRepoType {
  @DocField(description = "A standard git repository. This is the default")
  GIT,
  @DocField(description = "A git repository hosted in Github")
  GITHUB,
  @DocField(description = "A Gerrit code review repository")
  GERRIT
}
