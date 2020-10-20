package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.ie.QuantifiableEntityNormalizer;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents dates and times according to ISO8601 standard while also allowing for
 * wild cards - e.g., can represent "21 June" without a year.
 * (Standard ISO8601 only allows removing less precise annotations (e.g.,
 * 200706 rather than 20070621 but not a way to represent 0621 without a year.)
 *
 * Format stores date and time separately since the majority of current use
 * cases involve only one of these items.  Standard ISO 8601 instead
 * requires &lt;date&gt;T&lt;time&gt;.
 *
 * Ranges are specified within the strings via forward slash.  For example
 * 6 June - 8 June is represented ****0606/****0608.  6 June onward is
 * ****0606/ and until 8 June is /****0608.
 *
 * @author Anna Rafferty
 *         TODO: add time support - currently just dates are supported
 */
public class ISODateInstance  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ISODateInstance.class);

  private static final boolean DEBUG = false;
  private ArrayList<String> tokens = new ArrayList<>(); //each token contains some piece of the date, from our input.

  public static final String OPEN_RANGE_AFTER = "A";
  public static final String OPEN_RANGE_BEFORE = "B";
  public static final String BOUNDED_RANGE = "C";
  public static final String NO_RANGE = "";
  public static final int DAY_OF_HALF_MONTH = 15;
  public static final int LAST_DAY_OF_MONTH = 31;//close enough for our purposes
  public static final String MONTH_OF_HALF_YEAR = "07";
  public static final String LAST_MONTH_OF_YEAR = "12";
  /**
   * String of the format {@literal <year><month><day>}.  Representations
   * by week are also allowed. If a more general field (such as year)
   * is not specified when a less general one (such as month) is, the characters
   * normally filled by the more general field are replaced by asterisks. For example,
   * 21 June would be \"****0621\".  Less general fields are simply truncated;
   * for example, June 2007 would be \"200706\".
   */
  private String isoDate = "";

  //Variable for marking if we were unable to parse the string associated with this isoDate
  private boolean unparseable = false;

  //private String isoTime = "";


  /**
   * Creates an empty date instance; you probably
   * don't want this in most cases.
   */
  public ISODateInstance() {
  }

  /**
   * Takes a string that represents a date, and attempts to
   * normalize it into ISO 8601-compatible format.
   */
  public ISODateInstance(String date) {
    extractFields(date);
  }

  public ISODateInstance(String date, String openRangeMarker) {
    extractFields(date);
    //now process the range marker; if a range was found independently, we ignore the marker
    if ( ! ISODateInstance.NO_RANGE.equals(openRangeMarker) && ! isoDate.contains("/")) {
      if (ISODateInstance.OPEN_RANGE_AFTER.equals(openRangeMarker)) {
        isoDate = isoDate + '/';
      } else if (ISODateInstance.OPEN_RANGE_BEFORE.equals(openRangeMarker)) {
        isoDate = '/' + isoDate;
      }
    }
  }

  /**
   * Constructor for a range of dates, beginning at date start and finishing at date end
   *
   */
  public ISODateInstance(ISODateInstance start, ISODateInstance end) {
    String startString = start.getDateString();
    if (start.isRange()) {
      startString = start.getStartDate();
    }
    String endString = end.getDateString();
    if (end.isRange()) {
      endString = end.getEndDate();
    }

    isoDate = startString + '/' + endString;
    unparseable = (start.isUnparseable() || end.isUnparseable());
  }

  /**
   * Construct a new ISODate based on its relation to a referenceDate.
   * relativeDate should be something like "today" or "tomorrow" or "last year"
   * and the resulting ISODate will be the same as the referenceDate, a day later,
   * or a year earlier, respectively.
   *
   */
  public ISODateInstance(ISODateInstance referenceDate, String relativeDate) {
    Pair<DateField, Integer> relation = relativeDateMap.get(relativeDate.toLowerCase());
    if (relation != null) {
      switch (relation.first()) {
        case DAY:
          incrementDay(referenceDate, relation);
          break;
        case MONTH:
          incrementMonth(referenceDate, relation);
          break;
        case YEAR:
          incrementYear(referenceDate, relation);
          break;
      }
    }
  }


  private void incrementYear(ISODateInstance referenceDate, Pair<DateField, Integer> relation) {
    String origDateString = referenceDate.getStartDate();
    String yearString = origDateString.substring(0, 4);
    if (yearString.contains("*")) {
      isoDate = origDateString;
      return;
    }
    isoDate = makeStringYearChange(origDateString, Integer.parseInt(yearString) + relation.second());
  }

  private void incrementMonth(ISODateInstance referenceDate, Pair<DateField, Integer> relation) {
    String origDateString = referenceDate.getStartDate();
    String monthString = origDateString.substring(4, 6);
    if (monthString.contains("*")) {
      isoDate = origDateString;
      return;
    }
    //Month is not a variable
    Integer monthNum = Integer.parseInt(monthString);
    //Check if we're an edge case
    if (((monthNum + relation.second()) > 12) || ((monthNum + relation.second) < 1)) {
      boolean decreasing = ((monthNum + relation.second) < 1);
      int newMonthNum = (monthNum + relation.second()) % 12;
      if (newMonthNum < 0) {
        newMonthNum *= -1;
      }
      //Set the month appropriately
      isoDate = makeStringMonthChange(origDateString, newMonthNum);
      //Increment the year if possible
      String yearString = origDateString.substring(0, 4);
      if (!yearString.contains("*")) {
        //How much we increment depends on above mod
        int numYearsToIncrement = (int) Math.ceil(relation.second() / 12.0);
        if (decreasing) {
          isoDate = makeStringYearChange(isoDate, Integer.parseInt(yearString) - numYearsToIncrement);
        } else {
          isoDate = makeStringYearChange(isoDate, Integer.parseInt(yearString) + numYearsToIncrement);
        }
      }
    } else {
      isoDate = makeStringMonthChange(origDateString, (monthNum + relation.second()));
    }
  }


  private void incrementDay(ISODateInstance referenceDate, Pair<DateField, Integer> relation) {
    String origDateString = referenceDate.getStartDate();
    String dayString = origDateString.substring(origDateString.length() - 2, origDateString.length());
    if (dayString.contains("*")) {
      isoDate = origDateString;
      return;
    }
    //Date is not a variable
    Integer dayNum = Integer.parseInt(dayString);
    String monthString = origDateString.substring(origDateString.length() - 4, origDateString.length() - 2);
    int numDaysInMonth = 30;//default - assume this if month is a variable
    int monthNum = -1;//ie, we don't know the month yet - this remains -1 if the month is a variable
    if (!monthString.contains("*")) {
      //Set appropriate numDaysInMonth and monthNum
      monthNum = Integer.parseInt(monthString);
      numDaysInMonth = daysPerMonth.get(monthNum);
    }

    //Now, find out if we're an edge case (potential to increment month)
    if (dayNum + relation.second() <= numDaysInMonth && dayNum + relation.second() >= 1) {
      //Not an edge case - just increment the day, create a new string, and return
      dayNum += relation.second();
      isoDate = makeStringDayChange(origDateString, dayNum);
      return;
    }

    //Since we're an edge case, the month can't be a variable - if it is a variable, just set this to the reference string
    if (monthNum == -1) {
      isoDate = origDateString;
      return;
    }
    //At this point, neither our day nor our month is a variable
    isoDate = origDateString;
    boolean decreasing = (dayNum + relation.second() < 1);
    //Need to increment the month, set the date appropriately - we need the new month num to set the day appropriately, so do month first
    int newMonthNum;
    //Now, check if we're an edge case for month
    if ((monthNum + 1 > 12 && !decreasing) || (monthNum - 1 < 1 && decreasing)) {
      //First, change the month
      if (decreasing) {
        newMonthNum = 12;
      } else {
        newMonthNum = 1;
      }
      //If we can, increment the year
      //TODO: fix this to work more nicely with variables and thus handle more cases
      String yearString = origDateString.substring(0, 4);
      if (!yearString.contains("*")) {
        if (decreasing) {
          isoDate = makeStringYearChange(isoDate, Integer.parseInt(yearString) - 1);
        } else {
          isoDate = makeStringYearChange(isoDate, Integer.parseInt(yearString) + 1);
        }
      }
    } else {
      //We're not an edge case for month - just increment
      if (decreasing) {
        newMonthNum = monthNum - 1;
      } else {
        newMonthNum = monthNum + 1;
      }
    }
    //do the increment
    isoDate = makeStringMonthChange(isoDate, newMonthNum);
    int newDateNum;
    if (decreasing) {
      newDateNum = -relation.second() + daysPerMonth.get(newMonthNum) - dayNum;
    } else {
      newDateNum = relation.second() - dayNum + daysPerMonth.get(monthNum);
    }
    //Now, change the day in our original string to be appropriate
    isoDate = makeStringDayChange(isoDate, newDateNum);


  }

  /**
   * Changes the day portion of the origDate String to be the String
   * value of newDay in two character format. (e.g., 9 -> "09")
   *
   */
  private static String makeStringDayChange(String origDate, int newDay) {
    String newDayString = (newDay < 10 ? ("0" + newDay) : String.valueOf(newDay));
    return origDate.substring(0, origDate.length() - 2) + newDayString;
  }

  /**
   * Changes the month portion of the origDate String to be the String
   * value of newDay in two character format. (e.g., 9 -> "09")
   *
   */
  private static String makeStringMonthChange(String origDate, int newMonth) {
    String newMonthString = (newMonth < 10 ? ("0" + newMonth) : String.valueOf(newMonth));
    return origDate.substring(0, 4) + newMonthString + origDate.substring(6, 8);
  }

  /**
   * Changes the year portion of the origDate String to be the String
   * value of newDay in two character format. (e.g., 9 -> "09")
   *
   */
  private static String makeStringYearChange(String origDate, int newYear) {
    String newYearString = String.valueOf(newYear);
    while (newYearString.length() < 4) {
      newYearString = '0' + newYearString;//we're compatible with year 1!
    }
    return newYearString + origDate.substring(4, origDate.length());
  }


  /**
   * Enum for the fields *
   */
  public static enum DateField {
    DAY, MONTH, YEAR
  }


  /**
   * Map for mapping a relativeDate String to a pair with the field that should be modified and the amount to modify it *
   */
  public static final Map<String, Pair<DateField, Integer>> relativeDateMap = Generics.newHashMap();

  static {
    //Add entries to the relative datemap
    relativeDateMap.put("today", new Pair<>(DateField.DAY, 0));
    relativeDateMap.put("tomorrow", new Pair<>(DateField.DAY, 1));
    relativeDateMap.put("yesterday", new Pair<>(DateField.DAY, -1));


  }

  public static final Map<Integer, Integer> daysPerMonth = Generics.newHashMap();

  static {
    //Add month entries
    daysPerMonth.put(1, 31);
    daysPerMonth.put(2, 28);
    daysPerMonth.put(3, 31);
    daysPerMonth.put(4, 30);
    daysPerMonth.put(5, 31);
    daysPerMonth.put(6, 30);
    daysPerMonth.put(7, 31);
    daysPerMonth.put(8, 31);
    daysPerMonth.put(9, 30);
    daysPerMonth.put(10, 31);
    daysPerMonth.put(11, 30);
    daysPerMonth.put(12, 31);
  }

  /**
   * Takes a string already formatted in ISODateInstance format
   * (such as one previously written out using toString) and creates
   * a new date instance from it
   *
   */
  public static ISODateInstance fromDateString(String date) {
    ISODateInstance d = new ISODateInstance();
    d.isoDate = date;
    return d;
  }

  public String toString() {
    return isoDate;
  }

  /**
   * Provided for backwards compatibility with DateInstance;
   * returns the same thing as toString()
   *
   */
  public String getDateString() {
    return this.toString();
  }

  /**
   * Uses regexp matching to match  month, day, and year fields.
   * TODO: Find a way to mark what's already been handled in the string
   */
  private boolean extractFields(String inputDate) {

    if (tokens.size() < 2) {
      tokenizeDate(inputDate);
    }
    if (DEBUG) {
      log.info("Extracting date: " + inputDate);
    }
    //first we see if it's a hyphen and two parseable dates - if not, we treat it as one date
    Pair<String, String> dateEndpoints = getRangeDates(inputDate);
    if (dateEndpoints != null) {
      ISODateInstance date1 = new ISODateInstance(dateEndpoints.first());
      if (dateEndpoints.first().contains(" ") && !dateEndpoints.second().contains(" ")) {
        //consider whether it's a leading modifier; e.g., "June 8-10" will be split into June 8, and 10 when really we'd like June 8 and June 10
        String date = dateEndpoints.first().substring(0, dateEndpoints.first().indexOf(' ')) + ' ' + dateEndpoints.second();
        ISODateInstance date2 = new ISODateInstance(date);
        if (!date1.isUnparseable() && !date2.isUnparseable()) {
          isoDate = (new ISODateInstance(date1, date2)).getDateString();
          return true;
        }
      }

      ISODateInstance date2 = new ISODateInstance(dateEndpoints.second());
      if (!date1.isUnparseable() && !date2.isUnparseable()) {
        isoDate = (new ISODateInstance(date1, date2)).getDateString();
        return true;
      }
    }

    if (extractYYYYMMDD(inputDate)) {
      return true;
    }
    if (extractMMDDYY(inputDate)) {
      return true;
    }
    boolean passed = false;
    passed = extractYear(inputDate) || passed;
    passed = extractMonth(inputDate) || passed;
    passed = extractDay(inputDate) || passed;

    //slightly hacky, but check for some common modifiers that get grouped into the date
    passed = addExtraRanges(inputDate) || passed;

    if (!passed) {//couldn't parse
      //try one more trick
      unparseable = true;
      boolean weekday = extractWeekday(inputDate);
      if (!weekday) {
        isoDate = inputDate;
      }
    }
    return passed;
  }

  private static String[] rangeIndicators = {"--", "-"};

  /**
   * Attempts to find the two sides of a range in the given string.
   * Uses rangeIndicators to find possible matches.
   *
   */
  private static Pair<String, String> getRangeDates(String inputDate) {
    for (String curIndicator : rangeIndicators) {
      String[] dates = inputDate.split(curIndicator);
      if (dates.length == 2) {
        return new Pair<>(dates[0], dates[1]);
      }
    }
    return null;
  }

  private boolean addExtraRanges(String inputDate) {
    if (isRange()) {
      return false;
    }
    inputDate = inputDate.toLowerCase();
    if (inputDate.contains("half")) {
      if (inputDate.contains("first") && isoDate.length() <= 6) {
        String firstDate = isoDate + "01";
        String secondDate;
        if (isoDate.length() == 4) {//year
          secondDate = isoDate + MONTH_OF_HALF_YEAR;
        } else {//month
          secondDate = isoDate + DAY_OF_HALF_MONTH;
        }
        isoDate = firstDate + '/' + secondDate;
        return true;
      } else if (inputDate.contains("second") && isoDate.length() <= 6) {
        String firstDate;
        String secondDate;
        if (isoDate.length() == 4) {//year
          firstDate = isoDate + MONTH_OF_HALF_YEAR;
          secondDate = isoDate + LAST_MONTH_OF_YEAR;
          isoDate = firstDate + '/' + secondDate;
        } else {//month
          firstDate = isoDate + DAY_OF_HALF_MONTH;
          secondDate = isoDate + LAST_DAY_OF_MONTH;
        }
        isoDate = firstDate + '/' + secondDate;
        return true;
      }
    }

    return false;
  }

  /**
   * Returns true iff this date represents a range
   * The range must have at least a start or end
   * date, but is not guaranteed to have both
   *
   * @return Whether this date represents a range
   */
  public boolean isRange() {
    if (unparseable) {
      return false;
    }
    return isoDate.matches("/");
  }

  /**
   * Returns true iff we were unable to parse the input
   * String associated with this date; in that case,
   * we just store the input string and shortcircuit
   * all of the comparison methods
   *
   */
  public boolean isUnparseable() {
    return unparseable;
  }


  /**
   * Returns this date or if it is a range,
   * the date the range starts.  If the date
   * is of the form /&lt;date&gt;, "" is returned
   *
   * @return Start date of range
   */
  public String getStartDate() {
    if (!isRange()) {
      return isoDate;
    }
    if (isoDate.startsWith("/")) {
      return "";
    }
    return isoDate.split("/")[0];
  }

  /**
   * Returns this date or if it is a range,
   * the date the range ends.  If the date
   * is of the form &lt;date&gt;/, "" is returned
   *
   * @return End date of range
   */
  public String getEndDate() {
    if (!isRange()) {
      return isoDate;
    }
    if (isoDate.endsWith("/")) {
      return "";
    }
    String[] split = isoDate.split("/");
    return split[split.length - 1];
  }

  /* -------------------------- Static Comparison Methods -------------------------- */
  /**
   * Returns true if date1 is after date2.
   *
   * Several tricky cases exist, and implementation tries to
   * go with the common sense interpretation:
   * When a year and a month are given for one, but only a month
   * for the other, it is assumed that both have the same year
   * e.g:
   * ****12 is after 200211
   *
   * When a year and a month are given for one but only a year
   * for the other, it is assumed that one of these is after the
   * other only if the years differ, e.g.:
   * 2003 is after 200211
   * 2002 is not after 200211
   * 200211 is not after 2002
   *
   * @return Whether date2 is after date1
   */
  static boolean isAfter(String date1, String date2) {
    if (!isDateFormat(date1) || !isDateFormat(date2)) {
      return false;
    }
    boolean after = true;
    //first check years
    String year = date1.substring(0, 4);
    String yearOther = date2.substring(0, 4);
    if (year.contains("*") || yearOther.contains("*")) {
      after = after && checkWildcardCompatibility(year, yearOther);
    } else if (Integer.parseInt(year) > Integer.parseInt(yearOther)) {
      return true;
    } else if (Integer.parseInt(year) < Integer.parseInt(yearOther)) {
      return false;
    }

    if (date1.length() < 6 || date2.length() < 6) {
      if (year.contains("*") || yearOther.contains("*")) {
        return after;
      } else {
        return after && (Integer.parseInt(year) != Integer.parseInt(yearOther));
      }
    }
    //then check months
    String month = date1.substring(4, 6);
    String monthOther = date2.substring(4, 6);
    if (month.contains("*") || monthOther.contains("*")) {
      after = after && checkWildcardCompatibility(month, monthOther);
    } else if (Integer.parseInt(month) > Integer.parseInt(monthOther)) {
      return true;
    } else if (Integer.parseInt(month) < Integer.parseInt(monthOther)) {
      return false;
    }

    if (date1.length() < 8 || date2.length() < 8) {
      if (month.contains("*") || monthOther.contains("*")) {
        return after;
      } else {
        return after && (Integer.parseInt(month) != Integer.parseInt(monthOther));
      }
    }

    //then check days
    String day = date1.substring(6, 8);
    String dayOther = date2.substring(6, 8);
    if (day.contains("*") || dayOther.contains("*")) {
      after = after && checkWildcardCompatibility(day, dayOther);
    } else if (Integer.parseInt(day) > Integer.parseInt(dayOther)) {
      return true;
    } else if (Integer.parseInt(day) <= Integer.parseInt(dayOther)) {
      return false;
    }

    return after;
  }

  /**
   * Right now, we say they're compatible iff one of them is all
   * wildcards or they are equivalent
   *
   */
  @SuppressWarnings("unused")
  private static boolean checkWildcardAfterCompatibility(String txt1, String txt2) {
    if (txt1.length() != txt2.length()) {
      return false;
    }

    for (int i = 0; i < txt1.length(); i++) {
      Character t1 = txt1.charAt(i);
      Character t2 = txt2.charAt(i);
      if (!(t1.equals('*') || t2.equals('*') || t1.equals(t2))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if the given txt contains only digits and "*" characters;
   * false otherwise
   *
   */
  private static boolean isDateFormat(String txt) {
    String numberValue = txt.replace("*", "");//remove wildcards
    try {
      Integer.parseInt(numberValue);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Returns true iff date1 could represent the same value as date2
   * e.g.
   * ****07 is compatible with 200207 (and 200207 is compatible with ****07)
   * 200207 is compatible with 20020714 (?maybe need a better idea of use case here...)
   *
   */
  public static boolean isCompatible(String date1, String date2) {
    boolean compatible = true;
    //first check years
    compatible = compatible && isYearCompatible(date1, date2);

    //then check months
    compatible = compatible && isMonthCompatible(date1, date2);

    //then check days
    compatible = compatible && isDayCompatible(date1, date2);

    return compatible;

  }

  /**
   * Checks if the years represented by the two dates are compatible
   * If either lacks a year, we return true.
   *
   */
  private static boolean isYearCompatible(String date1, String date2) {
    boolean compatible = true;
    if (date1.length() < 4 || date2.length() < 4) {
      return compatible;
    }
    //first check years
    String year = date1.substring(0, 4);
    String yearOther = date2.substring(0, 4);
    if (year.contains("*") || yearOther.contains("*")) {
      compatible = compatible && checkWildcardCompatibility(year, yearOther);
    } else if (!year.equals(yearOther)) {
      return false;
    }
    return compatible;
  }

  /**
   * Checks if the months represented by the two dates are compatible
   * If either lacks a month, we return true.
   *
   */
  private static boolean isMonthCompatible(String date1, String date2) {
    boolean compatible = true;
    if (date1.length() < 6 || date2.length() < 6) {
      return compatible;
    }
    //then check months
    String month = date1.substring(4, 6);
    String monthOther = date2.substring(4, 6);
    if (month.contains("*") || monthOther.contains("*")) {
      compatible = (compatible && checkWildcardCompatibility(month, monthOther));
    } else if (!month.equals(monthOther)) {
      return false;
    }
    return compatible;
  }

  /**
   * Checks if the days represented by the two dates are compatible
   * If either lacks a day, we return true.
   *
   */
  private static boolean isDayCompatible(String date1, String date2) {
    boolean compatible = true;
    if (date1.length() < 8 || date2.length() < 8) {
      return compatible;
    }
    //then check days
    String day = date1.substring(6, 8);
    String dayOther = date2.substring(6, 8);
    if (day.contains("*") || dayOther.contains("*")) {
      compatible = compatible && checkWildcardCompatibility(day, dayOther);
    } else if (!day.equals(dayOther)) {
      return false;
    }
    return compatible;
  }


  /**
   */
  private static boolean checkWildcardCompatibility(String txt1, String txt2) {
    if (txt1.length() != txt2.length()) {
      return false;
    }
    for (int i = 0; i < txt1.length(); i++) {
      Character t1 = txt1.charAt(i);
      Character t2 = txt2.charAt(i);
      if (!(t1.equals('*') || t2.equals('*') || t1.equals(t2))) {
        return false;
      }
    }
    return true;
  }


  /* -------------------------- Instance Comparison Methods -------------------------- */
  /**
   * Returns true iff this date
   * contains the date represented by other.
   * A range contains a date if it
   * is equal to or after the start date and equal to or
   * before the end date.  For open ranges, contains
   * is also inclusive of the one end point.
   *
   */
  public boolean contains(ISODateInstance other) {
    if (this.isUnparseable() || other.isUnparseable()) {
      return this.isoDate.equals(other.isoDate);
    }
    String start = this.getStartDate();
    if (!start.equals("")) {//we have a start date, need to make sure other is after it
      String startOther = other.getStartDate();
      if (startOther.equals("")) {
        return false;//incompatible
      } else {
        if (!isAfter(startOther, start)) {
          return false;
        }
      }
    }
    //now we've found out that the start date is appropriate, check the end date
    String end = this.getEndDate();
    if (! end.isEmpty()) {
      String endOther = other.getEndDate();
      if (endOther.isEmpty()) {
        return false;
      } else {
        if (!isAfter(end, endOther)) {
          return false;
        }
      }
    }
    return true;//passes both start and end
  }


  /**
   * Returns true if this date instance is after
   * the given dateString.  If this date instance
   * is a range, then returns true only if both
   * start and end dates are after dateString.
   *
   * Several tricky cases exist, and implementation tries to
   * go with the commonsense interpretation:
   * When a year and a month are given for one, but only a month
   * for the other, it is assumed that both have the same year
   * e.g:
   * ****12 is after 200211
   *
   * When a year and a month are given for one but only a year
   * for the other, it is assumed that one of these is after the
   * other only if the years differ, e.g.:
   * 2003 is after 200211
   * 2002 is not after 200211
   * 200211 is not after 2002
   *
   */
  public boolean isAfter(String dateString) {
    if (this.isUnparseable()) {
      return false;
    }
    if (!isDateFormat(dateString)) {
      return false;
    }
    return isAfter(this.getEndDate(), dateString);
  }

  public boolean isCompatibleDate(ISODateInstance other) {
    if (this.isUnparseable() || other.isUnparseable()) {
      return this.isoDate.equals(other.isoDate);
    }

    //first see if either is a range
    if (this.isRange()) {
      return this.contains(other);
    } else if (other.isRange()) {
      return false;//not compatible if other is range and this isn't
    } else {
      return isCompatible(isoDate, other.getDateString());
    }
  }

  /**
   * Looks if the years for the two dates are compatible.
   * This method does not consider ranges and uses only the
   * start date.
   *
   */
  public boolean isYearCompatible(ISODateInstance other) {
    if (this.isUnparseable() || other.isUnparseable()) {
      return this.isoDate.equals(other.isoDate);
    }

    return isYearCompatible(isoDate, other.getDateString());
  }

  /**
   * Looks if the months for the two dates are compatible.
   * This method does not consider ranges and uses only the
   * start date.
   *
   */
  public boolean isMonthCompatible(ISODateInstance other) {
    if (this.isUnparseable() || other.isUnparseable()) {
      return this.isoDate.equals(other.isoDate);
    }

    return isMonthCompatible(isoDate, other.getDateString());
  }

  /**
   * Looks if the days for the two dates are compatible.
   * This method does not consider ranges and uses only the
   * start date.
   *
   */
  public boolean isDayCompatible(ISODateInstance other) {
    if (this.isUnparseable() || other.isUnparseable()) {
      return this.isoDate.equals(other.isoDate);
    }

    return isDayCompatible(isoDate, other.getDateString());
  }


  /* -------------------------- Tokenization and Field Extraction -------------------------- */
  //These methods are taken directly from or modified slightly from {@link DateInstance}

  private void tokenizeDate(String inputDate) {
    tokens = new ArrayList<>();
    Pattern pat = Pattern.compile("[-]");
    if (inputDate == null) {
      System.out.println("Null input date");
    }
    Matcher m = pat.matcher(inputDate);
    String str = m.replaceAll(" - ");
    str = str.replaceAll(",", " ");
    PTBTokenizer<Word> tokenizer = PTBTokenizer.newPTBTokenizer(new BufferedReader(new StringReader(str)));
    while (tokenizer.hasNext()) {
      Word nextToken = tokenizer.next();
      tokens.add(nextToken.toString());
    }
    if(DEBUG) {
      System.out.println("tokens:" + tokens);
    }
  }


  /**
   * This method does YYYY-MM-DD style ISO date formats
   *
   * @return whether it worked.
   */
  private boolean extractYYYYMMDD(String inputDate) {
    Pattern pat = Pattern.compile("([12][0-9]{3})[ /-]?([01]?[0-9])[ /-]([0-3]?[0-9])[ \t\r\n\f]*");
    Matcher m = pat.matcher(inputDate);
    if (m.matches()) {
      if (DEBUG) {
        log.info("YYYYMMDD succeeded");
      }
      String monthValue = m.group(2);
      if (monthValue.length() < 2)//we always use two digit months
      {
        monthValue = '0' + monthValue;
      }
      String dayValue = m.group(3);
      if (dayValue.length() < 2) {
        dayValue = '0' + dayValue;
      }
      String yearString = m.group(1);
      isoDate = yearString + monthValue + dayValue;
      return true;
    }
    return false;
  }

  /**
   * Note: This method copied from {@code DateInstance}; not sure how we tell that it
   * is MMDD versus DDMM (sometimes it will be ambiguous).
   *
   */
  private boolean extractMMDDYY(String inputDate) {
    Pattern pat = Pattern.compile("([0-1]??[0-9])[ \t\n\r\f]*[/-][ \t\n\r\f]*([0-3]??[0-9])[ \t\r\n\f]*[/-][ \t\r\n\f]*([0-2]??[0-9]??[0-9][0-9])[ \t\r\n\f]*");
    Matcher m = pat.matcher(inputDate);
    if (m.matches()) {
      if (DEBUG) {
        log.info("MMDDYY succeeded");
      }
      String monthValue = m.group(1);
      if (monthValue.length() < 2)//we always use two digit months
      {
        monthValue = '0' + monthValue;
      }
      String dayValue = m.group(2);
      if (dayValue.length() < 2) {
        dayValue = '0' + dayValue;
      }
      String yearString; // always initialized below
      if (m.group(3).length() == 2) {
        int yearInt = Integer.parseInt(m.group(3));
        //Now we add "20" or "19" to the front of the two digit year depending on its value....
        if (yearInt < 50) {
          yearString = "20" + m.group(3);
        } else {
          yearString = "19" + m.group(3);
        }

      } else {
        yearString = m.group(3);
      }
      //lastYearSet = new Integer(yearString).intValue();
      isoDate = yearString + monthValue + dayValue;
      return true;
    }
    return false;
  }

  private Pattern re1 = Pattern.compile("[1-2][0-9]{3}|'[0-9]{2}");
  private Pattern re2 = Pattern.compile("[0-9][^0-9].*([0-9]{2})\\s*$");

  public boolean extractYear(String inputDate) {
    if (DEBUG) {
      log.info("Extracting year from: |" + inputDate + '|');
    }
    String extract;
    Matcher m1 = re1.matcher(inputDate);
    Matcher m2 = re2.matcher(inputDate);
    if (m1.find()) {
      extract = m1.group(0);
    } else if (m2.find()) {
      extract = m2.group(1);
    } else {
      extract = foundMiscYearPattern(inputDate);
      if (StringUtils.isNullOrEmpty(extract)) {
        isoDate = "****";
        return false;
      }
    }

    if ( ! "".equals(extract)) {
      if (extract.charAt(0) == '\'') {
        extract = extract.substring(1);
      }
      extract = extract.trim();
      if (extract.length() == 2) {
        if (extract.charAt(0) < '5') {
          extract = "20" + extract;
        } else {
          extract = "19" + extract;
        }
      }
      if (inputDate.charAt(inputDate.length() - 1) == 's') {//decade or century marker
        if (extract.charAt(2) == '0') {//e.g., 1900s -> 1900/1999
          String endDate = Integer.toString((Integer.parseInt(extract) + 99));
          extract = extract + '/' + endDate;
        } else {//e.g., 1920s -> 1920/1929
          String endDate = Integer.toString((Integer.parseInt(extract) + 9));
          extract = extract + '/' + endDate;
        }
      }
      isoDate = extract;
      if (DEBUG) {
        log.info("year extracted:" + extract);
      }
      return true;
    }
    isoDate = "****";
    return false;
  }

  /**
   * Tries to find a year pattern in the input string that may be somewhat
   * odd/non-standard.
   *
   */
  private static String foundMiscYearPattern(String inputDate) {
    String year = "";
    if (inputDate.toLowerCase().contains("century")) {
      if (inputDate.endsWith("A.D. ")) {
        inputDate = inputDate.substring(0, inputDate.length()-5);
        if(DEBUG) {
          System.out.println("inputDate: |" + inputDate + "|");
        }
      }
      if (inputDate.startsWith("late")) {
        inputDate = inputDate.substring(5, inputDate.length());
        if(DEBUG) {
          System.out.println("inputDate: |" + inputDate + "|");
        }
      }
      if (inputDate.startsWith("early")) {
        inputDate = inputDate.substring(6, inputDate.length());
        if(DEBUG) {
          System.out.println("inputDate: |" + inputDate + "|");
        }
      }
      if (Character.isDigit(inputDate.charAt(0))) {
        // just parse number part, assuming last two letters are st/nd/rd
        year = QuantifiableEntityNormalizer.normalizedNumberStringQuiet(inputDate.substring(0, inputDate.length() - 2), 1, "", null);
        if (year == null) {
          year = "";
        }
        if (year.contains(".")) {//number format issue
          year = year.substring(0, year.indexOf('.'));
        }
        while (year.length() < 4) {
          year = year + '*';
        }
      } else if (QuantifiableEntityNormalizer.ordinalsToValues.containsKey(inputDate)) {
        year = Double.toString(QuantifiableEntityNormalizer.ordinalsToValues.getCount(inputDate));
        while (year.length() < 4) {
          year = year + '*';
        }
      } else {
        if (DEBUG) {
          System.out.println("ISODateInstance: Couldn't parse probable century: " + inputDate);
        }
        year = "";
      }
    }
    return year;
  }

  private static final Pattern[] extractorArray = {Pattern.compile("[Jj]anuary|JANUARY|[Jj]an\\.?|JAN\\.?"), Pattern.compile("[Ff]ebruary|FEBRUARY|[Ff]eb\\.?|FEB\\.?"), Pattern.compile("[Mm]arch|MARCH|[Mm]ar\\.?|MAR\\.?"), Pattern.compile("[Aa]pril|APRIL|[Aa]pr\\.?|APR\\.?"), Pattern.compile("[Mm]ay|MAY"), Pattern.compile("[Jj]une|JUNE|[Jj]un\\.?|JUN\\.?"), Pattern.compile("[Jj]uly|JULY|[Jj]ul\\.?|JUL\\.?"), Pattern.compile("[Aa]ugust|AUGUST|[Aa]ug\\.?|AUG\\.?"), Pattern.compile("[Ss]eptember|SEPTEMBER|[Ss]ept?\\.?|SEPT?\\.?"), Pattern.compile("[Oo]ctober|OCTOBER|[Oo]ct\\.?|OCT\\.?"), Pattern.compile("[Nn]ovember|NOVEMBER|[Nn]ov\\.?|NOV\\.?"), Pattern.compile("[Dd]ecember|DECEMBER|[Dd]ec(?:\\.|[^aeiou]|$)|DEC(?:\\.|[^aeiou]|$)")}; // avoid matching "decades"!

  public boolean extractMonth(String inputDate) {
    boolean foundMonth = false;

    for (int i = 0; i < 12; i++) {
      String extract = "";
      Matcher m = extractorArray[i].matcher(inputDate);
      if (m.find()) {
        extract = m.group(0);
      }
      if ( ! "".equals(extract)) {
        if (!foundMonth) {
          if (DEBUG) {
            log.info("month extracted: " + extract);
          }
          int monthNum = i + 1;
          if (isoDate.length() != 4) {
            isoDate = "****";
          }
          String month = (monthNum < 10) ? "0" + monthNum : String.valueOf(monthNum);
          isoDate += month;
          foundMonth = true;
        }
      }
    }
    return foundMonth;
  }

  public boolean extractDay(String inputDate) {
    try {
      for (String extract : tokens) {
        if (QuantifiableEntityNormalizer.wordsToValues.containsKey(extract)) {
          extract = Integer.toString(Double.valueOf(QuantifiableEntityNormalizer.wordsToValues.getCount(extract)).intValue());
        } else if (QuantifiableEntityNormalizer.ordinalsToValues.containsKey(extract)) {
          extract = Integer.toString(Double.valueOf(QuantifiableEntityNormalizer.ordinalsToValues.getCount(extract)).intValue());
        }
        extract = extract.replaceAll("[^0-9]", "");
        if ( ! extract.isEmpty()) {
          Long i = Long.parseLong(extract);
          if (i.intValue() < 32L && i.intValue() > 0L) {
            if (isoDate.length() < 6) { //should already have year and month
              if (isoDate.length() != 4) { //throw new RuntimeException("Error extracting dates; should have had month and year but didn't");
                isoDate = isoDate + "******";
              } else {
                isoDate = isoDate + "**";
              }
            }
            String day = (i < 10) ? "0" + i : String.valueOf(i);
            isoDate = isoDate + day;
            return true;
          }
        }
      }
    } catch (NumberFormatException e) {
      log.info("Exception in extract Day.");
      log.info("tokens size :" + tokens.size());
      e.printStackTrace();
    }
    return false;
  }

  private static final Pattern[] weekdayArray = {Pattern.compile("[Ss]unday"), Pattern.compile("[Mm]onday"), Pattern.compile("[Tt]uesday"), Pattern.compile("[Ww]ednesday"), Pattern.compile("[Tt]hursday"), Pattern.compile("[Ff]riday"), Pattern.compile("[Ss]aturday")};

  /**
   * This is a backup method if everything else fails.  It searches for named
   * days of the week and if it finds one, it sets that as the date in lowercase form
   *
   */
  public boolean extractWeekday(String inputDate) {
    for (Pattern p : weekdayArray) {
      Matcher m = p.matcher(inputDate);
      if (m.find()) {
        String extract = m.group(0);
        isoDate = extract.toLowerCase();
        return true;
      }
    }
    return false;
  }

  /**
   * For testing only
   *
   */
  public static void main(String[] args) {
    Properties props = StringUtils.argsToProperties(args);
    String dateProperty = props.getProperty("date");
    if (dateProperty != null) {
      ISODateInstance d = new ISODateInstance(dateProperty);
      System.out.println(dateProperty + " processed as " + d);
    }
  }


}
