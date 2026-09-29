/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.testing;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.copybara.onboard.ai.LlmClient;
import com.google.copybara.onboard.ai.LlmException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * An {@link LlmClient} for hermetic tests. Returns programmed responses in order and records every
 * prompt it receives. Makes no network calls.
 */
public final class FakeLlmClient implements LlmClient {

  private interface Response {
    String get() throws LlmException;
  }

  private final Deque<Response> responses = new ArrayDeque<>();
  private final List<String> prompts = new ArrayList<>();

  /** Queues {@code response} to be returned by the next {@link #generate} call. */
  @CanIgnoreReturnValue
  public FakeLlmClient respondWith(String response) {
    responses.addLast(() -> response);
    return this;
  }

  /** Queues {@code exception} to be thrown by the next {@link #generate} call. */
  @CanIgnoreReturnValue
  public FakeLlmClient failWith(LlmException exception) {
    responses.addLast(
        () -> {
          throw exception;
        });
    return this;
  }

  /** Returns every prompt passed to {@link #generate}, in order. */
  public ImmutableList<String> getPrompts() {
    return ImmutableList.copyOf(prompts);
  }

  @Override
  public String generate(String prompt) throws LlmException {
    prompts.add(prompt);
    checkState(!responses.isEmpty(), "No more programmed responses for prompt:\n%s", prompt);
    return responses.removeFirst().get();
  }
}
