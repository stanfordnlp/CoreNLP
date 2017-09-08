package old.edu.stanford.nlp.util;

import java.util.*;

/**
 * Class for memoizing the result of string concatenations.
 * This class is probably a better choice than StringBuilder
 * and StringBuffer if many string concatenations are identical.
 * 
 * @author Michel Galley
 */
public class StringBuildMemoizer {

  private static int stringAllowance = 8; // reduces default 16 -> 8

  private static Map<ArrayWrapper<String>,String> m
    = new HashMap<ArrayWrapper<String>,String>();

  public static void setStringAllowance(int a) {
    stringAllowance = a;
  }

  public static String toString(String... arr) {
    ArrayWrapper<String> aw = new ArrayWrapper<String>(arr);
    String oldstr = m.get(aw);
    if(oldstr != null)
      return oldstr;
    StringBuilder sb = new StringBuilder(stringAllowance);
    for(String s : arr)
      sb.append(s);
    String newstr = sb.toString();
    m.put(aw,newstr);
    return newstr;
  }

  private static String uncachedToString(String... arr) {
    StringBuilder sb = new StringBuilder(stringAllowance);
    for(String s : arr)
      sb.append(s);
    return sb.toString();
  }

  public static void main(String[] args) {
    int it = Integer.parseInt(args[0]);
    int sz = Integer.parseInt(args[1]);
    boolean cached = Boolean.parseBoolean(args[2]);

    String[] tagsets = new String[]{"CC", "CD", "JJ", "LB", "NN", "VBD", "VBZ", "DT" };
    String[] conj = new String[sz];
		Random r = new Random();
    for(int i=0; i<it; ++i) {
      for(int j=0; j<conj.length; ++j)
				conj[j] = tagsets[r.nextInt(tagsets.length)];
      if(cached)
        StringBuildMemoizer.toString(conj);
      else
        StringBuildMemoizer.uncachedToString(conj);
    }
  }
}
