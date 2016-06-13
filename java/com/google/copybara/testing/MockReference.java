package com.google.copybara.testing;

import com.google.common.base.MoreObjects;
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
  @Nullable private final Long timestamp;

  public MockReference(String reference) {
    this.reference = reference;
    this.timestamp = null;
  }

  private MockReference(String reference, @Nullable Long timestamp) {
    this.reference = reference;
    this.timestamp = timestamp;
  }

  /**
   * Returns an instance equivalent to this one but with the timestamp set to the specified value.
   */
  public MockReference withTimestamp(long timestamp) {
    return new MockReference(reference, timestamp);
  }

  @Nullable
  @Override
  public Long readTimestamp() throws RepoException {
    return timestamp;
  }

  @Override
  public String asString() {
    return reference;
  }

  @Override
  public String getLabelName() {
    return MOCK_LABEL_REV_ID;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("reference", reference)
        .toString();
  }
}
