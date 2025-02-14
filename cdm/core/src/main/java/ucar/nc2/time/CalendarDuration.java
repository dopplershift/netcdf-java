/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.time;

import org.joda.time.Period;
import javax.annotation.concurrent.Immutable;

/**
 * A replacement for ucar.nc2.units.TimeDuration. incomplete.
 *
 *
 * A Duration is a fixed number of seconds.
 * I think thats wrong: a CalendarDuration should be a integer and a CalendarPeriod.
 * Optionally, a CalendarPeriod could have an integer value.
 *
 * Implements the thredds "duration" XML element type: specifies a length of time.
 * This is really the same as a ucar.nc2.units.TimeUnit, but it allows xsd:duration syntax as well as udunits syntax.
 * It also keeps track if the text is empty.
 * <p/>
 * A duration can be one of the following:
 * <ol>
 * <li>a valid udunits string compatible with "secs"
 * <li>an xsd:duration type specified in the following form "PnYnMnDTnHnMnS" where:
 * <ul>
 * <li>P indicates the period (required)
 * <li>nY indicates the number of years
 * <li>nM indicates the number of months
 * <li>nD indicates the number of days
 * <li>T indicates the start of a time section (required if you are going to specify hours, minutes, or seconds)
 * <li>nH indicates the number of hours
 * <li>nM indicates the number of minutes
 * <li>nS indicates the number of seconds
 * </ul>
 * </ol>
 *
 * <p>
 * change to JSR-310 javax.time.Duration when ready
 * relation to javax.xml.datatype ?
 *
 * @author john caron
 * @see "http://www.unidata.ucar.edu/projects/THREDDS/tech/catalog/InvCatalogSpec.html#durationType"
 */

@Immutable
public class CalendarDuration {

  /**
   * Convert a time udunit string
   * 
   * @param value number of units
   * @param udunit msec, sec, minute, hour, hr, day, week, month, year (plural form ok)
   * @return joda Period
   */
  static org.joda.time.Period convertToPeriod(int value, String udunit) {
    if (udunit.endsWith("s"))
      udunit = udunit.substring(0, udunit.length() - 1);

    switch (udunit) {
      case "msec":
        return Period.millis(value);
      case "sec":
        return Period.seconds(value);
      case "minute":
        return Period.minutes(value);
      case "hour":
      case "hr":
        return Period.hours(value);
      case "day":
        return Period.days(value);
      case "week":
        return Period.weeks(value);
      case "month":
        return Period.months(value);
      case "year":
        return Period.years(value);
    }

    throw new IllegalArgumentException("cant convert " + udunit + " to Joda Period");
  }

  /////////////////////////////////////////////////////////////////////

  // private final String text;
  private final CalendarPeriod.Field timeUnit;
  // private final boolean isBlank;
  private final double value;
  // private org.joda.time.Period jperiod;

  public CalendarDuration(int value, CalendarPeriod.Field timeUnit) {
    this.value = value;
    this.timeUnit = timeUnit;
  }

  public CalendarPeriod.Field getTimeUnit() {
    return timeUnit;
  }

  public static CalendarDuration fromUnitString(String unitString) {
    return new CalendarDuration(1, CalendarPeriod.fromUnitString(unitString));
  }

  /*
   * Construct from 1) udunit time udunit string, 2) xsd:duration syntax, 3) blank string.
   *
   * @param text parse this text.
   * 
   * @throws java.text.ParseException if invalid text.
   *
   * public CalendarDuration(String text) throws java.text.ParseException {
   * /* TimeUnit tunit = null;
   * text = (text == null) ? "" : text.trim();
   * this.text = text;
   * this.isBlank = (text == null) || text.length() == 0;
   * 
   * // see if its blank
   * if (isBlank) {
   * try {
   * tunit = new TimeUnit("1 sec");
   * } catch (Exception e) { // cant happen
   * }
   * 
   * } else {
   * // see if its a udunits string
   * try {
   * tunit = new TimeUnit(text);
   * 
   * if (debug) System.out.println(" set time udunit= " + timeUnit);
   * }
   * 
   * catch (Exception e) {
   * // see if its a xsd:duration
   * /*
   * A time span as defined in the W3C XML Schema 1.0 specification:
   * "PnYnMnDTnHnMnS, where nY represents the number of years, nM the number of months, nD the number of days,
   * 'T' is the date/time separator, nH the number of hours, nM the number of minutes and nS the number of seconds.
   * The number of seconds can include decimal digits to arbitrary precision."
   *
   * try {
   * javax.xml.datatype.DatatypeFactory factory = javax.xml.datatype.DatatypeFactory.newInstance();
   * javax.xml.datatype.Duration d = factory.newDuration(text);
   * long secs = d.getTimeInMillis(new Date()) / 1000;
   * tunit = new TimeUnit(secs + " secs");
   * } catch (Exception e1) {
   * throw new java.text.ParseException(e1.getMessage(), 0);
   * }
   * }
   * }
   * 
   * timeUnit = tunit;
   * duration = tunit.toDurationStandard();
   * }
   */

