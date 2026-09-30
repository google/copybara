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

package com.google.copybara.onboard.ai;

import com.beust.jcommander.Parameters;
import com.google.copybara.Option;
import com.google.copybara.exception.ValidationException;

/** Options for AI-assisted onboarding. */
@Parameters(separators = "=")
public class AiOnboardOptions implements Option {

  /**
   * Returns the model backend used to generate configs. Environments that provide a backend
   * override this.
   */
  public LlmClient getLlmClient() throws ValidationException {
    throw new ValidationException("No LLM backend is configured for AI-assisted onboarding.");
  }

  /**
   * Returns Markdown describing the target workflow macro, its parameters, and examples. It is sent
   * to the model ahead of the user's answers. Environments that provide an archetype override this.
   */
  public String getArchetype() throws ValidationException {
    throw new ValidationException("No archetype is configured for AI-assisted onboarding.");
  }
}
