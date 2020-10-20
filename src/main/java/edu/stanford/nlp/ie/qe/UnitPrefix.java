package edu.stanford.nlp.ie.qe;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.tokensregex.Env;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Potential prefix that goes in front of a quantifiable unit
 *
 * @author Angel Chang
 */
public class UnitPrefix {
  public String name;
  public String symbol;
  // What does this prefix do to the unit?
  public Double scale;
  public String system;

  public UnitPrefix(String name, String symbol, Double scale, String system) {
    this.name = name;
    this.symbol = symbol;
    this.scale = scale;
    this.system = system;
  }

  private Unit convert(Unit u) {
    return new Unit(
            name + u.getName(), symbol + u.getSymbol(),
            u.getType(), u, scale
         );
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public Double getScale() {
    return scale;
  }

  public void setScale(Double scale) {
    this.scale = scale;
  }

  public void setScale(Number scale) {
    this.scale = scale.doubleValue();
  }

  public static void registerPrefixes(Env env, String filename) throws IOException {
    List<UnitPrefix> prefixes = loadPrefixes(filename);
    registerPrefixes(env, prefixes);
  }

  public static void registerPrefixes(Env env, List<UnitPrefix> prefixes) {
    for (UnitPrefix prefix: prefixes) {
      registerPrefix(env, prefix);
    }
  }

  public static void registerPrefix(Env env, UnitPrefix prefix) {
    env.bind(prefix.getName().toUpperCase(), prefix);
  }

  public static List<UnitPrefix> loadPrefixes(String filename) throws IOException {
    Pattern commaPattern = Pattern.compile("\\s*,\\s*");
    BufferedReader br = IOUtils.readerFromString(filename);
    String headerString = br.readLine();
    String[] header = commaPattern.split(headerString);
    Map<String,Integer> headerIndex = new HashMap<>();
    for (int i = 0; i < header.length; i++) {
      headerIndex.put(header[i], i);
    }
    int iName = headerIndex.get("name");
    int iPrefix = headerIndex.get("prefix");
    int iBase = headerIndex.get("base");
    int iExp = headerIndex.get("exp");
    int iSystem = headerIndex.get("system");
    String line;
    List<UnitPrefix> list = new ArrayList<>();
    while ((line = br.readLine()) != null) {
      String[] fields = commaPattern.split(line);
      double base = Double.parseDouble(fields[iBase]);
      double exp = Double.parseDouble(fields[iExp]);
      double scale = Math.pow(base, exp);
      UnitPrefix unitPrefix = new UnitPrefix(fields[iName], fields[iPrefix], scale, fields[iSystem]);
      list.add(unitPrefix);
    }
    br.close();
    return list;
  }
}
