package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.stats.ClassicCounter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Grabs dates and various features for them
 * off of GATE-annotated XML prepared for the Pascal competition.
 * Not used in the final system; currently obsolete.
 *
 * @author Chris Cox
 * @author Jamie Nicolson
 */
public class DateGrabber extends DefaultHandler {

  private final boolean trainMode = true; //If trainMode is TRUE, DateGrabber looks for PascalDocument tags.

  private LinkedList<String> tokenCache = null;
  private final int numTokensToCache = 5; //DateGrabber sends this many previous context tokens to PascalDate
  //for each instance of a date.
  private PascalDate currentDate = null;
  private ArrayList<PascalDate> dateCollector = null;
  private ArrayList<String> temporalOrder = null;

  private int numCountedDates;   //counts all valid date tags in a document.

  private int numCountedTokens;  //counts tokens in a document.

  static final int NUM_TYPES = 5;  //Number of possible labels for a date.
  static final int NUM_PARTITIONS = 10; //Number of sections to divide the document into

  // keeps track of the orderOnPage of the first occurrence of each date
  // type.
  private HashMap<String, Integer> orderOfFirst;
  private ClassicCounter<String> dateCounts;  //counts for each date.

  public DateGrabber() {
    dateCollector = new ArrayList<PascalDate>();
    System.err.println("DateGrabber created.");
  }

  @Override
  public void startDocument() {
    typeCounts = new int[NUM_TYPES][2];  //counts number of good and bad labels for a date.
    tokenCache = new LinkedList<String>();
    temporalOrder = new ArrayList<String>();
    dateCounts = new ClassicCounter<String>();
    numCountedDates = 0;
    numCountedTokens = 0;
    orderOfFirst = new HashMap<String, Integer>();

  }

