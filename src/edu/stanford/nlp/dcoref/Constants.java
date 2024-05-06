package edu.stanford.nlp.dcoref;

import java.util.Locale;
import java.util.logging.Logger;

public class Constants {

  protected Constants() {} // static class but extended by jcoref

  /** if true, use truecase annotator */
  public static final boolean USE_TRUECASE = false;

  /** if true, use gold speaker tags */
  public static final boolean USE_GOLD_SPEAKER_TAGS = false;

  /** if false, use Stanford NER to predict NE labels */
  public static final boolean USE_GOLD_NE = false;

  /** if false, use Stanford parse to parse */
  public static final boolean USE_GOLD_PARSES = false;

  /** if false, use Stanford tagger to tag */
  public static final boolean USE_GOLD_POS = false;

  /** if false, use mention prediction */
  public static final boolean USE_GOLD_MENTIONS = false;

  /** if true, use given mention boundaries */
  public static final boolean USE_GOLD_MENTION_BOUNDARIES = false;

  /** Flag for using discourse salience */
  public static final boolean USE_DISCOURSE_SALIENCE = true;

  /** Use person attributes in pronoun matching */
  public static final boolean USE_DISCOURSE_CONSTRAINTS = true;

  /** if true, remove appositives, predicate nominatives in post processing */
  public static final boolean REMOVE_APPOSITION_PREDICATENOMINATIVES = true;

  /** if true, remove singletons in post processing */
  public static final boolean REMOVE_SINGLETONS = true;

  /** if true, read *auto_conll, if false, read *gold_conll */
  public static final boolean USE_CONLL_AUTO = true;

  /** if true, print in conll output format */
  public static final boolean PRINT_CONLL_OUTPUT = false;

  /** Default path for conll scorer script */
  public static final String conllMentionEvalScript = "/u/nlp/data/coref/conll-2012/scorer/v4/scorer.pl";

  /** if true, skip coreference resolution. do mention detection only */
  public static final boolean SKIP_COREF = false;

  /** Default sieve passes */
  public static final String SIEVEPASSES = "MarkRole, DiscourseMatch, ExactStringMatch, RelaxedExactStringMatch, PreciseConstructs, StrictHeadMatch1, StrictHeadMatch2, StrictHeadMatch3, StrictHeadMatch4, RelaxedHeadMatch, PronounMatch";

  /** Use animacy list (Bergsma and Lin, 2006; Ji and Lin, 2009) */
  public static final boolean USE_ANIMACY_LIST = true;

  /** Share attributes between coreferent mentions **/
  public static final boolean SHARE_ATTRIBUTES = true;

  /** Whether or not the RuleBasedCorefMentionFinder can reparse a phrase to find its head */
  public static final boolean ALLOW_REPARSING = true;

  /** Default language */
  public static final Locale LANGUAGE_DEFAULT = Locale.ENGLISH;
  
  public static final String LANGUAGE_PROP = "coref.language";
  public static final String STATES_PROP = "dcoref.states";
  public static final String DEMONYM_PROP = "dcoref.demonym";
  public static final String ANIMATE_PROP = "dcoref.animate";
  public static final String INANIMATE_PROP = "dcoref.inanimate";
  public static final String MALE_PROP = "dcoref.male";
  public static final String NEUTRAL_PROP = "dcoref.neutral";
  public static final String FEMALE_PROP = "dcoref.female";
  public static final String PLURAL_PROP = "dcoref.plural";
  public static final String SINGULAR_PROP = "dcoref.singular";
  public static final String SIEVES_PROP = "dcoref.sievePasses";
  public static final String MENTION_FINDER_PROP = "dcoref.mentionFinder";
  public static final String MENTION_FINDER_PROPFILE_PROP = "dcoref.mentionFinder.props";
  public static final String SCORE_PROP = "dcoref.score";
  public static final String LOG_PROP = "dcoref.logFile";
  public static final String ACE2004_PROP = "dcoref.ace2004";
  public static final String ACE2005_PROP = "dcoref.ace2005";
  public static final String MUC_PROP = "dcoref.muc";
  public static final String CONLL2011_PROP = "dcoref.conll2011";
  public static final String CONLL_OUTPUT_PROP = "dcoref.conll.output";
  public static final String CONLL_SCORER = "dcoref.conll.scorer";
  public static final String PARSER_MODEL_PROP = "parse.model";
  public static final String PARSER_MAXLEN_PROP = "parse.maxlen";
  public static final String POSTPROCESSING_PROP = "dcoref.postprocessing";
  public static final String MAXDIST_PROP = "dcoref.maxdist";
  public static final String REPLICATECONLL_PROP = "dcoref.replicate.conll";
  public static final String GENDER_NUMBER_PROP = "dcoref.big.gender.number";
  public static final String COUNTRIES_PROP = "dcoref.countries";
  public static final String STATES_PROVINCES_PROP = "dcoref.states.provinces";
  public static final String OPTIMIZE_SIEVES_PROP = "dcoref.optimize.sieves";
  public static final String OPTIMIZE_SIEVES_KEEP_ORDER_PROP = "dcoref.optimize.sieves.keepOrder";
  public static final String OPTIMIZE_SIEVES_SCORE_PROP = "dcoref.optimize.sieves.score";
  public static final String RUN_DIST_CMD_PROP = "dcoref.dist.cmd";
  public static final String RUN_DIST_CMD_WORK_DIR = "dcoref.dist.workdir";
  public static final String SCORE_FILE_PROP = "dcoref.score.output";
  public static final String SINGLETON_PROP = "dcoref.singleton.predictor";
  public static final String SINGLETON_MODEL_PROP = "dcoref.singleton.model";
  public static final String DICT_LIST_PROP = "dcoref.dictlist";
  public static final String DICT_PMI_PROP = "dcoref.dictpmi";
  public static final String SIGNATURES_PROP = "dcoref.signatures";

