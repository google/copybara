/*
 * Copyright (C) 2024 Google LLC
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)

public class TtlSecretTest {

  @Test
  public void validCred() throws Exception {
    Clock clock = mock(Clock.class);
    Instant now = Instant.ofEpochSecond(100000);
    when(clock.instant()).thenReturn(now);
    TtlSecret underTest = new TtlSecret("SECRET!", "valid", now.plusSeconds(30), clock);
    assertThat(underTest.valid()).isTrue();
  }

  @Test
  public void invalidCred() throws Exception {
    Clock clock = mock(Clock.class);
    Instant now = Instant.ofEpochSecond(100000);
    when(clock.instant()).thenReturn(now);
    TtlSecret underTest = new TtlSecret("SECRET!", "invalid", now.minusSeconds(30), clock);
    assertThat(underTest.valid()).isFalse();
  }

  @Test
  public void invalidCred_grace() throws Exception {
    Clock clock = mock(Clock.class);
    Instant now = Instant.ofEpochSecond(100000);
    when(clock.instant()).thenReturn(now);
    TtlSecret underTest = new TtlSecret("SECRET!", "invalid", now.plusSeconds(3), clock);
    assertThat(underTest.valid()).isFalse();
  }

}
