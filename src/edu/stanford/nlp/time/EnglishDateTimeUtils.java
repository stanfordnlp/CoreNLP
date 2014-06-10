package edu.stanford.nlp.time;

import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.util.ArrayMap;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Some utility functions for date time (for English)
 *
 * @author Angel Chang
 */
public class EnglishDateTimeUtils {

  static final Pattern teFixedHolPattern = Pattern.compile("\\b(new\\s+year|inauguration|valentine|ground|candlemas|patrick|fool|(saint|st\\.)\\s+george|walpurgisnacht|may\\s+day|beltane|cinco|flag|baptiste|canada|dominion|independence|bastille|halloween|allhallow|all\\s+(saint|soul)s|day\\s+of\\s+the\\s+dead|fawkes|veteran|christmas|xmas|boxing)\\b", Pattern.CASE_INSENSITIVE);
  static final Pattern teNthDOWHolPattern = Pattern.compile("\\b(mlk|king|president|canberra|mother|father|labor|columbus|thanksgiving)\\b", Pattern.CASE_INSENSITIVE);
  static final Pattern teLunarHolPattern = Pattern.compile("\\b(easter|palm\\s+sunday|good\\s+friday|ash\\s+wednesday|shrove\\s+tuesday|mardis\\s+gras)\\b", Pattern.CASE_INSENSITIVE);
  static final Pattern teDayHolPattern   = Pattern.compile("\\b(election|memorial|C?Hanukk?ah|Rosh|Kippur|tet|diwali|halloween)\\b", Pattern.CASE_INSENSITIVE);

  // holidays that appear on fixed date
  static Map<String,String> fixedHol2Date = new HashMap<String,String>(30);
  static {
    fixedHol2Date.put("newyear",   "0101");
    fixedHol2Date.put("inauguration", "0120");
		fixedHol2Date.put("valentine", "0214");
    fixedHol2Date.put("ground",    "0202");
    fixedHol2Date.put("candlemas",    "0202");
    fixedHol2Date.put("patrick",   "0317");
    fixedHol2Date.put("fool", "0401");
    fixedHol2Date.put("st.george","0423");
    fixedHol2Date.put("saintgeorge",  "0423");
		fixedHol2Date.put("walpurgisnacht", "0430");
    fixedHol2Date.put("mayday",    "0501");
    fixedHol2Date.put("beltane",      "0501");
    fixedHol2Date.put("cinco",     "0505");
    fixedHol2Date.put("flag",         "0614");
    fixedHol2Date.put("baptiste",  "0624");
    fixedHol2Date.put("dominion",     "0701");
		fixedHol2Date.put("canada",    "0701");
		fixedHol2Date.put("independence", "0704");
    fixedHol2Date.put("bastille",     "0714");
    fixedHol2Date.put("halloween", "1031");
    fixedHol2Date.put("allhallow",    "1101");
    fixedHol2Date.put("allsaints",  "1101");
    fixedHol2Date.put("allsouls",     "1102");
    fixedHol2Date.put("dayofthedead", "1102");
    fixedHol2Date.put("fawkes",    "1105");
    fixedHol2Date.put("veteran",      "1111");
		fixedHol2Date.put("christmas", "1225");
    fixedHol2Date.put("xmas",         "1225"   );
  }

  // holidays that appear on certain day of the week
  // format is month-DOW-nth
  static Map<String,String> nthDOWHol2Date = new HashMap<String,String>(9);
  static {
    nthDOWHol2Date.put("mlk",        "1-1-3");
    nthDOWHol2Date.put("king",         "1-1-3");
		nthDOWHol2Date.put("president",  "2-1-3");
    nthDOWHol2Date.put("canberra",     "3-1-3");
		nthDOWHol2Date.put("mother",       "5-7-2");
		nthDOWHol2Date.put("father",     "6-7-3");
    nthDOWHol2Date.put("labor",        "9-1-1");
		nthDOWHol2Date.put("columbus",   "10-1-2");
    nthDOWHol2Date.put("thanksgiving", "11-4-4");
  }

