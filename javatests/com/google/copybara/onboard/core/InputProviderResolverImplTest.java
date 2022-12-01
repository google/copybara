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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.onboard.core.AskInputProvider.Mode;
import com.google.copybara.util.console.testing.TestingConsole;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InputProviderResolverImplTest {

  private static final Input<String> ONE =
      Input.create("InputProviderResolverImplTestOne",
          "just for test", null, String.class,
          (s, resolver) -> s);
  private static final Input<String> TWO =
      Input.create("InputProviderResolverImplTestTwo",
          "just for test", null, String.class,
          (s, resolver) -> s);
  private static final Input<String> THREE =
      Input.create("InputProviderResolverImplTestThree",
          "just for test", null, String.class,
          (s, resolver) -> s);

  private static final Input<String> RESOLVE =
      Input.<String>create("InputProviderResolverImplTestResolve",
          "just for test", null, String.class,
          (s, resolver) -> {
            try {
              Optional<String> val = resolver.resolve(TWO);
              if (val.isPresent()) {
                return val.get();
              } else {
                throw new CannotConvertException(
                    String.format("Cannot convert %s", s));
              }
            } catch (CannotProvideException e) {
              throw new CannotConvertException(String.format(
                  "Cannot convert %s: %s", s, e.getMessage()));
            } catch (InterruptedException e) {
              throw new RuntimeException("Unexpected", e);
            }
          });

  private TestingConsole console;

  @Before
  public void setup() {
    console = new TestingConsole();
  }

  @Test
  public void testSimple() throws CannotProvideException, InterruptedException {
    InputProviderResolver resolver = InputProviderResolverImpl.create(ImmutableList.of(
        new DepProvider(ONE, TWO),
        new DepProvider(TWO, THREE),
        new ConstantProvider<>(THREE, "hello")
    ), ImmutableList.of(), Mode.AUTO, console);
    assertThat(resolver.resolve(ONE)).isEqualTo(Optional.of("hello"));
  }

  @Test
  public void testOptionalResolve() throws CannotProvideException, InterruptedException {
    InputProviderResolver resolver = InputProviderResolverImpl.create(ImmutableList.of(
        new ConstantProvider<>(ONE, null)
    ), ImmutableList.of(), Mode.FAIL, console);
    assertThat(resolver.resolveOptional(ONE)).isEqualTo(Optional.empty());
  }

  @Test
  public void testConsoleInserted() throws CannotProvideException, InterruptedException {
    console.respondWithString("my value");
    console.respondWithString("my value");
    console.respondWithString("my value");
    InputProviderResolver resolver = InputProviderResolverImpl.create(ImmutableList.of(
        new DepProvider(ONE, TWO),
        new DepProvider(TWO, THREE),
        new ConstantProvider<>(THREE, "hello")
    ), ImmutableList.of(), Mode.CONFIRM, console);
    assertThat(resolver.resolve(ONE)).isEqualTo(Optional.of("my value"));
  }

  @Test
  public void testCache() throws CannotProvideException, InterruptedException {
    InputProviderResolver resolver = InputProviderResolverImpl.create(ImmutableList.of(
        new DepProvider(ONE, TWO, TWO),
        new OnlyOnce(TWO)
    ), ImmutableList.of(), Mode.AUTO, console);
    // Cached under the same root call to resolve
    assertThat(resolver.resolve(ONE)).isEqualTo(Optional.of("GOODGOOD"));
    // Cached between root calls to resolve
    assertThat(resolver.resolve(ONE)).isEqualTo(Optional.of("GOODGOOD"));
  }

  @Test
  public void testNotFoundStillAsks() throws CannotProvideException, InterruptedException {
    console.respondWithString("take this");
    InputProviderResolver resolver = InputProviderResolverImpl.create(ImmutableList.of(
        new ConstantProvider<>(ONE, "hello")), ImmutableList.of(), Mode.AUTO, console);
    assertThat(resolver.resolve(TWO)).isEqualTo(Optional.of("take this"));
  }

  @Test
  public void testLoop() throws CannotProvideException {
    InputProviderResolver resolver = InputProviderResolverImpl.create(ImmutableList.of(
        new DepProvider(ONE, TWO),
        new DepProvider(TWO, THREE),
        new DepProvider(THREE, ONE)
    ), ImmutableList.of(), Mode.AUTO, console);
    assertThat(assertThrows(IllegalStateException.class,
        () -> resolver.resolve(ONE))).hasMessageThat()
        .contains("Loop detected trying to resolver input: InputProviderResolverImplTestOne"
            + " -> InputProviderResolverImplTestTwo -> InputProviderResolverImplTestThree"
            + " -> *InputProviderResolverImplTestOne");
  }

  @Test
  public void testBadReturnType() throws CannotProvideException, InterruptedException {
    InputProviderResolver resolver =
        InputProviderResolverImpl.create(
            ImmutableList.of(
                new InputProvider() {
                  @Override
                  public <T> Optional<T> resolve(Input<T> input, InputProviderResolver db) {
                    return (Optional<T>) Optional.of(42);
                  }

                  @Override
                  public ImmutableMap<Input<?>, Integer> provides() {
                    return defaultPriority(ImmutableSet.of(ONE));
                  }
                }),
            ImmutableList.of(),
            Mode.AUTO,
            console);
    assertThat(assertThrows(IllegalStateException.class, () -> resolver.resolve(ONE)))
        .hasMessageThat()
        .containsMatch(".*InputProviderResolverImplTestOne.*requires an object of type.*");
  }

  @Test
  public void testResolveConverterLoop() throws CannotProvideException {
    console.respondWithString("is ignore");
    console.respondWithString("is ignore");
    InputProviderResolver resolver = InputProviderResolverImpl.create(
        ImmutableList.of(
            new DepProvider(TWO, RESOLVE),
            new ConstantProvider<>(RESOLVE, null)),
        ImmutableList.of(),
        Mode.AUTO, console);
    assertThat(assertThrows(IllegalStateException.class,
        () -> resolver.resolve(RESOLVE)))
        .hasMessageThat().contains("Loop detected");
  }

  @Test
  public void testResolveConverter() throws CannotProvideException, InterruptedException {
    console.respondWithString("is ignore");
    InputProviderResolver resolver = InputProviderResolverImpl.create(ImmutableList.of(
            new ConstantProvider<>(TWO, "other"),
            new ConstantProvider<>(RESOLVE, null)),
        ImmutableList.of(),
        Mode.AUTO, console);
    assertThat(resolver.resolve(RESOLVE)).isEqualTo(Optional.of("other"));
  }

  /**
   * A provider that just uses the resolver to return its dependency
   */
  private static final class DepProvider implements InputProvider {

    private final Input<String> provides;
    private final Input<String>[] dependencies;

    public DepProvider(Input<String> provides, Input<String> ... dependencies) {
      this.provides = provides;

      this.dependencies = dependencies;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> resolve(Input<T> input, InputProviderResolver db)
        throws InterruptedException, CannotProvideException {
      if (input == provides) {
        StringBuilder sb = new StringBuilder();
        for (Input<String> dependency : dependencies) {
          Optional<String> resolve = db.resolve(dependency);
          if (resolve.isEmpty()) {
            return Optional.empty();
          }
          sb.append(resolve.get());
        }
        return (Optional<T>) Optional.of(sb.toString());
      }
      throw new IllegalStateException("Shouldn't happen");
    }

    @Override
    public ImmutableMap<Input<?>, Integer> provides() {
      return defaultPriority(ImmutableSet.of(provides));
    }
  }

  private final class OnlyOnce implements InputProvider {

    private final Input<String> input;

    public OnlyOnce(Input<String> input) {
      this.input = input;
    }

    private boolean called = false;

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> resolve(Input<T> input, InputProviderResolver db) {
      checkArgument(input == this.input);
      if (called) {
        return (Optional<T>) Optional.of("BAD");
      } else {
        called = true;
        return (Optional<T>) Optional.of("GOOD");
      }
    }

    @Override
    public ImmutableMap<Input<?>, Integer> provides() {
      return defaultPriority(ImmutableSet.of(input));
    }
  }
}
