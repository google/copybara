package com.google.copybara.config;

import com.beust.jcommander.internal.Nullable;
import com.google.common.base.Joiner;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;

/**
 * Utilities for dealing with Skylark parameter objects and converting them to Java ones.
 */
public final class SkylarkUtil {

  private SkylarkUtil() {
  }

  /**
   * Converts an object that can be the NoneType to the actual object if it is not
   * or returns the default value if none.
   */
  @SuppressWarnings("unchecked")
  public static <T> T convertFromNoneable(Object obj, @Nullable T defaultValue) {
    if (EvalUtils.isNullOrNone(obj)) {
      return defaultValue;
    }
    return (T) obj;
  }

  /**
   * Converts a string to the corresponding enum or fail if invalid value.
   *
   * @param location location of the skylark element requesting the conversion
   * @param fieldName name of the field to convert
   * @param value value to convert
   * @param enumType the type class of the enum to use for conversion
   * @param <T> the enum class
   */
  public static <T extends Enum<T>> T stringToEnum(Location location, String fieldName,
      String value, Class<T> enumType) throws EvalException {
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      throw new EvalException(location,
          String.format("Invalid value '%s' for field '%s'. Valid values are: %s", value, fieldName,
              Joiner.on(", ").join(enumType.getEnumConstants())));
    }
  }

  /**
   * Checks that a mandatory string field is not empty.
   */
  public static String checkNotEmpty(String value, String name, Location location)
      throws EvalException {
    if (value.isEmpty()) {
      throw new EvalException(location, String.format("Invalid empty field '%s'.", name));
    }
    return value;
  }
}