  /**
   * Get the duration in natural units, ie units of getTimeUnit()
   * 
   * @return the duration in natural units
   */
  public double getValue() {
    return value;
  }

  /*
   * Get the duration in seconds
   * 
   * @return the duration in seconds
   *
   * public double getValueInMillisecs() {
   * if (timeUnit == CalendarPeriod.Millisec)
   * return value;
   * else if (timeUnit == CalendarPeriod.Second)
   * return 1000 * value;
   * else if (timeUnit == CalendarPeriod.Minute)
   * return 60 * 1000 * value;
   * else if (timeUnit == CalendarPeriod.Hour)
   * return 60 * 60 * 1000 * value;
   * else if (timeUnit == CalendarPeriod.Day)
   * return 24 * 60 * 60 * 1000 * value;
   * else if (timeUnit == CalendarPeriod.Month)
   * return 30.0 * 24.0 * 60.0 * 60.0 * 1000.0 * value;
   * else if (timeUnit == CalendarPeriod.Year)
   * return 365.0 * 24.0 * 60.0 * 60.0 * 1000.0 * value;
   * 
   * else throw new IllegalStateException();
   * }
   * 
   * public double getValueInSeconds() {
   * if (timeUnit == CalendarPeriod.Millisec)
   * return value/1000;
   * else if (timeUnit == CalendarPeriod.Second)
   * return value;
   * else if (timeUnit == CalendarPeriod.Minute)
   * return 60 * value;
   * else if (timeUnit == CalendarPeriod.Hour)
   * return 60 * 60 * value;
   * else if (timeUnit == CalendarPeriod.Day)
   * return 24 * 60 * 60 * value;
   * else if (timeUnit == CalendarPeriod.Month)
   * return 30.0 * 24.0 * 60.0 * 60.0 * value;
   * else if (timeUnit == CalendarPeriod.Year)
   * return 365.0 * 24.0 * 60.0 * 60.0 * value;
   * 
   * else throw new IllegalStateException();
   * }
   * 
   * public double getValueInHours() {
   * if (timeUnit == CalendarPeriod.Millisec)
   * return value/1000/60/60;
   * else if (timeUnit == CalendarPeriod.Second)
   * return value/60/60;
   * else if (timeUnit == CalendarPeriod.Minute)
   * return value/60;
   * else if (timeUnit == CalendarPeriod.Hour)
   * return value;
   * else if (timeUnit == CalendarPeriod.Day)
   * return 24 * value;
   * else if (timeUnit == CalendarPeriod.Month)
   * return 30.0 * 24.0 * value;
   * else if (timeUnit == CalendarPeriod.Year)
   * return 365.0 * 24.0 * value;
   * 
   * else throw new IllegalStateException();
   * }
   * 
   * public double getValueInDays() {
   * if (timeUnit == CalendarPeriod.Millisec)
   * return value/1000/60/60/24;
   * else if (timeUnit == CalendarPeriod.Second)
   * return value/60/60/24;
   * else if (timeUnit == CalendarPeriod.Minute)
   * return value/60/24;
   * else if (timeUnit == CalendarPeriod.Hour)
   * return value/24;
   * else if (timeUnit == CalendarPeriod.Day)
   * return value;
   * else if (timeUnit == CalendarPeriod.Month)
   * return 30.0 * value;
   * else if (timeUnit == CalendarPeriod.Year)
   * return 365.0 * value;
   * 
   * else throw new IllegalStateException();
   * }
   */

  /*
   * If this is a blank string
   * 
   * @return true if this is a blank string
   *
   * public boolean isBlank() {
   * return isBlank;
   * }
   * 
   * /*
   * Get the String text
   * 
   * @return the text
   *
   * public String getText() {
   * return text == null ? timeUnit.toString() : text;
   * }
   */

  /**
   * String representation
   * 
   * @return getText()
   */
  public String toString() {
    return timeUnit.toString();
  }

  /*
   * Override to be consistent with equals
   *
   * public int hashCode2() {
   * return isBlank() ? 0 : (int) getValueInSeconds();
   * }
   * 
   * /*
   * TimeDurations with same value in seconds are equals
   *
   * public boolean equals2(Object o) {
   * if (this == o)
   * return true;
   * if (!(o instanceof CalendarDuration))
   * return false;
   * 
   * CalendarDuration to = (CalendarDuration) o;
   * return to.getValueInMillisecs() == getValueInMillisecs();
   * }
   */

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CalendarDuration that = (CalendarDuration) o;

    if (Double.compare(that.value, value) != 0)
      return false;
    return timeUnit == that.timeUnit;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = timeUnit != null ? timeUnit.hashCode() : 0;
    temp = value != +0.0d ? Double.doubleToLongBits(value) : 0L;
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }
}
