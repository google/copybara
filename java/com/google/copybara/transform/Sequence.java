package com.google.copybara.transform;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.EnvironmentException;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.ProgressPrefixConsole;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A transformation that runs a sequence of delegate transformations
 */
public class Sequence implements Transformation {

  private final ImmutableList<Transformation> sequence;

  protected final Logger logger = Logger.getLogger(Sequence.class.getName());

  Sequence(ImmutableList<Transformation> sequence) {
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

  @Override
  public Transformation reverse() {
    ImmutableList.Builder<Transformation> list = ImmutableList.builder();
    for (Transformation element : sequence) {
      list.add(element.reverse());
    }
    return new Sequence(list.build().reverse());
  }

  @VisibleForTesting
  public ImmutableList<Transformation> getSequence() {
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

  @DocElement(yamlName = "!Sequence", description = "A sequence of transformations. This is useful"
      + " when you want to reuse the sequence in several workflows and be able to get the inverse"
      + " of the sequence.", elementKind = Transformation.class)
  public final static class Yaml implements Transformation.Yaml {

    ImmutableList<Transformation.Yaml> transformations = ImmutableList.of();

    @DocField(description = "Transformations to run on the migration code.",
        required = false)
    public void setTransformations(List<? extends Transformation.Yaml> transformations)
        throws ConfigValidationException {
      this.transformations = ImmutableList.<Transformation.Yaml>copyOf(transformations);
    }

    @Override
    public Sequence withOptions(Options options)
        throws ConfigValidationException, EnvironmentException {
      //Avoid nesting one sequence inside another sequence
      if (this.transformations.size() == 1 && Iterables
          .getOnlyElement(this.transformations) instanceof Sequence.Yaml) {
        return (Sequence) Iterables.getOnlyElement(this.transformations).withOptions(options);
      }
      ImmutableList.Builder<Transformation> transformations = new ImmutableList.Builder<>();
      for (Transformation.Yaml yaml : this.transformations) {
        transformations.add(yaml.withOptions(options));
      }
      return new Sequence(transformations.build());
    }

    @Override
    public void checkReversible() throws ConfigValidationException {
      for (Transformation.Yaml transformation : transformations) {
        transformation.checkReversible();
      }
    }
  }
}
