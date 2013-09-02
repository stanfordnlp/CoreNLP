package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.ie.regexp.RegexpExtractor;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;

import java.io.BufferedReader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Holds information corresponding to a date, with functionality for
 * converting Date Strings to {@link Calendar} objects.
 *
 * @author Chris Cox
 */

public class DateInstance {

  private static final boolean DEBUG = false;

  public static int lastYearSet; // When years are missing, we set the year to the
  // last one matched by any DateInstance.

  private StringBuilder dateSB = new StringBuilder(); //the original string, containing some form of a date.
  private ArrayList<String> tokens = new ArrayList<String>();    //each token contains some piece of the date, from our input.

  private boolean isRange = false;         //whether this DateInstance is one date or a range  between two dates.

  private Calendar startDate = Calendar.getInstance();

  private Calendar endDate = Calendar.getInstance();

  private boolean fieldsExtracted = false;
  private static SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

  public DateInstance(){}

  public DateInstance(String date) {
    add(date);
  }

  public void add(String value) {
    dateSB.append(value).append(" ");
    tokens.add(value);
  }

  public void printString() {
    System.out.println(dateSB);
  }

  public boolean isRange() {
    return isRange;
  }

  /**
   * When the date is not a range, getStartDate() returns the
   * date.
   */
  public Calendar getStartDate() {
    return startDate;
  }

  public Calendar getEndDate() {
    return endDate;
  }

  public String getDateString() {
    if (!fieldsExtracted) {
      extractFields();
    }
    return dateFormat.format(startDate.getTime());
  }

  public String getEndDateString() {
    return dateFormat.format(endDate.getTime());
  }

  public int getYear() {
    return startDate.get(Calendar.YEAR);
  }

  public void setYear(int year) {
    startDate.set(Calendar.YEAR, year);
  }

  public void print() {
    System.err.print("Actual: " + dateSB.toString() + " ");
    System.err.println("Labelled: " + (startDate.get(Calendar.MONTH) + 1) + " " + startDate.get(Calendar.DAY_OF_MONTH) + " " + startDate.get(Calendar.YEAR) + "  Range: " + isRange);
    if (isRange) {
      System.err.print(" End date: " + (endDate.get(Calendar.MONTH) + 1) + " " + endDate.get(Calendar.DAY_OF_MONTH) + " " + endDate.get(Calendar.YEAR) + "\n");
    }
  }

  /**
   * Uses regexp matching to match  month, day, and year fields
   * from the Date String into a {@link Calendar}.
   */
  public boolean extractFields() {
    setDateToDefaults();
    fieldsExtracted = true;

    if (tokens.size() < 2) {
      tokenizeDate();
    }
    if (dateSB.length() == 0) {
      setDateToDefaults();
    }
    if (DEBUG) System.err.println("Extracting date: " + dateSB);
    if (extractMMDDYY()) {
      //print();
      return true;
    }
    boolean passed = false;
    passed=extractYear() || passed;
    passed=extractMonth() || passed;
    passed=extractDay() || passed;
    if (!passed) {
      return false;
    }
    extractRange();
    return true;
  }


