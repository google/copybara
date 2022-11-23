/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.onboard.core;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.Optional;

/**
 * An {@link InputProvider} that first tries to use a delegate {@code InputProvider}, then
 * use the default and then maybe ask the user for a value as last resort.
 */
public class AskInputProvider implements InputProvider {

  // We could use empty string but then we wouldn't allow the user to use the empty string when
  // the default is not empty.
  public static final String DEFAULT_PLACE_HOLDER = "PLEASE_USE_THE_DEFAULT";
  private final InputProvider delegate;
  private final Mode mode;
  private final Console console;

  public AskInputProvider(InputProvider delegate, Mode mode, Console console) {
    this.delegate = delegate;
    this.mode = mode;
    this.console = console;
  }

  @Override
  public <T> Optional<T> resolve(Input<T> input, InputProviderResolver db)
      throws InterruptedException, CannotProvideException {
    Optional<T> res = delegate.resolve(input, db);
    return mode.handleInput(input, res, console);
  }

  @Override
  public ImmutableMap<Input<?>, Integer> provides() {
    return delegate.provides();
  }

  /**
   * The different modes to ask the user for input given a result from an InputProvider
   */
  public enum Mode {
    /**
     * Fail if it requires to ask the user for input
     */
    FAIL {
      @Override
      <T> Optional<T> handleInput(Input<T> input, Optional<T> res,
          Console console) throws CannotProvideException {
        Optional<T> result = inputOrDefault(input, res);
        if (result.isPresent()) {
          return result;
        }
        throw new CannotProvideException(String.format(
            "Couldn't infer a value for %s(%s)", input.name(), input.description()));
      }
    },
    /**
     * Use the delegate but confirm the selection with the user
     * by using the result as the default for the question.
     */
    CONFIRM {
      @Override
      <T> Optional<T> handleInput(Input<T> input, Optional<T> res, Console console)
          throws InterruptedException {
        return askUser(input, inputOrDefault(input, res), console);
      }
    },
    /**
     * Only ask the user for input if the delegate cannot find a value
     * for an input.
     */
    AUTO {
      @Override
      <T> Optional<T> handleInput(Input<T> input, Optional<T> res,
          Console console) throws InterruptedException {
        Optional<T> defaultVal = inputOrDefault(input, res);
        if (defaultVal.isPresent()) {
          console.infoFmt("Inferred value for '%s(%s)': %s", input.description(),
              input.name(), defaultVal.get());
          return defaultVal;
        }
        return askUser(input, defaultVal, console);
      }
    };

    private static <T> Optional<T> askUser(Input<T> input, Optional<T> defaultVal, Console console)
        throws InterruptedException {
      try {
        String askResult = console.ask(
            String.format("Value for %s(%s)%s? ", input.description(), input.name(),
                defaultVal.map(t -> "[" + t + "]").orElse("")),
            defaultVal.isPresent() ? DEFAULT_PLACE_HOLDER : null,
            s -> {
              if (s.equals(DEFAULT_PLACE_HOLDER) && defaultVal.isPresent()) {
                return true;
              }
              try {
                input.convert(s);
                return true;
              } catch (Exception e) {
                console.error(e.getMessage());
                return false;
              }
            });
        if (DEFAULT_PLACE_HOLDER.equals(askResult) && defaultVal.isPresent()) {
          return defaultVal;
        }
        return Optional.of(input.convert(askResult));
      } catch (IOException e) {
        // We only throw IO on user cancellation. We need to fix that.
        throw new InterruptedException(e.getMessage());
      } catch (CannotConvertException e) {
        throw new IllegalStateException(String.format("Error processing %s."
            + " This is a copybara error. It should be catch by the validator", input), e);
      }
    }

    private static <T> Optional<T> inputOrDefault(Input<T> input, Optional<T> res) {
      if (res.isPresent()) {
        return res;
      }
      if (input.defaultValue() != null) {
        return Optional.of(input.defaultValue());
      }
      return Optional.empty();
    }

    abstract <T> Optional<T> handleInput(Input<T> input, Optional<T> res,
        Console console)
        throws CannotProvideException, InterruptedException;
  }
}
