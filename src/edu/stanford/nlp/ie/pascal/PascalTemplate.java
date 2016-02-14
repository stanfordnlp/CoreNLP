package edu.stanford.nlp.ie.pascal; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps non-background Pascal fields to strings.
 *
 * @author Chris Cox
 */


public class PascalTemplate  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(PascalTemplate.class);

  public static final String[] fields = {
    //dates
    "workshoppapersubmissiondate",
    "workshopnotificationofacceptancedate",
    "workshopcamerareadycopydate",
    "workshopdate",
    //location
    "workshoplocation",
    //workshop info
    "workshopacronym",
    "workshophomepage",
    "workshopname",
    //conference info
    "conferenceacronym",
    "conferencehomepage",
    "conferencename",
    //background symbol
    "0"
  };

  public static final String BACKGROUND_SYMBOL = "0";

  private static final Index<String> fieldIndices;

  static {
    fieldIndices = new HashIndex<>();
    for (String field : fields) {
      fieldIndices.add(field);
    }
  }

  private final String[] values;


  public PascalTemplate() {
    values = new String[fields.length];
    for (int i = 0; i < values.length; i++) {
      values[i] = null;
    }
  }

  //copy constructor
  public PascalTemplate(PascalTemplate pt) {
    this.values = new String[fields.length];
    for (int i = 0; i < values.length; i++) {
      if (pt.values[i] == null) {
        this.values[i] = null;
      } else {
        this.values[i] = pt.values[i];
      }
    }
  }

  /*
   * Acronym stemming and matching fields
   */
  private static Pattern acronymPattern = Pattern.compile("([ \r-/a-zA-Z]+?)(?:[ -'*\t\r\n\f0-9]*)", Pattern.DOTALL);

  /**
   *
   */
  public static boolean acronymMatch(String s1, String s2, HashMap stemmedAcronymIndex) {
    log.info("Testing match:" + s1 + " : " + s2);
    String stem1 = (String) stemmedAcronymIndex.get(s1);
    String stem2 = (String) stemmedAcronymIndex.get(s2);
    log.info("Got stems:" + s1 + " : " + s2);
    return stem1.equals(stem2);
  }
  /**
   *
   */
  public static String stemAcronym(String s, CliqueTemplates ct) {
    if (ct.stemmedAcronymIndex.containsKey(s)) {
      return (String) ct.stemmedAcronymIndex.get(s);
    }
    Matcher matcher = acronymPattern.matcher(s);
    if (!matcher.matches() || s.equalsIgnoreCase("www")) {
      log.info("Not a valid acronym: " + s);
      return "null";
    }

    String stemmed = matcher.group(1).toLowerCase();
    if (stemmed.endsWith("-")) {
      stemmed = stemmed.substring(0, stemmed.length() - 1);
    }

    ct.stemmedAcronymIndex.put(s, stemmed);
    log.info("Stemmed: " + s + " to: " + stemmed);
    if (ct.inverseAcronymMap.containsKey(stemmed)) {
      HashSet set = (HashSet) ct.inverseAcronymMap.get(stemmed);
      set.add(s);
    } else {
      HashSet set = new HashSet();
      set.add(s);
      ct.inverseAcronymMap.put(stemmed, set);
    }
    return stemmed;
  }

/**
 * Merges partial (clique) templates into a full one.
 *
 * @param dt date template
 * @param location location
 * @param wi workshop/conference info template
 * @return the {@link PascalTemplate} resulting from this merge.
 */

  public static PascalTemplate mergeCliqueTemplates(DateTemplate dt, String location, InfoTemplate wi) {
    PascalTemplate pt = new PascalTemplate();
    pt.setValue("workshopnotificationofacceptancedate", dt.noadate);
    pt.setValue("workshopcamerareadycopydate", dt.crcdate);
    pt.setValue("workshopdate", dt.workdate);
    pt.setValue("workshoppapersubmissiondate", dt.subdate);
    pt.setValue("workshoplocation", location);
    pt.setValue("workshopacronym", wi.wacronym);
    pt.setValue("workshophomepage", wi.whomepage);
    pt.setValue("workshopname", wi.wname);
    pt.setValue("conferenceacronym", wi.cacronym);
    pt.setValue("conferencehomepage", wi.chomepage);
    pt.setValue("conferencename", wi.cname);
    return pt;
  }

/**
 * Sets template values.
 * @param fieldName (i.e. workshopname, workshopdate)
 */
  public void setValue(String fieldName, String value) {
    int index = getFieldIndex(fieldName);
    assert(index != -1);
    values[index] = value;
  }

  public void setValue(int index, String value) {
    if (index != values.length - 1) {
      values[index] = value;
    }
  }

  public String getValue(String fieldName) {
    int i = getFieldIndex(fieldName);
    if (i == -1 || i == values.length - 1) {
      return null;
    } else {
      return values[i];
    }
  }

  @Override
  public boolean equals(Object obj) {

    if (obj == null) {
      return false;
    }
    if (!(obj instanceof PascalTemplate)) {
      return false;
    }

    PascalTemplate pt = (PascalTemplate) obj;
    String[] values2 = pt.values;

    if (values.length != values2.length) {
      return false;
    }

    for (int i = 0; i < values.length - 1; i++) {
      if (values[i] == null) {
        if (values2[i] != null) {
          return false;
        }
      } else {
        if (values2[i] == null) {
          return false;
        }
        if (!values2[i].equals(values[i])) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int tally = 37;
    for (int i = 0; i < values.length - 1; i++) {
      int n;
      if (values[i] == null) {
        n = 11;
      } else {
        n = values[i].hashCode();
      }
      tally = 17 * tally + n;
    }
    return tally;
  }

  /**
   *
   * @param tag field name (i.e. workshopdate, workshoplocation)
   * @return the reference of that field in the underlying {@link edu.stanford.nlp.util.Index}
   */
  public static int getFieldIndex(String tag) {
    return (fieldIndices.indexOf(tag));
  }

  /**
   * Should be passed a <code>Counter[]</code>, each entry of which
   * keeps scores for possibilities in that template slot.  The counter
   * for each template value is incremented by the corresponding score of
   * this PascalTemplate.
   *
   * @param fieldValueCounter an array of counters, each of which holds label possibilities for one field
   * @param score increment counts by this much.
   */

  public void writeToFieldValueCounter(Counter<String>[] fieldValueCounter, double score) {
    for (int i = 0; i < fields.length; i++) {
      if ((values[i] != null) && !values[i].equals("NULL")) {
        fieldValueCounter[i].incrementCount(values[i], score);
      }
    }
  }
/**
 * Divides this template into partial templates, and updates the counts of these
 * partial templates in the {@link CliqueTemplates} object.
 *
 * @param ct the partial templates counter object
 * @param score increment counts by this much
 */
  public void unpackToCliqueTemplates(CliqueTemplates ct, double score) {

    ct.dateCliqueCounter.incrementCount(new DateTemplate(values[0], values[1], values[2], values[3]), score);
    if (values[4] != null) {
      ct.locationCliqueCounter.incrementCount(values[4], score);
    }

    ct.workshopInfoCliqueCounter.incrementCount(new InfoTemplate(values[6], values[5], values[7], values[9], values[8], values[10], ct), score);
  }

  public void print() {
    log.info("PascalTemplate: ");
    log.info(this.toString());
  }

  @Override
  public String toString() {
    String str = "\n====================\n";
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null) {
        if (!(values[i].equalsIgnoreCase("NULL"))) {
          str = str.concat(fields[i] + " : " + values[i] + "\n");
        }
      }
    }
    return str;
  }
}