  @Override
  public void endDocument() {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < NUM_TYPES; ++i) {
      for (int j = 0; j < 2; ++j) {
        sb.append(typeCounts[i][j] + ",");
      }
    }
    if (typeCounts[0][0] + typeCounts[0][1] == 0) {
      System.err.println("--> " + filename + " has no workshopdate");
    }
    setTemporalOrderingFields();
    //System.out.println(sb.toString());
    System.out.println("Document '" + filename + "' completed");
    //printDateCollector();
  }

  private void printDateCollector() {
    for (int i = 0; i < dateCollector.size(); i++) {
      PascalDate pd = dateCollector.get(i);
      pd.print();
    }
  }



  /*
  word pos kind orth label
  */

  public String findAttribute(Attributes attributes, String name) {
    int numAttributes = attributes.getLength();
    for (int i = 0; i < numAttributes; i++) {
      if (attributes.getQName(i).equalsIgnoreCase(name)) {
        return attributes.getValue(i);
      }
    }
    return null;
  }

  static String dateFields[] = {"workshopdate", "workshoppapersubmissiondate", "workshopnotificationofacceptancedate", "workshopcamerareadycopydate"};

  int dateIndex = NUM_TYPES - 1;
  int stickyDateIndex;
  int typeCounts[][];

  String normalized;
  String range;
  String occurrence;
  int goodDate;

  /*
   * startElement
   * ------------
   * Each time we start a tag, we check to see if it is a date, token, or a special Pascal tag if we are training.
   * If the tag is a date and is valid, we create a new PascalDate and send the features to it.
   * If the tag is a token, we cache the token value.
   * If the tag is a PascalTag, we set the stickyDateIndex parameter, (Pascal Tags can occur before or after
   * Date tags, we want to ensure that the Date is matched up with its PascalTag in either case).
   */

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {
    try {
      if (qName.equalsIgnoreCase("date")) {
        // start of a date tag
        normalized = findAttribute(attributes, "normalized");
        range = findAttribute(attributes, "range");
        occurrence = findAttribute(attributes, "occurrence");
        stickyDateIndex = dateIndex;

        if (normalized.equals("1/1/1000")) {
          goodDate = 0;
        } else {
          goodDate = 1;
        }

        if (goodDate == 1) {
          currentDate = new PascalDate();
          currentDate.date = normalized;
          currentDate.documentName = filename;
          currentDate.isRange = range.equalsIgnoreCase("true");
          currentDate.occurrenceIndex = Integer.parseInt(occurrence);
          currentDate.orderOnPageIndex = numCountedDates;
          if (!orderOfFirst.containsKey(currentDate.date)) {
            orderOfFirst.put(currentDate.date, Integer.valueOf(numCountedDates));

          }
          currentDate.orderOfFirst = orderOfFirst.get(currentDate.date).intValue();
          if (stickyDateIndex < dateFields.length) {
            currentDate.pascalTag = dateFields[stickyDateIndex];
            //currentDate.pascalTag = "keeper";
          }
          currentDate.posTag = findAttribute(attributes, "POS");
          currentDate.prevTokens = tokenCache.toArray(new String[numTokensToCache]);
          currentDate.tokenIndex = numCountedTokens;
          dateCollector.add(currentDate);
          processTemporalOrdering(normalized);
        }
        //System.out.println("Setting stickydateindex to " stickyDateIndex);
      } else if (qName.equalsIgnoreCase("token")) {
        tokenCache.add(findAttribute(attributes, "string"));
        if (tokenCache.size() > numTokensToCache) {
          tokenCache.removeFirst();
        }
        numCountedTokens++;

      } else if (trainMode) {

        int openingIndex = computeDateIndex(qName);
        if (openingIndex != NUM_TYPES - 1) {
          dateIndex = openingIndex;

          stickyDateIndex = dateIndex;
          //System.out.println("Setting stickydateindex to " +
          //    stickyDateIndex);
        }
      }
      //System.out.println("opening " + qName + ", index is " + dateIndex);
    } catch (RuntimeException e) {
      e.printStackTrace();
      throw e;
    }
  }

  /*
   * processTemporalOrdering
   * -----------------------
   * Inserts the passed date(in string form: mm/dd/yyyy) into the temporalOrder
   * ArrayList, wherever it should be.  It returns the index it inserted at.  If the date
   * was already present, it returns the index of that date.  Also increments dateCounter for the
   * key "dateString"
   */

  private int processTemporalOrdering(String dateString) {
    int i = 0;
    String[] date1 = dateString.split("/");
    //System.err.println("Split :" +dateString+ ": " + date1[0]+" "+ date1[1]  + " "+ date1[2]);
    for (i = 0; i < temporalOrder.size(); i++) {
      String s = temporalOrder.get(i);
      String[] date2 = s.split("/");
      switch (compareDateStrings(date1, date2)) {
        case 1:
          temporalOrder.add(i, dateString);
          dateCounts.setCount(dateString, 1.0);
          return i;
        case 0:
          dateCounts.incrementCount(dateString);
          return i;
      }
    }
    //dateString is the latest, we add it to the end.
    temporalOrder.add(dateString);
    dateCounts.setCount(dateString, 1.0);
    return i;

  }

  /*
   * setTemporalOrderingFields
   * ------------------------
   * Called from endDocument, this sets each PascalDate's temporalOrdering field to the
   * appropriate index of the temporalOrdering array, which is complete only at the end of the document.
   * Also sets the numOccurrences field to the appropriate tally.
   */

  private void setTemporalOrderingFields() {
    //System.err.println("Setting ordering: ");
    //System.err.println(temporalOrder);
    for (int i = (dateCollector.size() - numCountedDates); i < dateCollector.size(); i++) {
      PascalDate pd = dateCollector.get(i);
      pd.temporalOrderIndex = temporalOrder.indexOf(pd.date);
      pd.tokenIndex = (pd.tokenIndex * NUM_PARTITIONS) / numCountedTokens;
      pd.numOccurrences = dateCounts.getCount(pd.date);
    }
  }

  /*
   * compareDateStrings
   * ------------------
   * returns 1 if date1 is earlier than date2,
   * returns -1 if date1 is later than date2,
   * returns 0 if they are identical.
   */
  private int compareDateStrings(String[] date1, String[] date2) {
    int[] d1 = {Integer.parseInt(date1[0]), Integer.parseInt(date1[1]), Integer.parseInt(date1[2])};
    int[] d2 = {Integer.parseInt(date2[0]), Integer.parseInt(date2[1]), Integer.parseInt(date2[2]),};
    if (d1[2] < d2[2]) {
      return 1;
    }
    if (d1[2] > d2[2]) {
      return -1;
    }
    if (d1[0] < d2[0]) {
      return 1;
    }
    if (d1[0] > d2[0]) {
      return -1;
    }
    if (d1[1] < d2[1]) {
      return 1;
    }
    if (d1[1] > d2[1]) {
      return -1;
    }
    return 0;
  }


  /*computeDateIndex
   *------------------
   * Determines if the passed name is one of the tagged fields.  (Only during training)
   */
  private int computeDateIndex(String qName) {
    int index = NUM_TYPES - 1;
    for (int i = 0; i < dateFields.length; ++i) {
      if (qName.equalsIgnoreCase(dateFields[i])) {
        index = i;
        break;
      }
    }
    return index;
  }

  @Override
  public void endElement(java.lang.String namespaceURI, java.lang.String localName, java.lang.String qName) {
    //if(qName.equalsIgnoreCase("date")) {

    if (trainMode) {
      int closingIndex = computeDateIndex(qName);
      //System.out.println("closing " + qName + ", index is " + closingIndex);
      if (closingIndex != NUM_TYPES - 1) {
        //assert( closingIndex == dateIndex);
        dateIndex = NUM_TYPES - 1;
      }
      if (qName.equalsIgnoreCase("date")) {
        if (goodDate == 1) {
          numCountedDates++;
        }
        typeCounts[stickyDateIndex][goodDate]++;

      }
    }
  }

  @Override
  public void warning(SAXParseException e) {
    System.err.println("Parse Exception warning caught.");
  }

  String filename;

  public void printFeaturesAndLabels(PrintWriter output) {
    for (int i = 0; i < dateCollector.size(); i++) {
      PascalDate pd = dateCollector.get(i);

      StringBuffer sb = new StringBuffer();

      // prevTokens
      int numTokens = pd.prevTokens.length;
      for (int t = 0; t < numTokens; ++t) {
        sb.append("prevToken" + t + "=" + pd.prevTokens[numTokens - t - 1] + " ");
        sb.append("prevToken" + "=" + pd.prevTokens[numTokens - t - 1] + " ");
      }

      // range
      sb.append("isRange=" + (pd.isRange ? "true " : "false "));

      // order on page
      sb.append("orderOnPage=" + pd.orderOnPageIndex + " ");

      // which fraction of page
      sb.append("fractionOfPage=" + pd.tokenIndex + " ");

      // num occurrences of date
      sb.append("numOccur=" + pd.numOccurrences + " ");

      // order on page of first occurrence
      sb.append("orderOfFirst=" + pd.orderOfFirst + " ");

      // temporal order of date
      sb.append("temporalOrder=" + pd.temporalOrderIndex + " ");

      // pascal tag is the label
      sb.append("label=" + pd.pascalTag + " ");

      output.println(sb.toString());
    }
  }

  public static void main(String[] args) throws Exception {
    SAXParserFactory xmlParserFactory = SAXParserFactory.newInstance();
    SAXParser xmlParser = xmlParserFactory.newSAXParser();
    //PascalHandler ph = new PascalHandler();
    DateGrabber ph = new DateGrabber();
    for (int a = 0; a < args.length; ++a) {
      //           System.err.println("Processing file " + a + ": " + args[a]);
      File infile = new File(args[a]);
      try {
        ph.filename = args[a];
        //       System.out.println("Parsing:" + ph.filename);
        xmlParser.parse(infile, ph);
        //System.out.println();
      } catch (Exception e) {
        System.err.println("exception caught in file:" + args[a]);
        e.printStackTrace();
      }
    }
    //ph.printDateCollector();
    PrintWriter outputFile = new PrintWriter(new FileOutputStream("date_features"));
    ph.printFeaturesAndLabels(outputFile);
    outputFile.close();
  }
}
