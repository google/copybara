// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.base.Preconditions;
import com.google.copybara.Options;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.Workflow;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.ConstructorException;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.MethodProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A YAML parser for the configuration.
 */
public final class YamlParser {

  // An instance of the snakeyaml reader which doesn't do any implicit conversions.
  private final Yaml yaml;

  public YamlParser(Iterable<TypeDescription> typeDescriptions) {
    TypeDescription top = new TypeDescription(Config.Yaml.class);
    top.putListPropertyType("workflows", Workflow.Yaml.class);

    Constructor constructor = new Constructor(top);
    constructor.setPropertyUtils(new CopybaraPropertyUtils());

    for (TypeDescription typeDescription : typeDescriptions) {
      constructor.addTypeDescription(typeDescription);
    }
    this.yaml = new Yaml(constructor, new Representer(), new DumperOptions(), new Resolver());
  }

  /**
   * Creates a TypeDescription that extracts the Yaml name to be used from the {@link DocElement}
   * annotation so that it doesn't need to be repeated.
   */
  public static TypeDescription docTypeDescription(Class<?> clazz) {
    DocElement annotation = Preconditions
        .checkNotNull(clazz.getAnnotation(DocElement.class),
            "%s class is not annotated with @%s",
            clazz.getName(), DocElement.class.getName());
    return new TypeDescription(clazz, annotation.yamlName());
  }

  /**
   * Load a YAML content, configure it with the program {@code Options} and return a {@link Config}
   * object.
   *
   * @param path a file representing a YAML Copybara configuration
   * @param options the options passed to the Copybara command
   * @throws NoSuchFileException in case the config file cannot be found
   * @throws IOException if the config file cannot be load
   */
  public Config loadConfig(Path path, Options options)
      throws IOException, NoSuchFileException, ConfigValidationException {
    String configContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    // TODO(matvore): The exceptions printed as a result of a bad configuration are hard to read.
    // It can include a long stack trace plus a nested cause. Find a way to make the error output
    // more digestable.
    Config.Yaml load;
    try {
      load = (Config.Yaml) yaml.load(configContent);
    } catch (ConstructorException e) {
      throw new ConfigValidationException("Error loading '" + path + "' configuration file", e);
    }
    if (load == null) {
      throw new ConfigValidationException("Configuration file '" + path + "' is empty");
    }
    return load.withOptions(options);
  }

  /**
   * A PropertyUtils that is able to detect generics mismatches when calling the setters.
   *
   */
  private class CopybaraPropertyUtils extends PropertyUtils {

    private final Map<Class<?>, Map<String, Property>> propertiesCache = new HashMap<>();

    /**
     * This method is almost an exact copy of its super method except for a call to
     * {@code new GenericMethodProperty} instead of {@code new MethodProperty}.
     */
    protected Map<String, Property> getPropertiesMap(Class<?> type, BeanAccess bAccess)
        throws IntrospectionException {
      if (bAccess != BeanAccess.DEFAULT) {
        throw new IllegalStateException(
            "Only setter properties are allowed. " + bAccess + " not supported");
      }

      if (propertiesCache.containsKey(type)) {
        return propertiesCache.get(type);
      }

      Map<String, Property> properties = new LinkedHashMap<>();
      // add JavaBean properties
      for (PropertyDescriptor property :
          Introspector.getBeanInfo(type).getPropertyDescriptors()) {
        Method readMethod = property.getReadMethod();
        if (readMethod == null || !readMethod.getName().equals("getClass")) {
          properties.put(property.getName(), new GenericMethodProperty(property));
        }
      }

      propertiesCache.put(type, properties);
      return properties;
    }
  }

  /**
   * A {@link MethodProperty} that can check that the elements of generic types are assignable to
   * what the setter methods are expecting.
   */
  private class GenericMethodProperty extends MethodProperty {

    private final PropertyDescriptor property;

    GenericMethodProperty(PropertyDescriptor property) {
      super(property);
      this.property = property;
    }

    @Override
    public void set(Object object, Object value) throws Exception {
      Method method = property.getWriteMethod();
      Type type = method.getGenericParameterTypes()[0];
      // Validate for simple generic types that the iterables elements are assignable
      // to what the method is expecting.
      if (isIterable(type)) {
        ParameterizedType parameterized = (ParameterizedType) type;
        Type innerType = parameterized.getActualTypeArguments()[0];
        // setters for types like List<String>
        if (innerType instanceof Class) {
          validateElements((Iterable<?>) value, (Class<?>) innerType);
        } else if (innerType instanceof WildcardType) {
          // setters for types like List<? extends Transformer>
          WildcardType variable = (WildcardType) innerType;
          if (variable.getUpperBounds().length == 1
              && variable.getUpperBounds()[0] instanceof Class) {
            validateElements((Iterable<?>) value, (Class<?>) variable.getUpperBounds()[0]);
          }
        }
      }
      super.set(object, value);
    }

    /**
     * Checks that the elements of {@code iterable} are of type {@code elementsType}
     */
    private void validateElements(Iterable<?> iterable, Class<?> elementsType)
        throws ConfigValidationException {
      int idx = 0;
      for (Object element : iterable) {
        if (!elementsType.isAssignableFrom(element.getClass())) {
          throw new ConfigValidationException("sequence field '"
              + property.getName() + "' expects elements of type '" + prettyType(elementsType)
              + "', but " + property.getName() + "[" + idx + "] is of type "
              + "'" + prettyType(element.getClass()) + "' (value = " + element + ")");
        }
        idx++;
      }
    }
  }

  private String prettyType(Class<?> detectedType) {
    // Basic java types simplification.
    if (detectedType.getName().startsWith("java.lang.")) {
      return Introspector.decapitalize(detectedType.getSimpleName());
    } else if (detectedType.getName().endsWith("$Yaml")) {
      // Replace ugly 'com.google.copybara.Something$Yaml' like names with 'Something'
      return detectedType.getEnclosingClass().getSimpleName();
    }

    return detectedType.getName();
  }

  private static boolean isIterable(Type type) {
    return type instanceof ParameterizedType
        && Iterable.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType());
  }
}