  // "jan" => 1, "feb" =>  2, "mar" =>  3, "apr" =>  4,
	//	 "may" => 5, "jun" =>  6, "jul" =>  7, "aug" =>  8,
	//	 "sep" => 9, "oct" => 10, "nov" => 11, "dec" => 12
  static String[] months = { "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec" };
  public static Map<String,Integer> month2Num = new HashMap<String,Integer>(months.length);
  static {
    for (int i = 0; i < months.length; i++) {
      month2Num.put(months[i], i+1);
    }
  }

  // "sunday"    => 0, "monday"   => 1, "tuesday" => 2,  "wednesday" => 3,
  // "thursday" => 4, "friday"  => 5, "saturday"  => 6
  static String[] dayOfWeek = { "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" };
  static Map<String,Integer> day2Num = new HashMap<String,Integer>(dayOfWeek.length);
  static {
    for (int i = 0; i < dayOfWeek.length; i++) {
      day2Num.put(dayOfWeek[i], i);
    }
  }

  // TODO: Java also knows about time zones...
  // %TE_TimeZones    = ("E" => -5, "C" => -6, "M" => -7, "P" => -8);
  static Map<String,Integer> teTimeZones = new ArrayMap<String,Integer>(4);
  static {
    teTimeZones.put("E", -5);
    teTimeZones.put("C", -6);
    teTimeZones.put("M", -7);
    teTimeZones.put("P", -8);
  }

  // %TE_Season       = ("spring" => "SP", "summer" => "SU",
  //     "autumn" => "FA", "fall" => "FA", "winter" => "WI");
  static Map<String,String> teSeason = new ArrayMap<String,String>(5);
  static {
    teSeason.put("spring", "SP");
    teSeason.put("summer", "SU");
    teSeason.put("autumn", "FA");
    teSeason.put("fall", "FA");
    teSeason.put("winter", "WI");
  }

  // %TE_Season2Month = ("SP" => 4, "SU" => 6, "FA" => 9, "WI" => 12);
  static Map<String,Integer> teSeason2Month = new ArrayMap<String,Integer>(4);
  static {
    teSeason2Month.put("SP", 4);
    teSeason2Month.put("SU", 6);
    teSeason2Month.put("FA", 9);
    teSeason2Month.put("WI", 12);
  }


  // Length of month in days (without leap years)
  static int[] teMl    = new int[]{0, 31, 28, 31,  30,  31,  30,  31,  31,  30,  31,  30, 31};
  // Cumulative number of days from beginning for each month (without leap years)
  static int[] teCumMl = new int[]{0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365};

  // time expression ordinals - like ordWord2Num but only goes to thirty
  static Map<String,Integer> teOrd2Num = new HashMap<String,Integer>(31);
  static {
    teOrd2Num.put("first", 1);
    teOrd2Num.put("second", 2);
    teOrd2Num.put("third", 3);
    teOrd2Num.put("fourth", 4);
    teOrd2Num.put("fifth", 5);
    teOrd2Num.put("sixth", 6);
    teOrd2Num.put("seventh", 7);
    teOrd2Num.put("eighth", 8);
    teOrd2Num.put("ninth", 9);
    teOrd2Num.put("tenth", 10);
    teOrd2Num.put("eleventh", 11);
    teOrd2Num.put("twelfth", 12);
    teOrd2Num.put("thirteenth", 13);
    teOrd2Num.put("fourteenth", 14);
    teOrd2Num.put("fifteenth", 15);
    teOrd2Num.put("sixteenth", 16);
    teOrd2Num.put("seventeenth", 17);
    teOrd2Num.put("eighteenth", 18);
	  teOrd2Num.put("nineteenth", 19);
    teOrd2Num.put("twentieth", 20);
    teOrd2Num.put("twenty-first", 21);
	  teOrd2Num.put("twenty-second", 22);
    teOrd2Num.put("twenty-third", 23);
    teOrd2Num.put("twenty-fourth", 24);
	  teOrd2Num.put("twenty-fifth", 25);
    teOrd2Num.put("twenty-sixth", 26);
    teOrd2Num.put("twenty-seventh", 27);
	  teOrd2Num.put("twenty-eighth", 28);
    teOrd2Num.put("twenty-ninth", 29);
    teOrd2Num.put("thirtieth", 30);
	  teOrd2Num.put("thirty-first", 31);
  }

