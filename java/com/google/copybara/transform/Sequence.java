/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.transform;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.ProgressPrefixConsole;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A transformation that runs a sequence of delegate transformations
 */
public class Sequence implements Transformation {

  private final ImmutableList<Transformation> sequence;

  protected final Logger logger = Logger.getLogger(Sequence.class.getName());

  private Sequence(ImmutableList<Transformation> sequence) {
    this.sequence = Preconditions.checkNotNull(sequence);
  }

  @Override
  public void transform(TransformWork work, Console console)
      throws IOException, ValidationException {
    for (int i = 0; i < sequence.size(); i++) {
      Transformation transformation = sequence.get(i);
      String transformMsg = String.format(
          "[%2d/%d] Transform %s", i + 1, sequence.size(),
          transformation.describe());
      logger.log(Level.INFO, transformMsg);

      console.progress(transformMsg);
      transformation.transform(work, new ProgressPrefixConsole(transformMsg + ": ", console));
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
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

  /**
   * Create a sequence from Skylark that avoids nesting a single sequence twice.
   * @param description a description of the argument being converted, such as its name
   */
  public static Sequence fromConfig(SkylarkList<Transformation> elements, String description)
      throws EvalException {
    //Avoid nesting one sequence inside another sequence
    if (elements.size() == 1 && elements.get(0) instanceof Sequence) {
      return (Sequence) elements.get(0);
    }
    return new Sequence(
        ImmutableList.copyOf(elements.getContents(Transformation.class, description)));
  }
}
