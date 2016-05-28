package com.google.copybara.transform;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.ProgressPrefixConsole;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A transformation that runs a sequence of delegate transformations
 */
public class Sequence<T extends Transformation> implements Transformation {

  protected final ImmutableList<T> sequence;

  protected final Logger logger = Logger.getLogger(Sequence.class.getName());

  public Sequence(ImmutableList<T> sequence) {
    this.sequence = Preconditions.checkNotNull(sequence);
  }

  @Override
  public void transform(Path workdir, Console console) throws IOException, ValidationException {
    for (int i = 0; i < sequence.size(); i++) {
      Transformation transformation = sequence.get(i);
      String transformMsg = String.format(
          "[%2d/%d] Transform %s", i + 1, sequence.size(),
          transformation.describe());
      logger.log(Level.INFO, transformMsg);

      console.progress(transformMsg);
      transformation.transform(workdir, new ProgressPrefixConsole(transformMsg + ": ", console));
    }
  }

  @VisibleForTesting
  public ImmutableList<T> getSequence() {
    return sequence;
  }

  /**
   * returns a string like "Sequence[a, b, c]"
   */
  @Override
  public String toString() {
    return "Sequence" + sequence;
  }

  @Override
  public String describe() {
    return "sequence";
  }

  public static class ReversibleSequence
      extends Sequence<ReversibleTransformation> implements ReversibleTransformation {

    public ReversibleSequence(ImmutableList<ReversibleTransformation> list) {
      super(list);
    }

    @Override
    public ReversibleTransformation reverse() throws ValidationException {
      ImmutableList.Builder<ReversibleTransformation> list = ImmutableList.builder();
      for (ReversibleTransformation element : sequence) {
        list.add(new Reverse(element));
      }
      return new ReversibleSequence(list.build().reverse());
    }

    @Override
    public String toString() {
      return "Reversible" + super.toString();
    }
  }

  public static Transformation of(ImmutableList<Transformation> transformations) {
    boolean reversible = true;
    ImmutableList.Builder<ReversibleTransformation> reversibleSequence = ImmutableList.builder();
    for (Transformation transformation : transformations) {
      if (!(transformation instanceof ReversibleTransformation)) {
        reversible = false;
        break;
      }
      reversibleSequence.add((ReversibleTransformation) transformation);
    }
    return reversible
        ? new ReversibleSequence(reversibleSequence.build())
        : new Sequence<>(transformations);
  }

  // TODO(malcon): create the yaml classes
}
