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
 * A transformer that removes matching substrings from the change description.
 */
public class Scrubber implements Transformation {

  private final Pattern pattern;
  private final String replacement;

  Scrubber(Pattern pattern, String replacement) {
    this.pattern = Preconditions.checkNotNull(pattern);
    this.replacement = Preconditions.checkNotNull(replacement);
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    work.setMessage(pattern.matcher(work.getMessage()).replaceAll(replacement));
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return "Description scrubber";
  }
}
