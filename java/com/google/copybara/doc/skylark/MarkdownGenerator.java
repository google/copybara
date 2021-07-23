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
import com.google.auto.common.AnnotationMirrors;
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
import com.google.copybara.doc.annotations.Library;
import com.google.copybara.doc.annotations.UsesFlags;
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
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;

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
    List<String> moduleClasses = new ArrayList<>();
    Set<Element> globalModules = elementsByAnnotation.get(Library.class);
    if (!globalModules.isEmpty()) {
      DocModule docModule = new DocModule("Globals", "Global functions available in Copybara");
      modules.add(docModule);
      for (Element element : globalModules) {
        TypeElement module = (TypeElement) element;
        moduleClasses.add(module.getQualifiedName().toString());
        for (Element member : findStarlarkMethods(module)) {
          docModule.functions.add(callableFunction((ExecutableElement) member,
              annotationHelper(member, StarlarkMethod.class), /*prefix=*/null));
        }
      }
    }
    for (Element element : elementsByAnnotation.get(StarlarkBuiltin.class)) {
      TypeElement module = (TypeElement) element;
      if (module.getNestingKind() != NestingKind.TOP_LEVEL) {
        // foo.SomeClass.Nested -> foo.SomeClass$Nested. Doesn't work for multi-nested, but we
        // don't care for now.
        moduleClasses.add(module.getQualifiedName().toString()
            .replaceFirst("\\.([^.]+)$","\\$$1"));
      } else {
        moduleClasses.add(module.getQualifiedName().toString());
      }

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

    FileObject res = processingEnv.getFiler().createResource(
        StandardLocation.SOURCE_OUTPUT, "", "starlark_class_list.txt");

    try (Writer writer = res.openWriter()) {
      writer.append(Joiner.on("\n").join(moduleClasses)).append("\n");
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

    String returnType = skylarkTypeName(member.getReturnType());
    if (returnType.equals("void") || returnType.equals("NoneType")) {
      returnType = null;
    }

    return documentSkylarkSignature(
        member,
        prefix != null ? String.format("%s.%s", prefix, callable.ann.name()) : callable.ann.name(),
        callable.ann.doc(),
        callable.ann.parameters(), // actual Param annotations
        callable.getValue("parameters"), // mirror of Param annotations
        member.getParameters(), // mirror of parameter variables
        returnType);
  }

  private ImmutableList<? extends Element> findStarlarkMethods(TypeElement module) {
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

  private DocFunction documentSkylarkSignature(
      Element member,
      String functionName,
      String doc,
      Param[] parameters, // actual Param annotations
      List<AnnotationValue> paramsAnnotations, // mirror of Param annotations
      List<? extends VariableElement> parameterVars, // mirror of parameter variables
      @Nullable String returnType)
      throws ElementException {

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

    for (int i = 0; i < paramsAnnotations.size(); i++) {
      Param paramAnnot = parameters[i];
      VariableElement paramVar = parameterVars.get(i);
      AnnotationHelper<Param> param =
          new AnnotationHelper<>(
              paramAnnot, (AnnotationMirror) paramsAnnotations.get(i).getValue(), paramVar);

      // Compute the list of names of allowed types (e.g. string or bool or NoneType).
      List<String> allowedTypeNames = new ArrayList<String>();

      // Use allowedTypes if provided.
      @SuppressWarnings("unchecked")
      List<AnnotationValue> paramTypeAnnots =
          (List<AnnotationValue>) param.getValue("allowedTypes");
      if (!paramTypeAnnots.isEmpty()) {
        for (AnnotationValue paramTypeAnnot : paramTypeAnnots) {
          AnnotationHelper<Param> paramType =
              new AnnotationHelper<>(
                  /*.ann unneeded here*/ null, (AnnotationMirror) paramTypeAnnot, paramVar);

          String typeName = skylarkTypeName(paramType.getClassValue("type"));
          DeclaredType generic1 = paramType.getClassValue("generic1");
          if (!generic1.toString().equals("java.lang.Object")) {
            typeName += " of " + skylarkTypeName(generic1);
          }
          allowedTypeNames.add(typeName);
        }
      } else {
        // Otherwise use the type of the parameter variable itself.
        String name = skylarkTypeName(paramVar.asType());
        allowedTypeNames.add(name);
      }

      params.add(
          new DocParam(
              param.ann.name(),
              docDefaultsMap.containsKey(param.ann.name())
                  ? docDefaultsMap.get(param.ann.name())
                  : Strings.isNullOrEmpty(param.ann.defaultValue())
                      ? null
                      : param.ann.defaultValue(),
              allowedTypeNames,
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

  private String skylarkTypeName(TypeMirror declared) {
    // TODO(b/171491425): This function is very fragile.
    // Mapping from a StarlarkValue subclass to its official name is the
    // job of Starlark.classType(cls)---not a trivial function---but it
    // requires loaded Java classes, so it can't be used here.
    // Perhaps the annotation processor could simply gather the names
    // of classes, and the Copybara binary (in --doc mode) could load
    // the classes and process them directly.

    String str = declared.toString();
    if (str.equals("?") || str.equals("void") || str.equals("int")) {
      return str;
    }
    if (str.equals("boolean")) {
      return "bool";
    }
    // Valid primitive types have all been dispatched.
    DeclaredType possibleGeneric = (DeclaredType) declared;

    List<? extends TypeMirror> typeArgs = possibleGeneric.getTypeArguments();
    List<String> typeArgNames = new ArrayList<>();
    boolean trivial = true; // all params are <?, Object>
    for (TypeMirror typeArg : typeArgs) {
      String name = skylarkTypeName(typeArg);
      if (!name.equals("?") && !name.equals("object")) {
        trivial = false;
      }
      typeArgNames.add(name);
    }

    String simpleName = skylarkTypeNameForClass(declared);
    if (trivial) {
      return simpleName;
    }
    if (typeArgs.size() == 1) {
      return simpleName + " of " + typeArgNames.get(0);
    }
    return simpleName + "[" + Joiner.on(", ").join(typeArgNames) + "]";
  }

  private String skylarkTypeNameForClass(TypeMirror declared) {
    String str = declared.toString();

    // Hard-code important non-annotated types
    // that Starklark.classType maps to Starlark names.
    // Add to this list if the assertion below fails.
    //
    // We use startsWith where the type may have a <..> suffix.
    // Starlark.classType uses subclass tests on Map, List.
    if (str.startsWith("net.starlark.java.eval.Sequence")) {
      return "sequence";
    } else if (str.equals("net.starlark.java.eval.StarlarkCallable")) {
      return "callable";
    } else if (str.equals("net.starlark.java.eval.StarlarkIterable")) {
      return "iterable";
    } else if (str.startsWith("java.util.Map")) {
      return "dict";
    } else if (str.startsWith("com.google.common.collect.ImmutableList")) {
      return "list";
    } else if (str.equals("java.lang.Boolean")) {
      return "bool";
    }

    // has Starlark annotation?
    Element element = processingEnv.getTypeUtils().asElement(declared);
    if (element != null) {
      StarlarkBuiltin annot = element.getAnnotation(StarlarkBuiltin.class);
      if (annot != null) {
        return annot.name();
      }

      // e.g. String, Copybara types, and Starlark interfaces:
      // Sequence<CopybaraType>, StarlarkCallable, StarlarkIterable.
      return simplerJavaTypes(element.asType().toString());
    }

    throw new IllegalArgumentException("need more heuristics for: " + str);
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
        result.add(
            new DocFlag(
                Joiner.on(", ").join(flagAnnotation.names()),
                simplerJavaTypes(member.asType().toString()),
                flagAnnotation.description()));
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
      throw new ElementException(
          element,
          String.format(
              "Cannot find @%s annotation field %s in class %s",
              AnnotationMirrors.toString(annMirror), name, element.getSimpleName()));
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

  private String simplerJavaTypes(String s) {
    Matcher m = Pattern.compile("(?:[A-z.]*\\.)*([A-z]+)").matcher(s);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
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
            tableRow(
                sb,
                param.name,
                String.format(
                    "`%s`<br><p>%s</p>",
                    Joiner.on("` or `").join(param.allowedTypes), param.description));
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
    private final List<String> allowedTypes;
    private final String description;

    DocParam(
        String name, @Nullable String defaultValue, List<String> allowedTypes, String description) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.allowedTypes = allowedTypes;
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
