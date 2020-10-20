package edu.stanford.nlp.ie.util; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;

/**
 * A script that goes through a data file and looks for instances
 * where place, place should have the , tagged as well.
 *
 * @author jrfinkel
 */
public class FixLocation  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FixLocation.class);

  public static final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

  static String inputFilename = null;
  static String outputFilename = null;

  private FixLocation() {} // static class

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      log.info("Input filename?");
      inputFilename = in.readLine();
    } else {
      inputFilename = args[0];
    }
    if (args.length < 2) {
      log.info("Output filename?");
      outputFilename = in.readLine();
    } else {
      outputFilename = args[1];
    }

    String[][] cols = readFile(inputFilename);
    fix(cols);
    print(cols);
  }

  public static String[][] readFile(String filename) throws Exception {
    String file = IOUtils.slurpFile(filename);
    String[] lines = file.split("\n");
    String[][] cols = new String[lines.length][];
    for (int i = 0; i < lines.length; i++) {
      cols[i] = lines[i].split("\\s+");
    }
    return cols;
  }

  public static void fix(String[][] cols) throws Exception {
    for (int i = 1; i < cols.length-1; i++) {
      if (cols[i-1].length < 2) { continue; }
      if (cols[i].length < 2) { continue; }
      if (cols[i+1].length < 2) { continue; }

      String prevLabel = cols[i-1][1];
      String curWord = cols[i][0];
      String nextLabel = cols[i+1][1];
      if (prevLabel.equals("LOCATION") &&
          nextLabel.equals("LOCATION") &&
          curWord.equals(",")) {
        query(cols, i);
      }
    }
  }

  public static BufferedReader answers;
  static {
    try {
      answers = new BufferedReader(new FileReader("answers"));
    } catch (Exception e) {}
  }

  private static Map<String,String> cache = Generics.newHashMap();

  public static void query(String[][] cols, int pos) throws Exception {
    String pre = "";
    if (cols[pos-1][0].matches("[-A-Z]*")) {
      cols[pos][1] = "LOCATION";
      return;
    }
    for (int i = pos-1; i >= 0 && cols[i].length >= 2; i--) {
      if (cols[i][1].equals("LOCATION")) {
        if (pre.equals("")) {
          pre = cols[i][0];
        } else {
          pre = cols[i][0] + " " + pre;
        }
      } else {
        break;
      }
    }

    String post = "";
    for (int i = pos+1; i < cols.length && cols[i].length >= 2; i++) {
      if (cols[i][1].equals("LOCATION")) {
        if (post.equals("")) {
          post = cols[i][0];
        } else {
          post = post + " " + cols[i][0];
        }
      } else {
        break;
      }
    }

    String ans = (answers == null) ? "": answers.readLine();
    String loc = pre+","+post+" ?";

    log.info(loc);

    if (ans.equals(loc)) {
      String response = answers.readLine();
      log.info(response);
      if (ans.equalsIgnoreCase("Y")) {
        cols[pos][1] = "LOCATION";
      }
    } else {
      ans = cache.get(loc);
      if (ans == null) {
        if (in.readLine().equalsIgnoreCase("Y")) {
          cache.put(loc, "Y");
          cols[pos][1] = "LOCATION";
        } else {
          cache.put(loc, "N");
        }
      } else if (ans.equalsIgnoreCase("Y")) {
        cols[pos][1] = "LOCATION";
        log.info("Y");
      }
    }
  }

  public static void print(String[][] cols) throws Exception {
    BufferedWriter out = new BufferedWriter(new FileWriter(outputFilename));
    for (String[] col : cols) {
      if (col.length >= 2) {
        out.write(col[0] + "\t" + col[1] + "\n");
      } else {
        out.write("\n");
      }
    }
    out.flush();
    out.close();
  }

}
