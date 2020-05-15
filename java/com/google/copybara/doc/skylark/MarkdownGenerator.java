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

import static com.google.common.base.Preconditions.checkNotNull;

import com.beust.jcommander.Parameter;
import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.html.HtmlEscapers;
import com.google.copybara.doc.annotations.DocDefault;
import com.google.copybara.doc.annotations.DocDefaults;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocSignaturePrefix;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.Examples;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.StarlarkBuiltin;
import com.google.devtools.build.lib.skylarkinterface.StarlarkGlobalLibrary;
import com.google.devtools.build.lib.skylarkinterface.StarlarkMethod;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Reads classes annotated with {@link DocElement} or
 * {@link com.google.copybara.doc.annotations.DocField} and generates Markdown documentation.
 */
public class MarkdownGenerator extends BasicAnnotationProcessor {

  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  private static void mdTitle(StringBuilder sb, int level, String name) {
    sb.append("\n").append(Strings.repeat("#", level)).append(' ').append(name).append("\n\n");
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {

    return ImmutableList.of(new ProcessingStep() {
      @Override
      public Set<? extends Class<? extends Annotation>> annotations() {
        return ImmutableSet.of(StarlarkBuiltin.class, StarlarkGlobalLibrary.class);
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

    LinkedList<DocModule> modules = new LinkedList<>();
    Set<Element> globalModules = elementsByAnnotation.get(StarlarkGlobalLibrary.class);
    if (!globalModules.isEmpty()) {
      DocModule docModule = new DocModule("Globals", "Global functions available in Copybara");
      modules.add(docModule);
      for (Element element : globalModules) {
        TypeElement module = (TypeElement) element;
        for (Element member : findStarlarkMethods(module)) {
          docModule.functions.add(callableFunction((ExecutableElement) member,
              annotationHelper(member, StarlarkMethod.class), /*prefix=*/null));
        }
      }
    }

    for (Element element : elementsByAnnotation.get(StarlarkBuiltin.class)) {
      TypeElement module = (TypeElement) element;

      StarlarkBuiltin skyModule = module.getAnnotation(StarlarkBuiltin.class);
      if (!skyModule.documented()) {
        continue;
      }

      DocSignaturePrefix docSignaturePrefix = module.getAnnotation(DocSignaturePrefix.class);


      DocModule docModule = new DocModule(skyModule.name(), skyModule.doc());
      modules.add(docModule);

      for (Element member : findStarlarkMethods(module)) {
        AnnotationHelper<StarlarkMethod> ann = annotationHelper(member, StarlarkMethod.class);
        if (ann.ann.structField()) {
          docModule.fields.add(new DocField(ann.ann.name(), ann.ann.doc()));
        } else {
          docModule.functions.add(callableFunction((ExecutableElement) member, ann,
              docSignaturePrefix != null ? docSignaturePrefix.value() : skyModule.name()));
        }
      }

      docModule.flags.addAll(generateFlagsInfo(module));
    }

    for (DocModule module : modules) {
      FileObject resource = processingEnv.getFiler().createResource(
          StandardLocation.SOURCE_OUTPUT, "", module.name + ".copybara.md");

      try (Writer writer = resource.openWriter()) {
        writer.append(module.toMarkdown(2)).append("\n");
      }
    }
  }

  private DocFunction callableFunction(ExecutableElement member,
      AnnotationHelper<StarlarkMethod> callable, @Nullable String prefix) throws ElementException {

    return documentSkylarkSignature(member,
        prefix != null
            ? String.format("%s.%s", prefix, callable.ann.name())
            : callable.ann.name(),
        /*skipFirstParam=*/false, callable.ann.doc(), callable.getValue("parameters"),
        callable.ann.parameters(), skylarkTypeName(member.getReturnType()));
  }

  private List<? extends Element> findStarlarkMethods(TypeElement module) {
    TypeMirror superclass = module.getSuperclass();
    ImmutableList.Builder<Element> result = ImmutableList.builder();
    if (!(superclass instanceof NoType)) {
      Element element = processingEnv.getTypeUtils().asElement(superclass);
      if (element instanceof TypeElement) {
        result.addAll(findStarlarkMethods((TypeElement) element));
      }
    }
    result.addAll(module.getEnclosedElements().stream().filter(member -> {
      AnnotationHelper<StarlarkMethod> ann = annotationHelper(member, StarlarkMethod.class);
      return ann != null && member instanceof ExecutableElement && ann.ann.documented();
    }).collect(Collectors.toList()));
    return result.build();
  }

  private void printExample(StringBuilder sb, int level, Example example) {
    mdTitle(sb, level, example.title() + ":");
    sb.append(example.before()).append("\n\n");
    sb.append("```python\n").append(example.code()).append("\n```\n\n");
    if (!example.after().equals("")) {
      sb.append(example.after()).append("\n\n");
    }
  }

  private DocFunction documentSkylarkSignature(Element member, String functionName,
      boolean skipFirstParam, String doc, List<AnnotationValue> paramsAnnotations,
      Param[] parameters, @Nullable String returnType) throws ElementException {

    ImmutableList.Builder<DocParam> params = ImmutableList.builder();

    Map<String, String> docDefaultsMap = new HashMap<>();
    AnnotationHelper<DocDefaults> docDefaults = annotationHelper(member, DocDefaults.class);
    AnnotationHelper<DocDefault> docDefault = annotationHelper(member, DocDefault.class);

    if (docDefaults != null) {
      for (DocDefault d : docDefaults.ann.value()) {
        docDefaultsMap.put(d.field(), d.value());
      }
    } else if (docDefault != null) {
      docDefaultsMap.put(docDefault.ann.field(), docDefault.ann.value());
    }

    for (int i = skipFirstParam ? 1 : 0; i < paramsAnnotations.size(); i++) {
      AnnotationHelper<Param> param = new AnnotationHelper<>(
          parameters[i], (AnnotationMirror) paramsAnnotations.get(i).getValue(), member);
      params.add(new DocParam(
          param.ann.name(),
          docDefaultsMap.containsKey(param.ann.name())
              ? docDefaultsMap.get(param.ann.name())
              : Strings.isNullOrEmpty(param.ann.defaultValue())
                  ? null
                  : param.ann.defaultValue(),
          skylarkTypeNameGeneric(
              param.getClassValue("type"),
              param.getClassValue("generic1")),
          param.ann.doc()));
    }

    AnnotationHelper<Examples> examples = annotationHelper(member, Examples.class);
    AnnotationHelper<Example> singleExample = annotationHelper(member, Example.class);

    ImmutableList.Builder<DocExample> docExample = ImmutableList.builder();
    if (examples != null) {
      for (Example example : examples.ann.value()) {
        docExample.add(new DocExample(example));
      }
    } else if (singleExample != null) {
      docExample.add(new DocExample(singleExample.ann));
    }
    return new DocFunction(functionName, doc, returnType, params.build(), generateFlagsInfo(member),
        docExample.build());
  }

  @Nullable
  private String skylarkTypeNameGeneric(DeclaredType declared, @Nullable DeclaredType generic) {
    String name = skylarkTypeName(declared);
    if (name == null) {
      return null;
    }
    if (generic == null || generic.toString().equals("java.lang.Object")) {
      return name;
    }
    return name + " of " + skylarkTypeName(generic);
  }

  @Nullable
  private String skylarkTypeName(TypeMirror declared) {
    if (declared.toString().equals("com.google.devtools.build.lib.syntax.NoneType")
        || declared.toString().equals("void")) {
      return null;
    }
    Element element = processingEnv.getTypeUtils().asElement(declared);
    if (element == null) {
      return simplerJavaTypes(declared);
    }
    StarlarkBuiltin skyType = element.getAnnotation(StarlarkBuiltin.class);
    if (skyType == null) {
      return simplerJavaTypes(element.asType());
    }
    if (!(declared instanceof DeclaredType)) {
      return skyType.name();
    }
    DeclaredType possibleGeneric = (DeclaredType) declared;
    if (possibleGeneric.getTypeArguments().isEmpty()) {
      return skyType.name();
    }
    if (possibleGeneric.getTypeArguments().size() == 1) {
      return skyType.name() + " of "
          + skylarkTypeName(possibleGeneric.getTypeArguments().get(0));
    }
    return skyType.name() + "["
        + Joiner.on(", ")
        .join(possibleGeneric.getTypeArguments().stream().map(this::skylarkTypeName).collect(
            Collectors.toList()))
        + "]";
  }

  private ImmutableList<DocFlag> generateFlagsInfo(Element classElement) throws ElementException {
    ImmutableList.Builder<DocFlag> result = ImmutableList.builder();
    AnnotationHelper<UsesFlags> annotation = annotationHelper(classElement, UsesFlags.class);
    if (annotation == null) {
      return result.build();
    }
    for (DeclaredType flag : annotation.getClassListValue("value")) {
      Element flagClass = flag.asElement();
      for (Element member : flagClass.getEnclosedElements()) {
        Parameter flagAnnotation = member.getAnnotation(Parameter.class);
        if (flagAnnotation == null
            || !(member instanceof VariableElement)
            || flagAnnotation.hidden()) {
          continue;
        }
        result.add(new DocFlag(Joiner.on(", ").join(flagAnnotation.names()),
            simplerJavaTypes(member.asType()), flagAnnotation.description()));
      }
    }
    return result.build();
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

  private String simplerJavaTypes(TypeMirror typeMirror) {
    String s = typeMirror.toString();
    Matcher m = Pattern.compile("(?:[A-z.]*\\.)*([A-z]+)").matcher(s);
    StringBuilder sb = new StringBuilder();
    while(m.find()) {
      String replacement = deCapitalize(m.group(1));
      m.appendReplacement(sb, replacement);
    }
    m.appendTail(sb);

    return HtmlEscapers.htmlEscaper().escape(sb.toString());
  }

  private void tableHeader(StringBuilder sb, String... fields) {
    tableRow(sb, fields);
    tableRow(sb, Stream.of(fields)
        .map(e -> Strings.repeat("-", e.length()))
        .toArray(String[]::new));
  }

  private void tableRow(StringBuilder sb, String... fields) {
    sb.append(Arrays.stream(fields).map(s -> s.replace("\n", "<br>"))
        .collect(Collectors.joining(" | ")))
        .append("\n");
  }

  private final class DocModule extends DocBase {

    private final TreeSet<DocField> fields = new TreeSet<>();
    private final TreeSet<DocFunction> functions = new TreeSet<>();
    private final TreeSet<DocFlag> flags = new TreeSet<>();

    DocModule(String name, String description) {
      super(name, description);
    }

    CharSequence toMarkdown(int level) {
      StringBuilder sb = new StringBuilder();
      mdTitle(sb, level, name);
      sb.append(description).append("\n\n");

      if (!fields.isEmpty()) {
        // TODO(malcon): Skip showing in ToC for now by showing it as a more deep element.
        mdTitle(sb, level + 2, "Fields:");
        tableHeader(sb, "Name", "Description");
        for (DocField field : fields) {
          tableRow(sb, field.name, field.description);
        }
        sb.append("\n");
      }

      printFlags(sb, flags);

      for (DocFunction func : functions) {
        sb.append("<a id=\"").append(func.name).append("\" aria-hidden=\"true\"></a>");
        mdTitle(sb, level + 1, func.name);
        sb.append(func.description);
        sb.append("\n\n");
        sb.append("`");
        if (func.returnType != null) {
          sb.append(func.returnType).append(" ");
        }
        sb.append(func.name).append("(");
        Joiner.on(", ").appendTo(sb, Lists.transform(func.params,
            p -> p.name + (p.defaultValue == null ? "" : "=" + p.defaultValue)));
        sb.append(")`\n\n");

        if (!Iterables.isEmpty(func.params)) {
          mdTitle(sb, level + 2, "Parameters:");
          tableHeader(sb, "Parameter", "Description");
          for (DocParam param : func.params) {
            tableRow(sb,
                param.name,
                String.format("`%s`<br><p>%s</p>", param.type, param.description));
          }
          sb.append("\n");
        }
        if (!func.examples.isEmpty()) {
          mdTitle(sb, level + 2, func.examples.size() == 1 ? "Example:" : "Examples:");
          for (DocExample example : func.examples) {
            printExample(sb, level + 3, example.example);
          }
          sb.append("\n");
        }
        printFlags(sb, func.flags);
      }
      return sb;
    }

    private void printFlags(StringBuilder sb, Collection<DocFlag> flags) {
      if (!flags.isEmpty()) {
        sb.append("\n\n**Command line flags:**\n\n");
        tableHeader(sb, "Name", "Type", "Description");
        for (DocFlag field : flags) {
          tableRow(sb, nowrap(field.name), String.format("*%s*", field.type), field.description);
        }
        sb.append("\n");
      }
    }

    /**
     * Don't wrap this text. Also use '`' to show it as code.
     */
    private String nowrap(String text) {
      return String.format("<span style=\"white-space: nowrap;\">`%s`</span>", text);
    }
  }

  private abstract class DocBase implements Comparable<DocBase> {

    protected final String name;
    protected final String description;

    DocBase(String name, String description) {
      this.name = checkNotNull(name);
      this.description = checkNotNull(description);
    }

    @Override
    public int compareTo(DocBase o) {
      return name.compareTo(o.name);
    }

    public String getName() {
      return name;
    }
  }

  private final class DocField extends DocBase {

    DocField(String name, String description) {
      super(name, description);
    }
  }

  private final class DocFunction extends DocBase {

    private final TreeSet<DocFlag> flags = new TreeSet<>();
    @Nullable
    private final String returnType;
    private final ImmutableList<DocParam> params;
    private final ImmutableList<DocExample> examples;

    DocFunction(String name, String description, @Nullable String returnType,
        Iterable<DocParam> params, Iterable<DocFlag> flags, Iterable<DocExample> examples) {
      super(name, description);
      this.returnType = returnType;
      this.params = ImmutableList.copyOf(params);
      this.examples = ImmutableList.copyOf(examples);
      Iterables.addAll(this.flags, flags);
    }
  }

  private final class DocParam {

    private final String name;
    @Nullable
    private final String defaultValue;
    private final String type;
    private final String description;

    DocParam(String name, @Nullable String defaultValue, String type, String description) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.type = type;
      this.description = description;
    }
  }

  private final class DocFlag extends DocBase {

    public final String type;

    DocFlag(String name, String type, String description) {
      super(name, description);
      this.type = type;
    }
  }

  private final class DocExample {

    private final Example example;

    DocExample(Example example) {
      this.example = example;
    }
  }
}
