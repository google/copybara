package com.google.copybara;

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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.copybara.exception.ValidationException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRulesException;
import java.util.Objects;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.HasBinary;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.syntax.TokenKind;

/** Starlark wrapper for java's Zoned Datetime */
@StarlarkBuiltin(name = "datetime", doc = "Module for datetime manipulation.")
public class StarlarkDateTimeModule implements StarlarkValue {

  @StarlarkMethod(
      name = "now",
      doc = "Returns a starlark_datetime object. The object is timezone aware.",
      parameters = {
        @Param(
            name = "tz",
            defaultValue = "'America/Los_Angeles'",
            doc = "The timezone. E.g. America/New_York, Asia/Tokyo, Europe/Rome",
            allowedTypes = {@ParamType(type = String.class)},
            named = true)
      })
  public StarlarkDateTime createFromNow(String zoneIdString) throws ValidationException {
    return new StarlarkDateTime(zoneIdString);
  }

  @StarlarkMethod(
      name = "fromtimestamp",
      doc =
          "Returns a starlark_datetime object representation of the epoch time. The object is"
              + " timezone aware.",
      parameters = {
        @Param(
            name = "timestamp",
            defaultValue = "0",
            doc = "Epoch time in seconds.",
            allowedTypes = {@ParamType(type = StarlarkInt.class)},
            named = true),
        @Param(
            name = "tz",
            defaultValue = "'America/Los_Angeles'",
            doc = "The timezone. E.g. America/New_York, Asia/Tokyo, Europe/Rome, etc.",
            allowedTypes = {@ParamType(type = String.class)},
            named = true)
      })
  public StarlarkDateTime createFromEpochSeconds(StarlarkInt timeInEpochSeconds, String zoneId)
      throws ValidationException, EvalException {
    return new StarlarkDateTime(timeInEpochSeconds.toLong(null), zoneId);
  }

  /** The StarLark facing wrapper for ZonedDateTime */
  @SuppressWarnings("ZonedDateTimeNowWithZone")
  @StarlarkBuiltin(name = "StarlarkDateTime", doc = "Starlark datetime object")
  public static class StarlarkDateTime implements StarlarkValue, HasBinary {

    private final ZonedDateTime zonedDateTime;

    public StarlarkDateTime(String zoneIdString) throws ValidationException {
      ZoneId zoneId = convertStringToZoneId(zoneIdString);
      this.zonedDateTime = ZonedDateTime.now(zoneId);
    }

    @SuppressWarnings("GoodTime")
    public StarlarkDateTime(long timeInEpochSeconds, String zoneIdString)
        throws ValidationException {
      ZoneId zoneId = convertStringToZoneId(zoneIdString);
      this.zonedDateTime = Instant.ofEpochSecond(timeInEpochSeconds).atZone(zoneId);
    }

    @Override
    public final Object binaryOp(TokenKind operator, Object rightSideOperand, boolean thisLeft)
        throws EvalException {
      Preconditions.checkArgument(
          rightSideOperand instanceof StarlarkDateTime,
          "Binary operators are supported between StarkDateTime objects only.");
      StarlarkDateTime otherDateTime = (StarlarkDateTime) rightSideOperand;
      switch (operator) {
        case MINUS:
          return new StarlarkTimeDelta(
              ChronoUnit.SECONDS.between(otherDateTime.zonedDateTime, zonedDateTime));
          // TODO(linjordan) - PLUS between StarklarkDatetime and StarlarkTimeDelta in the future
        default:
          throw new EvalException(String.format("Glob does not support %s operator", operator));
      }
    }

    private ZoneId convertStringToZoneId(String zoneIdString) throws ValidationException {
      try {
        return (Strings.isNullOrEmpty(zoneIdString)
            ? ZoneId.systemDefault()
            : ZoneId.of(zoneIdString));
      } catch (ZoneRulesException e) {
        throw new ValidationException(
            String.format(
                "An error was thrown creating StarlarkDateTime from zone id. Make sure your"
                    + " timezone is available in %s",
                ZoneId.getAvailableZoneIds()),
            e);
      }
    }

    @SuppressWarnings("GoodTime")
    @StarlarkMethod(
        name = "in_epoch_seconds",
        doc = "Returns the time in epoch seconds for the starlark_datetime instance")
    public long getTimeInEpochSeconds() {
      return this.zonedDateTime.toEpochSecond();
    }

    @SuppressWarnings("GoodTime")
    @StarlarkMethod(
        name = "strftime",
        doc =
            "Returns a string representation of the StarlarkDateTime object with your chosen"
                + " formatting",
        parameters = {
          @Param(
              name = "format",
              doc =
                  "Format string used to present StarlarkDateTime object. See"
                      + " https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html"
                      + " for patterns.",
              allowedTypes = {@ParamType(type = String.class)},
              named = true),
        })
    public String formatToString(String format) throws ValidationException {
      try {
        return zonedDateTime.format(DateTimeFormatter.ofPattern(format));
      } catch (DateTimeException | IllegalArgumentException e) {
        throw new ValidationException(
            String.format(
                "The StarlarkDateTime object '%s' could not be formatted using format string '%s':",
                this, format),
            e);
      }
    }

    @Override
    public String toString() {
      return this.zonedDateTime.toString();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof StarlarkDateTime)) {
        return false;
      }
      return obj == this || this.zonedDateTime.equals(((StarlarkDateTime) obj).zonedDateTime);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(this.zonedDateTime);
    }
  }

  /** Time delta, used to do binary operations with Starlark Datetime */
  public static class StarlarkTimeDelta implements StarlarkValue {
    private final Duration duration;

    /** Breaks StarlarkTimeDelta into days, hours, minutes, and seconds */
    @SuppressWarnings("GoodTime")
    public StarlarkTimeDelta(long seconds) {
      duration = Duration.ofSeconds(seconds);
    }

    @SuppressWarnings("GoodTime")
    @StarlarkMethod(name = "total_seconds", doc = "Total number of seconds in a timedelta object.")
    public long totalSeconds() {
      return duration.getSeconds();
    }

    // TODO(linjordan) - implement timedelta + StarlarkDatetime
  }
}
