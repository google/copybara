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

package com.google.copybara.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.copybara.EndpointProvider;
import com.google.copybara.Option;
import com.google.copybara.Options;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** A Skylark module used by tests */
@StarlarkBuiltin(name = "testing", doc = "Module to use mock endpoints in tests.")
public class TestingModule implements StarlarkValue {

  private final TestingOptions testingOptions;

  public TestingModule(Options options) {
    Options opts = checkNotNull(options, "Options cannot be null");
    this.testingOptions = checkNotNull(opts.get(TestingOptions.class), "TestOptions not set");
  }

  @StarlarkMethod(name = "origin", doc = "A dummy origin")
  public DummyOrigin origin() {
    return testingOptions.origin;
  }

  @StarlarkMethod(name = "destination", doc = "A dummy destination")
  public RecordsProcessCallDestination destination() {
    return testingOptions.destination;
  }

  @StarlarkMethod(name = "dummy_endpoint", doc = "A dummy feedback endpoint")
  public EndpointProvider<DummyEndpoint> dummyEndpoint() {
    return EndpointProvider.wrap(testingOptions.feedbackTrigger);
  }

  @StarlarkMethod(name = "dummy_trigger", doc = "A dummy feedback trigger")
  public DummyTrigger dummyTrigger() {
    return testingOptions.feedbackTrigger;
  }

  @StarlarkMethod(name = "dummy_checker", doc = "A dummy checker")
  public DummyChecker dummyChecker() {
    return testingOptions.checker;
  }

  /**
   * Holder for options to adjust this module's behavior to the needs of a test.
   */
  public final static class TestingOptions implements Option {

    public DummyOrigin origin;
    public RecordsProcessCallDestination destination;

    public DummyTrigger feedbackTrigger;
    public DummyChecker checker;
  }
}
