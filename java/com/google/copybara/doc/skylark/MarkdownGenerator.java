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

package com.google.copybara.doc.skylark;

import com.beust.jcommander.Parameter;
import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Reads classes annotated with {@link DocElement} or {@link DocField} and generates Markdown
 * documentation.
 */
public class MarkdownGenerator extends BasicAnnotationProcessor {

  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {

    return ImmutableList.of(new ProcessingStep() {
      @Override
      public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.<Class<? extends Annotation>>of(SkylarkModule.class);
      }

      @Override
      public Set<Element> process(
          SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
        try {
          processDoc(elementsByAnnotation);
        } catch (ElementException e) {
          processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), e.element);
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

  private void processDoc(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation)
      throws ElementException, IOException {
    Multimap<String, String> docByElementType = ArrayListMultimap.create();

    for (Element element : elementsByAnnotation.get(SkylarkModule.class)) {
      TypeElement module = (TypeElement) element;
      StringBuilder sb = new StringBuilder();

      SkylarkModule skyModule = module.getAnnotation(SkylarkModule.class);
      if (!skyModule.documented()) {
        continue;
      }
      sb.append("# ").append(skyModule.name()).append("\n\n");
      sb.append(skyModule.doc());
      sb.append("\n\n");

      // Generate flags associated with the whole module
      sb.append(generateFlagsInfo(module));

      for (Element member : module.getEnclosedElements()) {
        sb.append(generateFunctionDocumentation(module, skyModule, member));
      }
      docByElementType.put(skyModule.name(), sb.toString());
    }

    for (String group : docByElementType.keySet()) {
      FileObject resource = processingEnv.getFiler().createResource(
          StandardLocation.SOURCE_OUTPUT, "", group + ".copybara.md");

      try (Writer writer = resource.openWriter()) {
        for (String groupValues : docByElementType.get(group)) {
          writer.append(groupValues).append("\n");
        }
      }
    }
  }

  private CharSequence generateFunctionDocumentation(TypeElement module, SkylarkModule skyModule,
      Element member)
      throws ElementException {
    StringBuilder sb = new StringBuilder();
    AnnotationHelper<SkylarkSignature> signature = annotationHelper(member,
        SkylarkSignature.class);
    if (signature == null || !(member instanceof VariableElement)) {
      return sb;
    }

    if (!signature.ann.documented()) {
      return sb;
    }

    String functionName = signature.ann.name();
    sb.append("## ").append(functionName).append("\n\n");
    sb.append(signature.ann.doc());
    sb.append("\n\n");

    // Create a string like `Origin foo(param, param2=Default)`
    sb.append("`");
    String returnTypeStr = skylarkTypeName(signature.getClassValue("returnType"));

    if (!returnTypeStr.equals("")) {
      sb.append(returnTypeStr).append(" ");
    }

    DeclaredType objectType = signature.getClassValue("objectType");

    if (objectType.equals(module)) {
      sb.append(skyModule.name()).append(".");
    }

    sb.append(functionName).append("(");

    List<AnnotationValue> params = signature.getValue("parameters");


    StringBuilder longDescription = new StringBuilder();
    int startIndex = firstParamIsSelf(module, skyModule, objectType) ? 1 : 0;
    for (int i = startIndex; i < params.size(); i++) {
      AnnotationHelper<Param> param = new AnnotationHelper<>(
          signature.ann.parameters()[i], (AnnotationMirror) params.get(i).getValue(), member);
      if (i > startIndex) {
        sb.append(", ");
      }
      sb.append(param.ann.name());
      String defaultValue = param.ann.defaultValue();

      if (!defaultValue.isEmpty()) {
        sb.append("=").append(defaultValue);
      }
      longDescription.append(param.ann.name()).append("|");
      longDescription.append("`").append(skylarkTypeNameGeneric(
          param.getClassValue("type"),
          param.getClassValue("generic1")
      )).append("`").append("<br><p>");

      longDescription.append(param.ann.doc());
      longDescription.append("</p>");
      longDescription.append("\n");
    }
    sb.append(")`\n\n");

    if (longDescription.length() > 0) {
      sb.append("### Parameters:\n\n");
      sb.append("Parameter | Description\n");
      sb.append("--------- | -----------\n");
      sb.append(longDescription);
      sb.append("\n\n");
    }
    // Generate flags associated with an specific method
    sb.append(generateFlagsInfo(member));
    return sb;
  }

  /** Detect if the first parameter is 'self' object. */
  private boolean firstParamIsSelf(TypeElement classElement, SkylarkModule skyModule,
      DeclaredType objectType) {
    return !skyModule.namespace() && objectType.toString().equals(classElement.toString());
  }

  private String skylarkTypeNameGeneric(DeclaredType declared, @Nullable DeclaredType generic) {
    String name = skylarkTypeName(declared);
    if (name.isEmpty()) {
      return "";
    }
    if (generic == null || generic.toString().equals("java.lang.Object")) {
      return name;
    }
    return name + " of " + skylarkTypeName(generic);
  }

  private String skylarkTypeName(DeclaredType declared) {
    if (declared.toString().equals(
        "com.google.devtools.build.lib.syntax.Runtime.NoneType")) {
      return "";
    }
    Element element = processingEnv.getTypeUtils().asElement(declared);
    SkylarkModule skyType = element.getAnnotation(SkylarkModule.class);
    if (skyType != null) {
      return skyType.name();
    }
    return simplerJavaTypes(element);
  }

  private CharSequence generateFlagsInfo(Element classElement) throws ElementException {
    StringBuilder sb = new StringBuilder();
    AnnotationHelper<UsesFlags> annotation = annotationHelper(classElement, UsesFlags.class);
    if (annotation == null) {
      return sb;
    }
    StringBuilder flagsString = new StringBuilder();
    for (DeclaredType flag : annotation.getClassListValue("value")) {
      Element flagClass = flag.asElement();
      for (Element member : flagClass.getEnclosedElements()) {
        Parameter flagAnnotation = member.getAnnotation(Parameter.class);
        if (flagAnnotation == null
            || !(member instanceof VariableElement)
            || flagAnnotation.hidden()) {
          continue;
        }
        VariableElement field = (VariableElement) member;
        flagsString.append(Joiner.on(", ").join(flagAnnotation.names()));
        flagsString.append(" | *");
        flagsString.append(simplerJavaTypes(field));
        flagsString.append("* | ");
        flagsString.append(flagAnnotation.description());
        flagsString.append("\n");
      }
    }
    if (flagsString.length() > 0) {
      sb.append("\n\n**Command line flags:**\n\n");
      sb.append("Name | Type | Description\n");
      sb.append("---- | ----------- | -----------\n");
      sb.append(flagsString);
      sb.append("\n");
    }
    return sb;
  }

  private String simplerJavaTypes(Element field) {
    String s = field.asType().toString();
    int dot = s.lastIndexOf('.');
    if (dot == -1) {
      return deCapitalize(s);
    }
    return deCapitalize(s.substring(dot + 1));
  }

  private String deCapitalize(String substring) {
    return Character.toLowerCase(substring.charAt(0)) + substring.substring(1);
  }

  /**
   * Small helper for accessing annotation information in a safe way (no ClassNotFound
   * exceptions when trying to access class value types) and at the same time access simple
   * types using the annotation fields.
   */
  private final class AnnotationHelper<A extends Annotation> {

    private final Element element;
    private final AnnotationMirror annMirror;
    private final A ann;

    private AnnotationHelper(A ann, AnnotationMirror annMirror, Element element) {
      this.element = element;
      this.annMirror = annMirror;
      this.ann = ann;
    }

    private DeclaredType getClassValue(String name) throws ElementException {
      return (DeclaredType) getValue(name);
    }

    @SuppressWarnings("unchecked")
    private List<DeclaredType> getClassListValue(String name) throws ElementException {
      List<DeclaredType> result = new ArrayList<>();
      for (AnnotationValue val : (List<AnnotationValue>) getValue(name)) {
        result.add((DeclaredType) val.getValue());
      }
      return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T getValue(String name) throws ElementException {
      Map<? extends ExecutableElement, ? extends AnnotationValue> members =
          processingEnv.getElementUtils().getElementValuesWithDefaults(annMirror);

      for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : members
          .entrySet()) {
        if (entry.getKey().getSimpleName().toString().equals(name)) {
          return (T) entry.getValue().getValue();
        }
      }
      throw new ElementException(element,
          String.format("Cannot find @%s annotation field %s in class %s", annMirror, name,
              element.getSimpleName()));
    }
  }

  private <T extends Annotation> AnnotationHelper<T> annotationHelper(Element element,
      Class<T> annotation) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (getQualifiedName(mirror.getAnnotationType())
          .contentEquals(annotation.getCanonicalName())) {
        return new AnnotationHelper<>(element.getAnnotation(annotation), mirror, element);
      }
    }
    return null;
  }

  private static Name getQualifiedName(DeclaredType type) {
    return ((TypeElement) type.asElement()).getQualifiedName();
  }

  /**
   * Exception that we use internally to abort execution but keep a reference to the failing element
   * in order to report the error correctly.
   */
  private static final class ElementException extends Exception {

    private final Element element;

    private ElementException(Element element, String message) {
      super(message);
      this.element = element;
    }
  }
}
