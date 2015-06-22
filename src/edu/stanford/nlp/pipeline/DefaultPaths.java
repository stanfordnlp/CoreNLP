package edu.stanford.nlp.pipeline;

/**
 * Default model paths for StanfordCoreNLP
 * All these paths point to files distributed with the model jar file (stanford-corenlp-models-*.jar)
 */
public class DefaultPaths {

  public static final String DEFAULT_POS_MODEL = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";

  public static final String DEFAULT_PARSER_MODEL = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";

  public static final String DEFAULT_NER_THREECLASS_MODEL = "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";
  public static final String DEFAULT_NER_CONLL_MODEL = "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz";
  public static final String DEFAULT_NER_MUC_MODEL = "edu/stanford/nlp/models/ner/english.muc.7class.distsim.crf.ser.gz";

  public static final String DEFAULT_REGEXNER_RULES = "edu/stanford/nlp/models/regexner/type_map_clean";
  public static final String DEFAULT_GENDER_FIRST_NAMES = "edu/stanford/nlp/models/gender/first_name_map_small";

  public static final String DEFAULT_TRUECASE_MODEL = "edu/stanford/nlp/models/truecase/truecasing.fast.caseless.qn.ser.gz";
  public static final String DEFAULT_TRUECASE_DISAMBIGUATION_LIST = "edu/stanford/nlp/models/truecase/MixDisambiguation.list";

  public static final String DEFAULT_DCOREF_ANIMATE = "edu/stanford/nlp/models/dcoref/animate.unigrams.txt";
  public static final String DEFAULT_DCOREF_DEMONYM = "edu/stanford/nlp/models/dcoref/demonyms.txt";
  public static final String DEFAULT_DCOREF_INANIMATE = "edu/stanford/nlp/models/dcoref/inanimate.unigrams.txt";
  public static final String DEFAULT_DCOREF_STATES = "edu/stanford/nlp/models/dcoref/state-abbreviations.txt";

  public static final String DEFAULT_DCOREF_COUNTRIES = "edu/stanford/nlp/models/dcoref/countries";
  public static final String DEFAULT_DCOREF_STATES_AND_PROVINCES = "edu/stanford/nlp/models/dcoref/statesandprovinces";
  public static final String DEFAULT_DCOREF_GENDER_NUMBER = "edu/stanford/nlp/models/dcoref/gender.map.ser.gz";
  
  public static final String DEFAULT_DCOREF_SINGLETON_MODEL = "edu/stanford/nlp/models/dcoref/singleton.predictor.ser";
  public static final String DEFAULT_DCOREF_DICT1 = "edu/stanford/nlp/models/dcoref/coref.dict1.tsv";
  public static final String DEFAULT_DCOREF_DICT2 = "edu/stanford/nlp/models/dcoref/coref.dict2.tsv";
  public static final String DEFAULT_DCOREF_DICT3 = "edu/stanford/nlp/models/dcoref/coref.dict3.tsv";
  public static final String DEFAULT_DCOREF_DICT4 = "edu/stanford/nlp/models/dcoref/coref.dict4.tsv";
  public static final String DEFAULT_DCOREF_NE_SIGNATURES = "edu/stanford/nlp/models/dcoref/ne.signatures.txt";

  public static final String DEFAULT_NFL_ENTITY_MODEL = "edu/stanford/nlp/models/machinereading/nfl/nfl_entity_model.ser";
  public static final String DEFAULT_NFL_RELATION_MODEL = "edu/stanford/nlp/models/machinereading/nfl/nfl_relation_model.ser";
  public static final String DEFAULT_NFL_GAZETTEER = "edu/stanford/nlp/models/machinereading/nfl/NFLgazetteer.txt";
  
  public static final String DEFAULT_SUP_RELATION_EX_RELATION_MODEL = "edu/stanford/nlp/models/supervised_relation_extractor/roth_relation_model.ser";


  private DefaultPaths() {
  }
  
}
