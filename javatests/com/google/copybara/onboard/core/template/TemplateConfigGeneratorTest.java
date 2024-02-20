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

package com.google.copybara.onboard.core.template;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.Input;
import com.google.copybara.onboard.core.InputProviderResolver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TemplateConfigGeneratorTest {

  public static final InputProviderResolver RESOLVER = new InputProviderResolver() {

    @Override
    public <T> T resolve(Input<T> input) {
      throw new IllegalStateException("Shouldn't be called in this test!");
    }
  };

  @Test
  public void testSimple() throws CannotProvideException, InterruptedException {
    TemplateConfigGenerator generator = new TemplateConfigGenerator(""
        + "foo = \"::foo::\",\n"
        + "bar = ::foo::,\n"
        + "other = ::bar::,\n"
        + "::keyword_params::\n"
        + "") {

      @Override
      public String name() {
        return "test";
      }

      @Override
      public ImmutableSet<Input<?>> consumes() {
        return ImmutableSet.of();
      }

      @Override
      public boolean isGenerator(InputProviderResolver resolver) {
        return true;
      }

      @Override
      protected ImmutableMap<Field, Object> resolve(InputProviderResolver resolver) {
        return ImmutableMap.of(
            Field.required("foo"), "hello",
            Field.required("bar"), 32,
            Field.optional("other"), true,
            Field.requiredKeyword("required_keyword"), keywordStringLiteral("something")
        );
      }
    };
    assertThat(generator.generate(RESOLVER)).isEqualTo(""
        + "foo = \"hello\",\n"
        + "bar = hello,\n"
        + "other = 32,\n"
        + "other = true,\n"
        + "required_keyword = \"something\",\n");
  }

  @Test
  public void testOnlyMatchNamedFieldsFromTemplate()
      throws CannotProvideException, InterruptedException {
    TemplateConfigGenerator generator =
        new TemplateConfigGenerator("" + "foo = \"::foo::\",\n" + "bar = ::bar::,\n" + "") {

          @Override
          public String name() {
            return "test";
          }

          @Override
          public ImmutableSet<Input<?>> consumes() {
            return ImmutableSet.of();
          }

          @Override
          public boolean isGenerator(InputProviderResolver resolver) {
            return true;
          }

          @Override
          protected ImmutableMap<Field, Object> resolve(InputProviderResolver resolver) {
            return ImmutableMap.of(
                Field.required("foo"), "::i_am_a_provided_value::", Field.required("bar"), "hello");
          }
        };
    assertThat(generator.generate(RESOLVER))
        .isEqualTo("" + "foo = \"::i_am_a_provided_value::\",\n" + "bar = hello,\n");
  }

  @Test
  public void testLoadStatements() throws CannotProvideException, InterruptedException {
    TemplateConfigGenerator generator =
        new TemplateConfigGenerator("::load_statements::\n") {

          @Override
          public String name() {
            return "test";
          }

          @Override
          public ImmutableSet<Input<?>> consumes() {
            return ImmutableSet.of();
          }

          @Override
          public boolean isGenerator(InputProviderResolver resolver) {
            return true;
          }

          @Override
          public String generate(InputProviderResolver resolver)
              throws CannotProvideException, InterruptedException {
            addLoadStatement("//devtools/foo/bar", "two");
            addLoadStatement("//devtools/foo/bar", "two");
            addLoadStatement("//devtools/foo/bar", "one");
            addLoadStatement("//devtools/bar/baz", "bara");
            return super.generate(resolver);
          }

          @Override
          protected ImmutableMap<Field, Object> resolve(InputProviderResolver resolver) {
            return ImmutableMap.of();
          }
        };
    assertThat(generator.generate(RESOLVER))
        .isEqualTo(
            "load('//devtools/bar/baz', 'bara')\n" + "load('//devtools/foo/bar', 'one', 'two')\n");
  }

  @Test
  public void testKeywordPadding() throws CannotProvideException, InterruptedException {
    TemplateConfigGenerator generator = new TemplateConfigGenerator(""
        + "foo = \"::foo::\",\n"
        + "  \t    ::keyword_params::\n"
        + "") {

      @Override
      public String name() {
        return "test";
      }

      @Override
      public ImmutableSet<Input<?>> consumes() {
        return ImmutableSet.of();
      }

      @Override
      public boolean isGenerator(InputProviderResolver resolver) throws InterruptedException {
        return true;
      }

      @Override
      protected ImmutableMap<Field, Object> resolve(InputProviderResolver resolver) {
        return ImmutableMap.of(
            Field.required("foo"), "hello",
            Field.optional("other"), true,
            Field.requiredKeyword("required_keyword"), keywordStringLiteral("something")
        );
      }
    };
    assertThat(generator.generate(RESOLVER)).isEqualTo(""
        + "foo = \"hello\",\n"
        + "  \t    other = true,\n"
        + "  \t    required_keyword = \"something\",\n");
  }
}

