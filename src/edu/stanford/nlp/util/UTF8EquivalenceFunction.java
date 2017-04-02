package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.function.Function;

/**
 * A word function that can be applied to Chinese text in the tagger
 * or similar systems to make it treat ( and （ the same.
 * That is, it maps regular ASCII-range printable characters to their
 * full-width equivalents
 *
 * @author John Bauer
 */
public class UTF8EquivalenceFunction implements Function<String, String>, Serializable {

  public static String replaceAscii(String w) {
    return StringUtils.tr(w,
                          "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~",
                          "\uFF01\uFF02\uFF03\uFF04\uFF05\uFF06\uFF07\uFF08\uFF09\uFF0A\uFF0B\uFF0C\uFF0D\uFF0E\uFF0F\uFF10\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17\uFF18\uFF19\uFF1A\uFF1B\uFF1C\uFF1D\uFF1E\uFF1F\uFF20\uFF21\uFF22\uFF23\uFF24\uFF25\uFF26\uFF27\uFF28\uFF29\uFF2A\uFF2B\uFF2C\uFF2D\uFF2E\uFF2F\uFF30\uFF31\uFF32\uFF33\uFF34\uFF35\uFF36\uFF37\uFF38\uFF39\uFF3A\uFF3B\uFF3C\uFF3D\uFF3E\uFF3F\uFF40\uFF41\uFF42\uFF43\uFF44\uFF45\uFF46\uFF47\uFF48\uFF49\uFF4A\uFF4B\uFF4C\uFF4D\uFF4E\uFF4F\uFF50\uFF51\uFF52\uFF53\uFF54\uFF55\uFF56\uFF57\uFF58\uFF59\uFF5A\uFF5B\uFF5C\uFF5D\uFF5E");
  }

  @Override
  public String apply(String input) {
    if (input == null) {
      return null;
    } else if (input.equals(".$.") || input.equals(".$$.")) {
      return input;
    } else {
      return replaceAscii(input);
    }
  }

  private static final long serialVersionUID = 1L;

}
