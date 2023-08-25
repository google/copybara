/*
 * Copyright (C) 2023 Google LLC
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

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.credentials.CredentialModule.UsernamePasswordIssuer;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import net.starlark.java.eval.Printer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CredentialModuleTest {
  private SkylarkTestExecutor starlark;


  @Before
  public void setUp() {
    OptionsBuilder optionsBuilder = new OptionsBuilder();

    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testStaticSecret() throws Exception {
    CredentialIssuer res =
        starlark.eval("res", "res = credentials.static_secret('static', 'secret_value')");
    assertThat(res.describe()).containsExactly(
        "type", "constant", "name", "static", "open", "false");
    Credential cred = res.issue();
    assertThat(cred.toString()).doesNotContain("secret_value");
    StringBuilder sb = new StringBuilder();
    res.repr(new Printer());
    assertThat(sb.toString()).doesNotContain("secret_value");
  }

  @Test
  public void testStaticValue() throws Exception {
    CredentialIssuer res =
        starlark.eval("res", "res = credentials.static_value('static')");
    assertThat(res.describe()).containsExactly(
        "type", "constant", "name", "static", "open", "true");
  }

  @Test
  public void testUsernamePassword() throws Exception {
    UsernamePasswordIssuer res =
        starlark.eval("res", "res = credentials.username_password("
            + "credentials.static_value('static'),"
            + " credentials.static_secret('static', 'secret_value'))");
    assertThat(res.describeCredentials().get(0)).containsExactly(
        "type", "constant", "name", "static", "open", "true");
    assertThat(res.describeCredentials().get(1)).containsExactly(
        "type", "constant", "name", "static", "open", "false");

  }
}
