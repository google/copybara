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

package com.google.copybara.util;

import com.google.common.base.Preconditions;
import java.util.function.Supplier;

/**
 * A {@link Supplier} that allows to defer setting the value after passing it as a parameter.
 */
public class SettableSupplier<T> implements Supplier<T> {

  private T value;

  public void set(T value) {
    Preconditions.checkState(this.value == null, "Value already set to: %s", this.value);
    this.value = Preconditions.checkNotNull(value);
  }
  
  @Override
  public T get() {
    return Preconditions.checkNotNull(value, "Value is still not set!");
  }
}
