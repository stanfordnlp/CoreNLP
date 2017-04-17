package edu.stanford.nlp.ie.qe;

import edu.stanford.nlp.util.StringUtils;

import java.util.Properties;

/**
 * Quantifiable Entity Extractor Options.
 *
 * @author Angel Chang
 */
public class Options {

  private static final String RULES_DIR = "edu/stanford/nlp/models/ie/qe/rules";
  private static final String[] DEFAULT_GRAMMAR_FILES = {RULES_DIR + "/english.qe.txt"};
  private static final String DEFAULT_PREFIX_FILE = RULES_DIR + "/prefixes.txt";
  private static final String DEFAULT_UNITS_FILE = RULES_DIR + "/units.txt";

  String prefixFilename = DEFAULT_PREFIX_FILE;
  String prefixRulesFilename = RULES_DIR + "/prefixes.rules.txt";
  String unitsFilename = DEFAULT_UNITS_FILE;
  String unitsRulesFilename = RULES_DIR + "/english.units.rules.txt";
  String text2UnitMapping = RULES_DIR + "/english.units.txt";
  String grammarFilename = StringUtils.join(new String[]{RULES_DIR + "/defs.qe.txt", prefixRulesFilename, unitsRulesFilename}, ",") + ',' +
      StringUtils.join(DEFAULT_GRAMMAR_FILES);


  public Options(String name, Properties props) {
    prefixFilename = props.getProperty(name + ".prefixes", prefixFilename);
    grammarFilename = props.getProperty(name + ".rules", grammarFilename);
  }

}