  static Map<String,Integer> teDecadeNums = new HashMap<String,Integer>(9);
  static {
    teDecadeNums.put("twenties", 2);
    teDecadeNums.put("thirties", 3);
    teDecadeNums.put("forties", 4);
    teDecadeNums.put("fifties", 5);
    teDecadeNums.put("sixties", 6);
    teDecadeNums.put("seventies", 7);
    teDecadeNums.put("eighties", 8);
    teDecadeNums.put("nineties", 9);
  }


  private static int getMonthLength(int year, int month) {
    int ml;
    if((month == 2) && (isLeapYear(year))) { ml = 29; }
    else { ml = teMl[month]; }
    return(ml);
  } // End of subroutine getMonthLength

  private static boolean isLeapYear(int year) {
    // This is the Gregorian Calendar
    if(((year % 400) == 0) ||
       (((year % 4) == 0) && ((year % 100) != 0))) {
	    return true;
    } else { return false; }
  } // End of subroutine isLeapYear

  // TODO: handle \A (match beginning of string, just once even when /m)
  private static final Pattern isoDateFormat = Pattern.compile("(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)");
  @SuppressWarnings("unused")
  private static int dayOfYear(String iso)
  {
    Matcher matcher = isoDateFormat.matcher(iso);
    if (matcher.matches()) {
      int year = Integer.parseInt(matcher.group(1));
      int month = Integer.parseInt(matcher.group(2));
      int day = Integer.parseInt(matcher.group(3));
      return dayOfYear(year, month, day);
    } else {
      return 0;
    }
  } // End of subroutine dayOfYear

  // TODO: Double check semantics of this implementation
  // should it be teCumMl[month] and month >= 2?
  private static int dayOfYear(int year, int month, int day) {
	  int doy = teCumMl[month - 1] + day;
	  if(isLeapYear(year) && (month > 2)) { doy++; }
    return doy;
  } // End of subroutine dayOfYear


  private static SUTime.IsoDate parseIsoDate(String iso)
  {
    Matcher matcher = isoDateFormat.matcher(iso);
    if (matcher.matches()) {
      int year = Integer.parseInt(matcher.group(1));
      int month = Integer.parseInt(matcher.group(2));
      int day = Integer.parseInt(matcher.group(3));
      return new SUTime.IsoDate(year, month, day);
    } else {
      return null;
    }
  }
  private static Pattern isoYearWeekFormat = Pattern.compile("(\\d\\d\\d\\d)W(\\d\\d)");
  @SuppressWarnings("unused")
  private static String week2DateIso(String iso)
  {
    Matcher matcher = isoYearWeekFormat.matcher(iso);
    if (matcher.matches()) {
      int year = Integer.parseInt(matcher.group(1));
      int week = Integer.parseInt(matcher.group(2));
      return week2DateIso(year, week);
    } else {
      return "00000001";
    }
  } // End of subroutine week2DateIso

  // Returns the date of the Thursday in the specified week.
  private static String week2DateIso(int year, int week)
  {
	  int doy = week*7 - 3;

	  int month = 1;
	  int ml = getMonthLength(year, month);
	  while(doy > ml) {
	    month++;
	    if(month > 12) { return "00000002";}
	    doy -= ml;
	    ml = getMonthLength(year, month);
	  }
	  String isoOut = String.format("%04d%02d%02d", year, month, doy);
    return isoOut;
  } // End of subroutine week2DateIso

  protected static String date2Week(String iso) {
    SUTime.IsoDate isoDate = parseIsoDate(iso);
    if (isoDate != null) {
      return date2Week(isoDate.year, isoDate.month, isoDate.day);
    } else {
      return null;
    }
  } // End of subroutine date2Week

