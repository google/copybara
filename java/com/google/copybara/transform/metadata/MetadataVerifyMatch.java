package com.google.copybara.transform.metadata;

import com.google.common.base.Preconditions;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import com.google.re2j.Pattern;
import java.io.IOException;

/**
 * A checker that validates that the change description satisfies a Regex or that it doesn't
 * if verifyNoMatch is set.
 */
public class MetadataVerifyMatch implements Transformation {

  private final Pattern pattern;
  private final boolean verifyNoMatch;

  MetadataVerifyMatch(Pattern pattern, boolean verifyNoMatch) {
    this.pattern = Preconditions.checkNotNull(pattern);
    this.verifyNoMatch = verifyNoMatch;
  }

  @Override
  public void transform(TransformWork work) throws IOException, ValidationException {
    boolean found = pattern.matcher(work.getMessage()).find();
    if (!found && !verifyNoMatch) {
      throw new ValidationException(
          String.format("Could not find '%s' in the change message."
              + " Message was:\n%s", pattern, work.getMessage()));
    } else if (found && verifyNoMatch) {
      throw new ValidationException(
          String.format("'%s' found in the change message"
          + ". Message was:\n%s", pattern, work.getMessage()));
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return String.format("Verify message %s '%s'",
        (verifyNoMatch ? "does not match" : "matches"),
        pattern);
  }
}
