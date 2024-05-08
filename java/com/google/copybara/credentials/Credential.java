/*
 * Copyright (C) 2023 Google LLC.
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
package com.google.copybara.credentials;

/** Holder for a credential. */
public interface Credential {

  /** A safe value that describes the credential */
  String printableValue();

  /** Whether the credential is still believed to be valid */
  boolean valid();

  /** The raw secret, this should not be used outside of framework code. */
  String provideSecret() throws CredentialRetrievalException;
}