  private boolean extractMMDDYY() {
    Pattern pat = Pattern.compile("([0-1]??[1-9])[ \t\n\r" + "\f]*[/-][ \t\n\r\f]*([0-3]??[0-9])[ \t\r\n\f]*[/-][ \t\r\n\f]*" + "([0-2]??[0-9]??[0-9][0-9])");
    Matcher m = pat.matcher(dateSB);
    String yearString = null;
    if (m.find()) {
      if (DEBUG) System.err.println("MMDDYY succeeded");
      startDate.set(Calendar.MONTH, Integer.parseInt(m.group(1)) - 1);
      startDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(m.group(2)));
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
      lastYearSet = Integer.parseInt(yearString);
      startDate.set(Calendar.YEAR, lastYearSet);
      return true;
    }
    return false;
  }

  private static Pattern pat = Pattern.compile("([0-1]?[1-9])\\s*[/-]\\s*([0-3]?[0-9])\\s*[/-]\\s*((?:[0-3][0-9])?[0-9][0-9])?");

  public static String getNormalizedDate(String orig) {
    if (orig.equals("01/01/1000")) { return null; }
    Matcher m = pat.matcher(orig);
    String yearString = "";
    if (m.matches()) {
      if (m.group(1).length() == 4) {
        return "00-00-"+m.group(1);
      } else {
        String month = m.group(1);
        if (month.length() == 1) { month = "0" + month; }

        if (m.group(2).length() == 4) {
          return month+"-00-"+m.group(2);
        } else {
          String day = m.group(2);
          if (day.length() == 1) { day = "0" + day; }

          String year = "??";
          if (m.group(3) != null) {
            if (!m.group(3).equals("1000") && !m.group(3).equals("10")) {
              if (m.group(3).length() == 2) {
                int yearInt = Integer.parseInt(m.group(3));
                if (yearInt < 50) {
                  year = "20" + m.group(3);
                } else {
                  year = "19" + m.group(3);
                }
              } else {
                year = m.group(3);
              }
            }
          }
          return month+"-"+day+"-"+year;
        }
      }
    }
    return null;
  }


  private Pattern re1 = Pattern.compile("[1-2][0-9]{3}|'[0-9]{2}");
  private Pattern re2 = Pattern.compile("[0-9][^0-9].*([0-9]{2})\\s*$");

  public boolean extractYear() {
    if (DEBUG) System.err.println("Extracting from: |" + dateSB + "|");
    String date = dateSB.toString();
    String extract;
    Matcher m1 = re1.matcher(date);
    Matcher m2 = re2.matcher(date);
    if (m1.find()) {
      extract = m1.group(0);
    } else if (m2.find()) {
      extract = m2.group(1);
    } else {
      return false;
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
      lastYearSet = Integer.parseInt(extract);
      startDate.set(Calendar.YEAR, lastYearSet);
      endDate.set(Calendar.YEAR, lastYearSet);
      return true;
    }
    return false;
  }

  private static Pattern[] extractorArray = {
      Pattern.compile("[Jj]anuary|JANUARY|[Jj]an\\.?|JAN\\.?"),
      Pattern.compile("[Ff]ebruary|FEBRUARY|[Ff]eb\\.?|FEB\\.?"),
      Pattern.compile("[Mm]arch|MARCH|[Mm]ar\\.?|MAR\\.?"),
      Pattern.compile("[Aa]pril|APRIL|[Aa]pr\\.?|APR\\.?"),
      Pattern.compile("[Mm]ay|MAY"),
      Pattern.compile("[Jj]une|JUNE|[Jj]un\\.?|JUN\\.?"),
      Pattern.compile("[Jj]uly|JULY|[Jj]ul\\.?|JUL\\.?"),
      Pattern.compile("[Aa]ugust|AUGUST|[Aa]ug\\.?|AUG\\.?"),
      Pattern.compile("[Ss]eptember|SEPTEMBER|[Ss]ept?\\.?|SEPT?\\.?"),
      Pattern.compile("[Oo]ctober|OCTOBER|[Oo]ct\\.?|OCT\\.?"),
      Pattern.compile("[Nn]ovember|NOVEMBER|[Nn]ov\\.?|NOV\\.?"),
      Pattern.compile("[Dd]ecember|DECEMBER|[Dd]ec\\.?|DEC\\.?") };

  /** If the parameter is a month name then it will be normalized into a
   *  capitalized full form.  Otherwise nothing will be done.
   */
  public static String normalizeMonth(String line) {
    int i;
    for (i = 0; i < 12; i++) {
      Matcher m = extractorArray[i].matcher(line);
      if (m.matches()) {
	break;
      }
    }
    switch(i) {
    case 0: return "January";
    case 1: return "February";
    case 2: return "March";
    case 3: return "April";
    case 4: return "May";
    case 5: return "June";
    case 6: return "July";
    case 7: return "August";
    case 8: return "September";
    case 9: return "October";
    case 10: return "November";
    case 11: return "December";
    default: return line;
    }
  }

  public boolean extractMonth() {
    boolean foundMonth = false;
    String line = dateSB.toString();

    for (int i = 0; i < 12; i++) {
      String extract = "";
      Matcher m = extractorArray[i].matcher(line);
      if (m.find()) {
	extract = m.group(0);
      }
      if ( ! "".equals(extract)) {
        if (!foundMonth) {
          if (DEBUG) System.err.println("month extracted: " + extract);
          startDate.set(Calendar.MONTH, i);
          endDate.set(Calendar.MONTH, i);
          foundMonth = true;
        } else if (startDate.get(Calendar.MONTH) != i) {
          if ((dateSB.toString()).indexOf("-") != -1) {
            this.isRange = true;
          }
          endDate.set(Calendar.MONTH, i);
          foundMonth = true;
        }
      }
    }
    return foundMonth;
  }

  public boolean extractDay() {
    String extract;
    for (int a = 0; a < tokens.size(); a++) {
      extract = tokens.get(a);
      extract = extract.replaceAll("[^0-9]", "");
      if (!extract.equals("")) {
        try {
          Integer i = Integer.valueOf(extract);
          if (i.intValue() < 32 && i.intValue() > 0) {
            startDate.set(Calendar.DAY_OF_MONTH, i.intValue());
            return true;
          }
        } catch (NumberFormatException e) {
          System.err.println("Exception in extract Day." + e.getStackTrace());
          System.err.println(e);
          System.err.println("tokens size :" + tokens.size());
        }
      }
    }
    return false;
  }

  /**
   * Ignores year.
   * This returns true when the passed string can be said to "fit" this
   * date, by  having the same mm/dd as the start or end date.
   */
  public boolean matches(String normalizedDate) {
    String startDateString = this.getDateString();
    if (DEBUG) System.err.println("Matching: " + startDateString + ":" + normalizedDate);

    String startDateSplit[] = startDateString.split("/");
    String targetDateSplit[] = normalizedDate.split("/");

    if (startDateSplit[0].equals(targetDateSplit[0]) && startDateSplit[1].equals(targetDateSplit[1])) {
      if (DEBUG) System.err.println("YES");
      return true;
    } else if (this.isRange) {
      String endDateSplit[] = (this.getEndDateString()).split("/");
      if (endDateSplit[0].equals(targetDateSplit[0]) && endDateSplit[1].equals(targetDateSplit[1])) {
        if (DEBUG) System.err.println("YES");
        return true;
      }
    }
    if (DEBUG) System.err.println("NO");
    return false;
  }

  private void extractRange() {
    RegexpExtractor re = new RegexpExtractor("range", "range", "[0-9][ sth\t\r\n\f.]*(?:[-/~]|through|and|to)[ \t\r\n\f]");
    String extract;
    String s;

    if (isRange) {
      return;
    }

    extract = re.extractField(dateSB.toString());
    if (extract.equals("")) {
      return;
    }
    for (int a = 0; a < tokens.size(); a++) {
      s = (tokens.get(a)).replaceAll("[^0-9]", "");
      if (!s.equals("")) {
        try {
          Integer i = Integer.valueOf(s);
          int j = i.intValue();
          if ((startDate.get(Calendar.YEAR) != j) && ((startDate.get(Calendar.DAY_OF_MONTH)) != j) && (j < 32)) {
            if (DEBUG) System.err.println("Found extra day: " + j );
            isRange = true;
            endDate.set(Calendar.DAY_OF_MONTH, j);
            return;
          }
        } catch (NumberFormatException n) {
          System.err.println("Exception in extractRange.");
          n.printStackTrace();
        }

      }
    }

  }

  private void tokenizeDate() {
    //tokens = new Vector();
    tokens = new ArrayList<String>();
    Pattern pat = Pattern.compile("[-]");
    Matcher m = pat.matcher(dateSB);
    String str = m.replaceAll(" - ");
    str = str.replaceAll(",", " ");
    PTBTokenizer tokenizer = PTBTokenizer.newPTBTokenizer(new BufferedReader(new StringReader(str)));
    while (tokenizer.hasNext()) {
      Word nextToken = (Word) tokenizer.next();
      tokens.add(nextToken.toString());
    }
    dateSB = new StringBuilder(str + " ");
  }

  private void setDateToDefaults() {
    startDate.set(Calendar.YEAR, 1000);
    startDate.set(Calendar.MONTH, 0);
    startDate.set(Calendar.DAY_OF_MONTH, 01);
    this.isRange = false;
  }


}
