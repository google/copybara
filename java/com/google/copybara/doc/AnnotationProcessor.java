/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.doc;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreElements;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;
import com.google.copybara.doc.annotations.Library;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.List;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import net.starlark.java.annot.StarlarkBuiltin;

/**
 * An annotation processor which, for each jar, writes a text file containing the names of all
 * annotated classes in that jar.
 */
public final class AnnotationProcessor extends BasicAnnotationProcessor {

  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {

    return ImmutableList.of(
        new ProcessingStep() {
          @Override
          public ImmutableSet<? extends Class<? extends Annotation>> annotations() {
            return ImmutableSet.of(StarlarkBuiltin.class, Library.class);
          }

          @Override
          public ImmutableSet<Element> process(
              SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
            try {
              writeClassList(elementsByAnnotation);
            } catch (IOException e) {
              // Unexpected but we cannot do too much about this and Kind.ERROR makes the build
              // to fail.
              e.printStackTrace();
              processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage());
            }
            return ImmutableSet.of();
          }
        });
  }

  private void writeClassList(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) throws IOException {

    List<String> classNames =
        Streams.concat(
                elementsByAnnotation.get(Library.class).stream(),
                elementsByAnnotation.get(StarlarkBuiltin.class).stream())
            .map(AnnotationProcessor::className)
            .collect(toImmutableList());

    FileObject res =
        processingEnv
            .getFiler()
            .createResource(StandardLocation.SOURCE_OUTPUT, "", "starlark_class_list.txt");

    try (Writer writer = res.openWriter()) {
      writer.append(Joiner.on("\n").join(classNames)).append("\n");
    }
  }

  private static String className(Element element) {
    TypeElement module = MoreElements.asType(element);
    if (module.getNestingKind().isNested()) {
      // foo.SomeClass.Nested -> foo.SomeClass$Nested. Doesn't work for multi-nested,
      // but we don't care for now.
      return module.getQualifiedName().toString().replaceFirst("\\.([^.]+)$", "\\$$1");
    } else {
      return module.getQualifiedName().toString();
    }
  }
}