  public static final String ALLOW_REPARSING_PROP = "dcoref.allowReparsing";

  public static final int MONITOR_DIST_CMD_FINISHED_WAIT_MILLIS = 60000;



  //
  // note that default paths for all dictionaries used are in
  // pipeline.DefaultPaths
  //

  /** print the values of variables in this class */
  public static void printConstants(Logger logger) {
    if (Constants.USE_ANIMACY_LIST) logger.info("USE_ANIMACY_LIST on");
    else logger.info("USE_ANIMACY_LIST off");
    if (Constants.USE_ANIMACY_LIST) logger.info("USE_ANIMACY_LIST on");
    else logger.info("USE_ANIMACY_LIST off");
    if (Constants.USE_DISCOURSE_SALIENCE) logger.info("use discourse salience");
    else logger.info("not use discourse salience");
    if (Constants.USE_TRUECASE) logger.info("use truecase annotator");
    else logger.info("not use truecase annotator");
    if (Constants.USE_DISCOURSE_CONSTRAINTS) logger.info("USE_DISCOURSE_CONSTRAINTS on");
    else logger.info("USE_DISCOURSE_CONSTRAINTS off");
    if (Constants.USE_GOLD_POS) logger.info("USE_GOLD_POS on");
    else logger.info("USE_GOLD_POS off");
    if (Constants.USE_GOLD_NE) logger.info("use gold NE type annotation");
    else logger.info("use Stanford NER");
    if (Constants.USE_GOLD_PARSES) logger.info("USE_GOLD_PARSES on");
    else logger.info("USE_GOLD_PARSES off");
    if (Constants.USE_GOLD_SPEAKER_TAGS) logger.info("USE_GOLD_SPEAKER_TAGS on");
    else logger.info("USE_GOLD_SPEAKER_TAGS off");
    if (Constants.USE_GOLD_MENTIONS) logger.info("USE_GOLD_MENTIONS on");
    else logger.info("USE_GOLD_MENTIONS off");
    if (Constants.USE_GOLD_MENTION_BOUNDARIES) logger.info("USE_GOLD_MENTION_BOUNDARIES on");
    else logger.info("USE_GOLD_MENTION_BOUNDARIES off");
    if (Constants.USE_CONLL_AUTO) logger.info("use conll auto set -> if GOLD_NE, GOLD_PARSE, GOLD_POS, etc turned on, use auto");
    else logger.info("use conll gold set -> if GOLD_NE, GOLD_PARSE, GOLD_POS, etc turned on, use gold");
    if (Constants.REMOVE_SINGLETONS) logger.info("REMOVE_SINGLETONS on");
    else logger.info("REMOVE_SINGLETONS off");
    if (Constants.REMOVE_APPOSITION_PREDICATENOMINATIVES) logger.info("REMOVE_APPOSITION_PREDICATENOMINATIVES on");
    else logger.info("REMOVE_APPOSITION_PREDICATENOMINATIVES off");
    logger.info("=================================================================");
  }

}
