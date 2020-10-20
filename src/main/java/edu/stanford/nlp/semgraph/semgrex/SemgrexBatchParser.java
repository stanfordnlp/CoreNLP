package edu.stanford.nlp.semgraph.semgrex; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Parses a batch of SemgrexPatterns from a stream.
 * Each SemgrexPattern must be defined in a single line.
 * This includes a preprocessor that supports macros, defined as: "macro NAME = VALUE" and used as ${NAME}
 * For example:
 *   # lines starting with the pound sign are skipped
 *   macro JOB = president|ceo|star
 *   {}=entity &gt;appos ({lemma:/${JOB}/} &gt;nn {ner:ORGANIZATION}=slot)
 */
public class SemgrexBatchParser  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SemgrexBatchParser.class);

  /** Maximum stream size in characters */
  private static final int MAX_STREAM_SIZE = 1024 * 1024;

  public static boolean VERBOSE = false;

  private SemgrexBatchParser() { } // static methods class

  public static List<SemgrexPattern> compileStream(InputStream is) throws IOException {
    return compileStream(is, null);
  }

  public static List<SemgrexPattern> compileStream(InputStream is, Env env) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    reader.mark(MAX_STREAM_SIZE);
    Map<String, String> macros = preprocess(reader);
    reader.reset();
    return parse(reader, macros, env);
  }

  private static List<SemgrexPattern> parse(BufferedReader reader, Map<String, String> macros, Env env) throws IOException {
    List<SemgrexPattern> patterns = new ArrayList<>();
    for(String line; (line = reader.readLine()) != null; ) {
      line = line.trim();
      if(line.isEmpty() || line.startsWith("#")) continue;
      if(line.startsWith("macro ")) continue;
      line = replaceMacros(line, macros);
      SemgrexPattern pattern = SemgrexPattern.compile(line, env);
      patterns.add(pattern);
    }
    return patterns;
  }

  private static final Pattern MACRO_NAME_PATTERN = Pattern.compile("\\$\\{[a-z0-9]+\\}", Pattern.CASE_INSENSITIVE);

  private static String replaceMacros(String line, Map<String, String> macros) {
    StringBuilder out = new StringBuilder();
    Matcher matcher = MACRO_NAME_PATTERN.matcher(line);
    int offset = 0;
    while(matcher.find(offset)) {
      int start = matcher.start();
      int end = matcher.end();
      String name = line.substring(start + 2, end - 1);
      String value = macros.get(name);
      if(value == null){
        throw new RuntimeException("ERROR: Unknown macro \"" + name + "\"!");
      }
      if(start > offset) {
        out.append(line.substring(offset, start));
      }
      out.append(value);
      offset = end;
    }
    if(offset < line.length()) out.append(line.substring(offset));
    String postProcessed =  out.toString();
    if(!postProcessed.equals(line) && VERBOSE) log.info("Line \"" + line + "\" changed to \"" + postProcessed + '"');
    return postProcessed;
  }

  private static Map<String, String> preprocess(BufferedReader reader) throws IOException {
    Map<String, String> macros = Generics.newHashMap();
    for(String line; (line = reader.readLine()) != null; ) {
      line = line.trim();
      if(line.startsWith("macro ")){
        Pair<String, String> macro = extractMacro(line);
        macros.put(macro.first(), macro.second());
      }
    }
    return macros;
  }

  private static Pair<String, String> extractMacro(String line) {
    assert(line.startsWith("macro"));
    int equalPosition = line.indexOf('=');
    if(equalPosition < 0) {
      throw new RuntimeException("ERROR: Invalid syntax in macro line: \"" + line + "\"!");
    }
    String name = line.substring(5, equalPosition).trim();
    if(name.isEmpty()) {
      throw new RuntimeException("ERROR: Invalid syntax in macro line: \"" + line + "\"!");
    }
    String value = line.substring(equalPosition + 1).trim();
    if(value.isEmpty()) {
      throw new RuntimeException("ERROR: Invalid syntax in macro line: \"" + line + "\"!");
    }
    return new Pair<>(name, value);
  }

}