  private static String date2Week(int year, int month, int day) {
	  int dl   = date2DOW(year, 1, 1);
	  int doy  = dayOfYear(year, month, day);

	  if((dl > 4) && (doy < (7-doy))) {
	    year--;
	    dl   = date2DOW(year, 1, 1);
	    doy += 365 + (isLeapYear(year)?1:0);
	  }

	  int w = (doy + dl +5)/7;
	  if(dl > 4) { w--; }
	  String weekStr = String.format("%4.dW%02.d", year, w);
	  return weekStr;
  } // End of subroutine date2Week


  // Converts date to Day of Week
  // Sunday = 0, Monday = 1, etc
  private static int date2DOW(String iso)  {
    SUTime.IsoDate isoDate = parseIsoDate(iso);
    if (isoDate != null) {
      return date2DOW(isoDate.year, isoDate.month, isoDate.day);
    } else {
      // TODO: Is this the best thing to return????
      return 7;
    }
  } // End of subroutine date2DOW

  // Converts date to Day of Week
  // Sunday = 0, Monday = 1, etc
  private static int date2DOW(int year, int month, int day)  {
    int a = (14 - month)/12;
    int y = year - a;
    int m = month + (12 * a) - 2;
    int d = (day + y + (y/4) - (y/100) + (y/400) + (31*m/12)) % 7;
    return(d);
  } // End of subroutine date2DOW


  //#######################################
  // Figures the nth DOW in month
  // as a date for a given year
  protected static int nthDOW2Date(int month, int dow, int nth, int year)
  {
    if (dow == 7) { dow = 0; }

    String str = String.format("%04d%02d01", year, month);
    int firstDow = date2DOW(str);
    int shift = dow - firstDow;

    // print "C: $first  $shift\n";

    if(shift < 0) { shift += 7; }
    return(shift+(7*nth)-6);
}

  // Figures out the date of easter for a given year
  @SuppressWarnings("unused")
  private static String getEasterDate(int year) {

    int g = year % 19;
    int c = year / 100;
    int h = (c - (c/4) - ((8*c+13)/25) + 19*g +15) % 30;
    int i = h - (h/28)*(1 - (h/28)*(29/(h+1))*((21-g)/11));
    int j = (year + (year/4) + i + 2 - c + (c/4)) % 7;
    int l = i - j;

    int m = 3 + ((l+40)/44);
    int d = l + 28 - 31*(m/4);
    String date = String.format("%04d%02d%02d", year, m, d);
    return(date);
  }

  protected static String month2Iso(String month) {
    int m = month2Num(month);
    if (m < 0) { return null; }
    return String.format("%02d", m);
  }

  protected static int month2Num(String month)
  {
    if (month == null) return -1;
    if (Character.isLetter(month.charAt(0))) {
      month = month.substring(0,3).toLowerCase();
      return month2Num.get(month);
    } else {
      return Integer.parseInt(month);
    }
  }

  protected static String day2Iso(String month, String day) {
    int d = day2Num(month2Num(month), day);
    if (d < 0) { return null; }
    return String.format("%02d", d);
  }

  protected static int day2Num(int month, String day)
  {
    if (day == null) return -1;
    day = day.toLowerCase();
    if (Character.isLetter(day.charAt(0))) {
      if ("ides".equals(day)) {
			  return (month == 3 || month == 5 || month == 7 || month == 10)? 15:13;
      } else if ("nones".equals(day)) {
        return (month == 3 || month == 5 || month == 7 || month == 10)? 7:5;
      } else {
        Number d2 = NumberNormalizer.wordToNumber(day);
        if (d2 != null) {
          return d2.intValue();
        } else {
          return -1;
        }
      }
    } else {
      if (day.endsWith("th") || day.endsWith("rd") || day.endsWith("nd") || day.endsWith("st")) {
        day = day.substring(0, day.length()-2);
      }
      return Integer.parseInt(day);
    }
  }

  protected static int year2Num(String year)
  {
    if (year == null) return -1;
    return Integer.parseInt(year);
  }

  private static Pattern yearEraPattern = Pattern.compile("\\s*\\b(a\\.?d|b\\.?c)(?:\\.|\\b)\\s*", Pattern.CASE_INSENSITIVE);

