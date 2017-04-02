package edu.stanford.nlp.dcoref.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import edu.stanford.nlp.dcoref.Dictionaries.Gender;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;

/**
 * This tool converts the gender file from the following:
 * <br>
 * w1 w2... TAB male female neutral <br>
 * etc <br>
 * <br>
 * into a serialized data structure which should take much less time to load.
 *
 * @author John Bauer
 */
public class ConvertGenderFile {

  private ConvertGenderFile() {} // static class

  public static void main(String[] args) throws IOException {
    String input = null;
    String output = null;
    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-input")) {
        input = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        output = args[argIndex + 1];
        argIndex += 2;
      } else {
        throw new IllegalArgumentException("Unknown argument " + args[argIndex]);
      }
    }

    if (input == null) {
      throw new IllegalArgumentException("Must specify input with -input");
    }

    if (output == null) {
      throw new IllegalArgumentException("Must specify output with -output");
    }

    Map<List<String>, Gender> genderNumber = Generics.newHashMap();

    BufferedReader reader = IOUtils.readerFromString(input);
    for (String line; (line = reader.readLine()) != null; ) {
      String[] split = line.split("\t");
      String[] countStr = split[1].split(" ");

      int male = Integer.parseInt(countStr[0]);
      int female = Integer.parseInt(countStr[1]);
      int neutral = Integer.parseInt(countStr[2]);

      Gender gender = Gender.UNKNOWN;
      if (male * 0.5 > female + neutral && male > 2) {
        gender = Gender.MALE;
      } else if (female * 0.5 > male + neutral && female > 2) {
        gender = Gender.FEMALE;
      } else if (neutral * 0.5 > male + female && neutral > 2) {
        gender = Gender.NEUTRAL;
      }

      if (gender == Gender.UNKNOWN) {
        continue;
      }

      String[] words = split[0].split(" ");
      List<String> tokens = Arrays.asList(words);

      genderNumber.put(tokens, gender);
    }

    IOUtils.writeObjectToFile(genderNumber, output);
  }
}
