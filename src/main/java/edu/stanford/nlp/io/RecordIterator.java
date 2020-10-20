package edu.stanford.nlp.io; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;
import java.io.*;

/**
 * For reading files or input streams which are structured as records and fields
 * (rows and columns).  Each time you call <code>next()</code>, you get back the
 * next record as a list of strings.  You can specify the field delimiter (as a
 * regular expression), how many fields to expect, and whether to filter lines
 * containing the wrong number of fields.
 *
 * The iterator may be empty, if the file is empty.  If there is an
 * <code>IOException</code> when <code>next()</code> is called, it is
 * caught silently, and <code>null</code> is returned (!).
 *
 * @author Bill MacCartney
 */
public class RecordIterator implements Iterator<List<String>>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(RecordIterator.class);

  private static String WHITESPACE = "\\s+";

  private BufferedReader reader;
  private int fields;                   // -1 means infer from first line of input
  private boolean filter;
  private String delim = WHITESPACE;
  private List<String> nextResult;


  // factory methods -------------------------------------------------------

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified <code>Reader</code>.
   *
   * @param reader   the reader to read from
   * @param fields   how many fields to expect in each record
   * @param filter   whether to filter lines containing wrong number of fields
   * @param delim    a regexp on which to split lines into fields (default whitespace)
   */
  public RecordIterator(Reader reader, int fields, boolean filter, String delim) { 
    this.reader = new BufferedReader(reader);
    this.fields = fields;
    this.filter = filter;
    this.delim = delim;
    if (delim == null) this.delim = WHITESPACE;
    advance();
  }

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified file.
   *
   * @param filename the file to read from
   * @param fields   how many fields to expect in each record
   * @param filter   whether to filter lines containing wrong number of fields
   * @param delim    a regexp on which to split lines into fields (default whitespace)
   */
  public RecordIterator(String filename, int fields, boolean filter, String delim) 
    throws FileNotFoundException { 
    this(new FileReader(filename), fields, filter, delim);
  }

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified <code>InputStream</code>.
   *
   * @param in       the <code>InputStream</code> to read from
   * @param fields   how many fields to expect in each record
   * @param filter   whether to filter lines containing wrong number of fields
   * @param delim    a regexp on which to split lines into fields (default whitespace)
   */
  public RecordIterator(InputStream in, int fields, boolean filter, String delim) { 
    this(new InputStreamReader(in), fields, filter, delim);
  }

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified file.  The default whitespace
   * delimiter is used.
   *
   * @param filename the file to read from
   * @param fields   how many fields to expect in each record
   * @param filter   whether to filter lines containing wrong number of fields
   */
  public RecordIterator(String filename, int fields, boolean filter) 
    throws FileNotFoundException { 
    this(filename, fields, filter, WHITESPACE);
  }

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified file.  The default whitespace
   * delimiter is used.  The first line is used to determine how many
   * fields per record to expect.
   *
   * @param filename the file to read from
   * @param filter   whether to filter lines containing wrong number of fields
   */
  public RecordIterator(String filename, boolean filter) 
    throws FileNotFoundException { 
    this(filename, -1, filter, WHITESPACE);
  }

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified file.  The default whitespace
   * delimiter is used.  Lines which contain other than <code>fields</code>
   * fields are filtered.
   *
   * @param filename the file to read from
   * @param fields   how many fields to expect in each record
   */
  public RecordIterator(String filename, int fields) 
    throws FileNotFoundException { 
    this(filename, fields, true, WHITESPACE);
  }

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified file.  No lines are filtered.
   *
   * @param filename the file to read from
   * @param delim    a regexp on which to split lines into fields (default whitespace)
   */
  public RecordIterator(String filename, String delim) 
    throws FileNotFoundException { 
    this(filename, 0, false, delim);
  }

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified file.  The default whitespace
   * delimiter is used.  No lines are filtered.
   *
   * @param filename the file to read from
   */
  public RecordIterator(String filename) 
    throws FileNotFoundException { 
    this(filename, 0, false, WHITESPACE);
  }

  /** 
   * Returns an <code>Iterator</code> over records (lists of strings)
   * corresponding to lines in the specified <code>InputStream</code>.  The
   * default whitespace delimiter is used.  No lines are filtered.
   *
   * @param in the stream to read from
   */
  public RecordIterator(InputStream in) { 
    this(in, 0, false, WHITESPACE);
  }


  // iterator methods ------------------------------------------------------

  public boolean hasNext() { 
    return (nextResult != null);
  }
  
  public List<String> next() {
    List<String> result = nextResult;
    advance();
    return result;
  }
  
  public void remove() {
    throw new UnsupportedOperationException();
  }


  // convenience methods ---------------------------------------------------

  /** 
   * A static convenience method that returns the first line of the
   * specified file as list of strings, using the specified regexp as
   * delimiter.
   *
   * @param filename the file to read from
   * @param delim    a regexp on which to split lines into fields (default whitespace)
   */
  public static List<String> firstRecord(String filename, String delim) 
    throws FileNotFoundException {
    RecordIterator it = new RecordIterator(filename, delim);
    if (!it.hasNext()) return null;
    return it.next();
  }

  /** 
   * A static convenience method that returns the first line of the
   * specified file as list of strings, using the default whitespace
   * delimiter.
   *
   * @param filename the file to read from
   */
  public static List<String> firstRecord(String filename) throws FileNotFoundException {
    return firstRecord(filename, WHITESPACE);
  }

  /** 
   * A static convenience method that tells you how many fields are in the
   * first line of the specified file, using the specified regexp as
   * delimiter.
   *
   * @param filename the file to read from
   * @param delim    a regexp on which to split lines into fields (default whitespace)
   */
  public static int determineNumFields(String filename, String delim) throws FileNotFoundException {
    List<String> fields = firstRecord(filename, delim);
    if (fields == null) return -1;
    else return fields.size();
  }

  /** 
   * A static convenience method that tells you how many fields are in the
   * first line of the specified file, using the default whitespace
   * delimiter.
   *
   * @param filename the file to read from
   */
  public static int determineNumFields(String filename) throws FileNotFoundException {
    return determineNumFields(filename, WHITESPACE);
  }


  // private methods -------------------------------------------------------

  private void advance() {
    nextResult = null;
    while (true) {                      // 2 exits in body of loop

      String line = null;
      try {
        line = reader.readLine();       // could block if reader is not ready
      } catch (IOException e) {
        // swallow it, yikes!
      }
      if (line == null) return;         // end of input: nextResult remains null
        

      String[] tokens = line.split(delim);
      if (fields < 0) fields = tokens.length; // remember number of fields in first line

      if (filter && 
          (tokens.length != fields ||   // wrong number of fields
           (tokens.length == 1 && tokens[0].equals("")))) // it's a blank line
        continue;                       // skip this line
      
      nextResult = new ArrayList<>();
      for (String token : tokens) nextResult.add(token);
      return;                           // this line will be our next result

    }
  }

  
  // -----------------------------------------------------------------------
  
  /**
   * Just for testing.  Reads from the file named on the command line, or from
   * stdin, and echoes the records it reads to stdout.
   */
  public static void main(String[] args) throws FileNotFoundException {

    RecordIterator it = null;

    if (args.length > 0) {
      it = new RecordIterator(args[0]);
    } else {
      it = new RecordIterator(System.in);
      log.info("[Reading from stdin...]");
    }
    
    while (it != null && it.hasNext()) {
      List<String> record = it.next();
      for (String field : record) {
        System.out.printf("[%-10s]", field);
      }
      System.out.println();
    }

  }

}