  /**
   * Takes a basic string representation of year and returns a normalized ISO representation
   *
   * <br>ISO 8601 prescribes a four digit year "YYYY".
   * <br>For years before 0000 or after 9999, it should always be prefixed by a "+" or "-"
   * <br>Note that year 0000 represents the year 1BC
   *    (there is offset of one, year 3BC would be -0002)
   * @param year
   */
  protected static String year2Iso(String year)
  {
    if (year == null) return null;
    if (year.matches("[+-]?[0-9X]{4}")) {
      return year;
    }
    year = year.toLowerCase();
    if (year.startsWith("'")) {
      year = year.substring(1);
    }
    boolean negative = false;
    Matcher m = yearEraPattern.matcher(year);
    if (m.find()) {
      String era = m.group(1);
      year = m.replaceAll("");
      if (era.startsWith("b")) {
        // BC
        negative = true;
      }
    }
    if (year.endsWith("ties")) {
      // decade
      String[] fields = year.split("\\s+|-");
      if (fields.length == 2) {
        Number century = NumberNormalizer.wordToNumber(fields[0]);
        Integer decade = teDecadeNums.get(fields[1]);
        if (decade != null && century != null) {
          // TODO: Do we allow things like nineteen fifties B.C.?
          String res = String.format("%02d%01dX", century, decade);
          return (negative)? "-" + res: res;
        } else {
          throw new IllegalArgumentException("Invalid year: " + year);
        }
      } else if (fields.length == 1) {
        Integer decade = teDecadeNums.get(fields[0]);
        if (decade != null) {
          // TODO: Do we allow things like fifties B.C.?
          String res = String.format("XX%01dX", decade);
          return (negative)? "-" + res: res;
        } else {
          throw new IllegalArgumentException("Invalid year: " + year);
        }
      } else {
        throw new IllegalArgumentException("Invalid year: " + year);
      }
    } else if (year.endsWith("0s")) {
      // decade
      year = year.substring(0, year.length() - 1);
      String[] fields = year.split("\\s+|-");
      if (fields.length == 2) {
        Number century = NumberNormalizer.wordToNumber(fields[0]);
        Number decade = NumberNormalizer.wordToNumber(fields[1]);
        if (decade != null && century != null) {
          // TODO: Do we allow things like 1950s B.C.?
          String res = String.format("%02d%01dX", century, decade.intValue()/10);
          return (negative)? "-" + res: res;
        } else {
          throw new IllegalArgumentException("Invalid year: " + year);
        }
      } else if (fields.length == 1) {
        Number decade = NumberNormalizer.wordToNumber(fields[0]);
        if (decade != null) {
          if (decade.intValue() < 100) {
            // TODO: Do we allow things like 50s B.C.?
            String res = String.format("XX%01dX", decade.intValue()/10);
            return (negative)? "-" + res: res;
          } else {
            if (decade.intValue() % 100 == 0) {
              // centuries
              String res = String.format("%02dXX", decade.intValue()/100);
              return (negative)? "-" + res: res;
            } else {
              String res = String.format("%03dX", decade.intValue()/10);
              return (negative)? "-" + res: res;
            }
          }
        } else {
          throw new IllegalArgumentException("Invalid year: " + year);
        }
      } else {
        throw new IllegalArgumentException("Invalid year: " + year);
      }

    } else{
      if (year.contains("teen ")) {
        // Handle "eighteen fifty"
        String[] fields = year.split("\\s+",2);
        if (fields.length == 2) {
          Number a = NumberNormalizer.wordToNumber(fields[0]);
          Number b = NumberNormalizer.wordToNumber(fields[1]);
          if (b.intValue() > 0 && b.intValue() < 100) {
            if (negative) {
              int v = a.intValue()*100 + b.intValue();
              v = v-1;
              return String.format("-%04d", v);
            } else {
              return String.format("%02d%02d", a.intValue(), b.intValue());
            }
          }
        }
      }
      Number y = NumberNormalizer.wordToNumber(year);
      if (y != null) {
        int v = y.intValue();
        if (negative) {
          v = v-1;
          return String.format("-%04d", v);
        } else {
          return String.format("%04d", v);
        }
      } else {
        throw new IllegalArgumentException("Invalid year: " + year);        
      }
    }
  }
}
