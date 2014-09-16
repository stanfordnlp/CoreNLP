package edu.stanford.nlp.ie.qe;

import edu.stanford.nlp.util.StringUtils;

import java.util.Properties;

/**
 * Quantifiable Entity Extractor Options
 *
 * @author Angel Chang
 */
public class Options {
  static final String RULES_DIR = "C:\\code\\NLP\\javanlp\\projects\\core\\src\\edu\\stanford\\nlp\\ie\\qe\\rules";
  static final String[] DEFAULT_GRAMMAR_FILES = {RULES_DIR + "/english.qe.txt"};
  static final String DEFAULT_PREFIX_FILE = RULES_DIR + "/prefixes.txt";
  String prefixFilename = DEFAULT_PREFIX_FILE;
  String prefixRulesFilename = RULES_DIR + "/prefixes.rules.txt";
  String unitsRulesFilename = RULES_DIR + "/english.units.rules.txt";
  String text2UnitMapping = RULES_DIR + "/english.units.txt";
  String grammarFilename = StringUtils.join(new String[]{RULES_DIR + "/defs.qe.txt", prefixRulesFilename, unitsRulesFilename}, ",") + "," +
      StringUtils.join(DEFAULT_GRAMMAR_FILES);
  boolean verbose = false;

  public Options()
  {
  }

  public Options(String name, Properties props)
  {
    prefixFilename = props.getProperty(name + ".prefixes", prefixFilename);
    grammarFilename = props.getProperty(name + ".rules", grammarFilename);
  }
}
