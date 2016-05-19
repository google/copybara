package com.google.copybara.testing;

import com.google.copybara.Origin;
import com.google.copybara.Origin.Reference;
import com.google.copybara.RepoException;

import javax.annotation.Nullable;

/**
 * A mock reference of a change
 */
public class MockReference<O extends Origin<O>> implements Reference<O> {

  public static final String MOCK_LABEL_REV_ID = "MockLabelRevId";

  private final String reference;

  public MockReference(String reference) {
    this.reference = reference;
  }

  @Nullable
  @Override
  public Long readTimestamp() throws RepoException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String asString() {
    return reference;
  }

  @Override
  public String getLabelName() {
    return MOCK_LABEL_REV_ID;
  }
}
