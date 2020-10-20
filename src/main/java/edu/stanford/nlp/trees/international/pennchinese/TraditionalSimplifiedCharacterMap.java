package edu.stanford.nlp.trees.international.pennchinese;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.io.RuntimeIOException;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;

/**
 * This class is a Function which transforms a String of traditional
 * text into a string of simplified text.  It does this by looking for
 * and extracting all single characters from a CEDict file.
 * <br>
 * There are a few hardcoded translations to cover for ambiguities in
 * the simplified translations of traditional characters.
 *
 * <ul>
 * <li> 鹼: mapped to 碱, although 硷 is listed as a possibility in CEDict.
 * <li> 於: mapped to 于, although 於 is listed as a possibility in CEDict.
 * <li> 祇: mapped to 只, although 祇 is listed as a possibility in CEDict.
 * <li> 彷: sometimes also 彷, but 仿 is more common.
 * <li> 甚: sometimes also 甚, but 什 is more common.
 * <li> 麽: can appear as 幺麽, but very rare.  Hardcoded for now
 *          unless that causes problems.
 * </ul>
 * @author John Bauer
 */
public class TraditionalSimplifiedCharacterMap implements Function<String, String> {
  Map<String, String> map = Generics.newHashMap();

  String[][] HARDCODED = {{"鹼", "碱"},
                          {"於", "于"},
                          {"祇", "只"},
                          {"彷", "仿"},
                          {"甚", "什"},
                          {"麽", "么"}};

  public TraditionalSimplifiedCharacterMap() {
    this(CEDict.path());
  }

  public TraditionalSimplifiedCharacterMap(String path) {
    // TODO: gzipped maps might be faster
    try {
      FileInputStream fis = new FileInputStream(path);
      InputStreamReader isr = new InputStreamReader(fis, "utf-8");
      BufferedReader br = new BufferedReader(isr);
      init(br);
      br.close();
      isr.close();
      fis.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  void init(BufferedReader reader) {
    try {
      Set<String> hardcodedSet = Generics.newHashSet();
      for (String[] transform : HARDCODED) {
        hardcodedSet.add(transform[0]);
        String traditional = transform[0];
        String simplified = transform[1];
        map.put(traditional, simplified);
      }

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#")) {
          continue;
        }
        if (line.length() >= 3 &&
            line.charAt(1) == ' ' && line.charAt(3) == ' ') {
          // We're only interested in lines that represent a single character
          String traditional = line.substring(0, 1);
          String simplified = line.substring(2, 3);
          // Fail on duplicates.  Only a few come up in cedict, and
          // those that do should already be accommodated
          if (map.containsKey(traditional) && !hardcodedSet.contains(traditional) &&
              !simplified.equals(map.get(traditional))) {
            throw new RuntimeException("Character " + traditional + " mapped to " +
                                       simplified + " already mapped to " +
                                       map.get(traditional));
          }
          map.put(traditional, simplified);
        }
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public String apply(String input) {
    StringBuilder translated = new StringBuilder();
    for (int i = 0; i < input.length(); ++i) {
      String c = input.substring(i, i + 1);
      if (map.containsKey(c)) {
        translated.append(map.get(c));
      } else {
        translated.append(c);
      }
    }
    return translated.toString();
  }

  public void translateLines(BufferedReader br, BufferedWriter bw) {
    try {
      String line;
      while ((line = br.readLine()) != null) {
        bw.write(apply(line));
        bw.newLine();
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public void translateFile(String input, String output) {
    try {
      FileInputStream fis = new FileInputStream(input);
      InputStreamReader isr = new InputStreamReader(fis, "utf-8");
      BufferedReader br = new BufferedReader(isr);

      FileOutputStream fos = new FileOutputStream(output);
      OutputStreamWriter osw = new OutputStreamWriter(fos, "utf-8");
      BufferedWriter bw = new BufferedWriter(osw);

      translateLines(br, bw);

      bw.close();
      osw.close();
      fos.close();

      br.close();
      isr.close();
      fis.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static void main(String[] args) {
    TraditionalSimplifiedCharacterMap mapper = new TraditionalSimplifiedCharacterMap();
    mapper.translateFile(args[0], args[1]);
  }
}
