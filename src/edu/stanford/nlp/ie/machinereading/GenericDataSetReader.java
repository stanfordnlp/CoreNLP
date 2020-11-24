package edu.stanford.nlp.ie.machinereading;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.ie.machinereading.common.NoPunctuationHeadFinder;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.parser.common.ParserAnnotations;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
// import edu.stanford.nlp.util.logging.Redwood;

/**
 *
 * @author Andrey Gusev
 * @author Mihai
 *
 */
public class GenericDataSetReader  {

  /** A logger for this class */
  // private static Redwood.RedwoodChannels log = Redwood.channels(GenericDataSetReader.class);
  protected Logger logger;

  /** Finds the syntactic head of a syntactic constituent */
  protected final HeadFinder headFinder = new NoPunctuationHeadFinder();

  /** NL processor to use for sentence pre-processing */
  protected StanfordCoreNLP processor;

  /**
   * Additional NL processor that implements only syntactic parsing (needed for head detection)
   * We need this processor to detect heads of predicted entities that cannot be matched to an existing constituent.
   * This is created on demand, only when necessary
   */
  protected Annotator parserProcessor;

  /** If true, we perform syntactic analysis of the dataset sentences and annotations */
  protected final boolean preProcessSentences;

  /**
   * If true, sets the head span to match the syntactic head of the extent.
   * Otherwise, the head span is not modified.
   * This is enabled for the NFL domain, where head spans are not given.
   */
  protected final boolean calculateHeadSpan;

  /** If true, it regenerates the index spans for all tree nodes (useful for KBP) */
  protected final boolean forceGenerationOfIndexSpans;

  /** Only around for legacy results */
  protected boolean useNewHeadFinder = true;

  public GenericDataSetReader() {
    this(null, false, false, false);
  }

  public GenericDataSetReader(StanfordCoreNLP processor, boolean preProcessSentences, boolean calculateHeadSpan, boolean forceGenerationOfIndexSpans) {
    this.logger = Logger.getLogger(GenericDataSetReader.class.getName());
    this.logger.setLevel(Level.SEVERE);

    if(processor != null) setProcessor(processor);
    parserProcessor = null;
    /* old parser options
    parser.setOptionFlags(new String[] {
        "-outputFormat", "penn,typedDependenciesCollapsed",
        "-maxLength", "100",
        "-retainTmpSubcategories"
    });
    */

    this.preProcessSentences = preProcessSentences;
    this.calculateHeadSpan = calculateHeadSpan;
    this.forceGenerationOfIndexSpans = forceGenerationOfIndexSpans;
  }

  public void setProcessor(StanfordCoreNLP p) {
    this.processor = p;
  }

  public void setUseNewHeadFinder(boolean useNewHeadFinder) {
    this.useNewHeadFinder = useNewHeadFinder;
  }

  // TODO(luffa): this should get the parser passed in somehow when
  // creating the Reader, or maybe we need to cache the parser the
  // same way the dependency parser is now cached
  public Annotator getParser() {
    if(parserProcessor == null){
      parserProcessor = StanfordCoreNLP.getExistingAnnotator("parse");
      assert(parserProcessor != null);
    }
    return parserProcessor;
  }

  public void setLoggerLevel(Level level) {
    logger.setLevel(level);
  }
  public Level getLoggerLevel() {
    return logger.getLevel();
  }

  /**
   * Parses one file or directory with data from one domain
   * @param path
   * @throws IOException
   */
  public final Annotation parse(String path) throws IOException {
    Annotation retVal; // set below or exceptions

    try {
      //
      // this must return a dataset Annotation. each sentence in this dataset must contain:
      // - TokensAnnotation
      // - EntityMentionAnnotation
      // - RelationMentionAnnotation
      // - EventMentionAnnotation
      // the other annotations (parse, NER) are generated in preProcessSentences
      //
      retVal = this.read(path);
    } catch (Exception ex) {
      IOException iox = new IOException(ex);
      throw iox;
    }

    if (preProcessSentences) {
      preProcessSentences(retVal);
      if(MachineReadingProperties.trainUsePipelineNER){
        logger.severe("Changing NER tags using the CoreNLP pipeline.");
        modifyUsingCoreNLPNER(retVal);
        }
    }
    return retVal;
  }

  private static void modifyUsingCoreNLPNER(Annotation doc) {
    Properties ann = new Properties();
    ann.setProperty("annotators", "pos, lemma, ner");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(ann, false);
    pipeline.annotate(doc);
    for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<EntityMention> entities = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
      if (entities != null) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        for (EntityMention en : entities) {
          //System.out.println("old ner tag for " + en.getExtentString() + " was " + en.getType());
          Span s = en.getExtent();
          Counter<String> allNertagforSpan = new ClassicCounter<>();
          for (int i = s.start(); i < s.end(); i++) {
            allNertagforSpan.incrementCount(tokens.get(i).ner());
          }
          String entityNertag = Counters.argmax(allNertagforSpan);
          en.setType(entityNertag);
          //System.out.println("new ner tag is " + entityNertag);
        }
      }

    }
  }

  public Annotation read(String path) throws Exception {
    return null;
  }

  private static String sentenceToString(List<CoreLabel> tokens) {
    StringBuilder os = new StringBuilder();

    //
    // Print text and tokens
    //
    if(tokens != null){
      boolean first = true;
      for(CoreLabel token: tokens) {
        if(! first) os.append(" ");
        os.append(token.word());
        first = false;
      }
    }

    return os.toString();
  }


  /**
   * Find the index of the head of an entity.
   *
   * @param ent The entity mention
   * @param tree The Tree for the entire sentence in which it occurs.
   * @param tokens The Sentence in which it occurs
   * @param setHeadSpan Whether to set the head span in the entity mention.
   * @return The index of the entity head
   */
  public int assignSyntacticHead(EntityMention ent, Tree tree, List<CoreLabel> tokens, boolean setHeadSpan) {
    if (ent.getSyntacticHeadTokenPosition() != -1) {
      return ent.getSyntacticHeadTokenPosition();
    }

    logger.finest("Finding syntactic head for entity: " + ent + " in tree: " + tree.toString());
    logger.finest("Flat sentence is: " + tokens);
    Tree sh = null;
    try {
      sh = findSyntacticHead(ent, tree, tokens);
    } catch (Exception | AssertionError e) {
      logger.severe("WARNING: failed to parse sentence. Will continue with the right-most head heuristic: " + sentenceToString(tokens));
      e.printStackTrace();
    }

    int headPos = ent.getExtentTokenEnd() - 1;
    if(sh != null){
      CoreLabel label = (CoreLabel) sh.label();
      headPos = label.get(CoreAnnotations.BeginIndexAnnotation.class);
    } else {
      logger.fine("WARNING: failed to find syntactic head for entity: " + ent + " in tree: " + tree);
      logger.fine("Fallback strategy: will set head to last token in mention: " + tokens.get(headPos));
    }
    ent.setHeadTokenPosition(headPos);

    if (setHeadSpan){
      // set the head span to match exactly the syntactic head
      // this is needed for some corpora where the head span is not given
      ent.setHeadTokenSpan(new Span(headPos, headPos + 1));
    }

    return headPos;
  }

  /**
   * Take a dataset Annotation, generate their parse trees and identify syntactic heads (and head spans, if necessary)
   */
  public void preProcessSentences(Annotation dataset) {
    logger.severe("GenericDataSetReader: Started pre-processing the corpus...");
    // run the processor, i.e., NER, parse etc.
    if (processor != null) {
      // we might already have syntactic annotation from offline files
      List<CoreMap> sentences = dataset.get(CoreAnnotations.SentencesAnnotation.class);
      if (sentences.size() > 0 && !sentences.get(0).containsKey(TreeCoreAnnotations.TreeAnnotation.class)) {
        logger.info("Annotating dataset with " + processor);
        processor.annotate(dataset);
      } else {
        logger.info("Found existing syntactic annotations. Will not use the NLP processor.");
      }
    }
    /*
    List<CoreMap> sentences = dataset.get(CoreAnnotations.SentencesAnnotation.class);
    for(int i = 0; i < sentences.size(); i ++){
      CoreMap sent = sentences.get(i);
      List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
      logger.info("Tokens for sentence #" + i + ": " + tokens);
      logger.info("Parse tree for sentence #" + i + ": " + sent.get(TreeCoreAnnotations.TreeAnnotation.class).pennString());
    }
    */

    List<CoreMap> sentences = dataset.get(CoreAnnotations.SentencesAnnotation.class);
    logger.fine("Extracted " + sentences.size() + " sentences.");
    for (CoreMap sentence : sentences) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      logger.fine("Processing sentence " + tokens);
      Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
      if(tree == null) throw new RuntimeException("ERROR: MR requires full syntactic analysis!");

      // convert tree labels to CoreLabel if necessary
      // we need this because we store additional info in the CoreLabel, such as the spans of each tree
      convertToCoreLabels(tree);

      // store the tree spans, if not present already
      CoreLabel l = (CoreLabel) tree.label();
      if(forceGenerationOfIndexSpans || (! l.containsKey(CoreAnnotations.BeginIndexAnnotation.class) && ! l.containsKey(CoreAnnotations.EndIndexAnnotation.class))){
        tree.indexSpans(0);
        logger.fine("Index spans were generated.");
      } else {
        logger.fine("Index spans were NOT generated.");
      }
      logger.fine("Parse tree using CoreLabel:\n" + tree.pennString());

      //
      // now match all entity mentions against the syntactic tree
      //
      if (sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class) != null) {
        for (EntityMention ent : sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class)) {
          logger.fine("Finding head for entity: " + ent);
          int headPos = assignSyntacticHead(ent, tree, tokens, calculateHeadSpan);
          logger.fine("Syntactic head of mention \"" + ent + "\" is: " + tokens.get(headPos).word());

          assert(ent.getExtent() != null);
          assert(ent.getHead() != null);
          assert(ent.getSyntacticHeadTokenPosition() >= 0);
        }
      }
    }
    logger.severe("GenericDataSetReader: Pre-processing complete.");
  }

  /**
   * Converts the tree labels to CoreLabels.
   * We need this because we store additional info in the CoreLabel, like token span.
   * @param tree
   */
  public static void convertToCoreLabels(Tree tree) {
    Label l = tree.label();
    if(! (l instanceof CoreLabel)){
      CoreLabel cl = new CoreLabel();
      cl.setValue(l.value());
      tree.setLabel(cl);
    }

    for (Tree kid : tree.children()) {
      convertToCoreLabels(kid);
    }
  }

  private static String printTree(Tree tree) {
    StringBuilder sb = new StringBuilder();
    return tree.toStringBuilder(sb).toString();
  }

  private Tree safeHead(Tree top) {
    Tree head = top.headTerminal(headFinder);
    if (head != null) return head;
    // if no head found return the right-most leaf
    List<Tree> leaves = top.getLeaves();
    if ( ! leaves.isEmpty()) {
      return leaves.get(leaves.size() - 1);
    }
    // fallback: return top
    return top;
  }

  /**
   * Finds the syntactic head of the given entity mention.
   *
   * @param ent The entity mention
   * @param root The Tree for the entire sentence in which it occurs.
   * @param tokens The Sentence in which it occurs
   * @return The tree object corresponding to the head. This MUST be a child of root.
   *     It will be a leaf in the parse tree.
   */
  public Tree findSyntacticHead(EntityMention ent, Tree root, List<CoreLabel> tokens) {
    if (!useNewHeadFinder) {
      return originalFindSyntacticHead(ent, root, tokens);
    }

    logger.fine("Searching for tree matching " + ent);
    Tree exactMatch = findTreeWithSpan(root, ent.getExtentTokenStart(), ent.getExtentTokenEnd());

    //
    // found an exact match
    //
    if (exactMatch != null) {
      logger.fine("Mention \"" + ent + "\" mapped to tree: " + printTree(exactMatch));
      return safeHead(exactMatch);
    }

    // no exact match found
    // in this case, we parse the actual extent of the mention, embedded in a sentence
    // context, so as to make the parser work better :-)

    int approximateness = 0;
    List<CoreLabel> extentTokens = new ArrayList<>();
    // Add tags in case we are using the SRParser, which needs tags to operate
    extentTokens.add(initCoreLabel("It", "PRP"));
    extentTokens.add(initCoreLabel("was", "VBD"));
    final int ADDED_WORDS = 2;
    for (int i = ent.getExtentTokenStart(); i < ent.getExtentTokenEnd(); i++) {
      // Add everything except separated dashes! The separated dashes mess with the parser too badly.
      CoreLabel label = tokens.get(i);
      if ( ! "-".equals(label.word())) {
        extentTokens.add(tokens.get(i));
      } else {
        approximateness++;
      }
    }
    extentTokens.add(initCoreLabel(".", "."));

    // constrain the parse to the part we're interested in.
    // Starting from ADDED_WORDS comes from skipping "It was".
    // -1 to exclude the period.
    // We now let it be any kind of nominal constituent, since there
    // are VP and S ones
    ParserConstraint constraint = new ParserConstraint(ADDED_WORDS, extentTokens.size() - 1, ".*");
    List<ParserConstraint> constraints = Collections.singletonList(constraint);
    Tree tree = parse(extentTokens, constraints);
    logger.fine("No exact match found. Local parse:\n" + tree.pennString());
    convertToCoreLabels(tree);
    tree.indexSpans(ent.getExtentTokenStart() - ADDED_WORDS);  // remember it has ADDED_WORDS extra words at the beginning
    Tree subtree = findPartialSpan(tree, ent.getExtentTokenStart());
    Tree extentHead = safeHead(subtree);
    logger.fine("Head is: " + extentHead);
    assert(extentHead != null);
    // extentHead is a child in the local extent parse tree. we need to find the corresponding node in the main tree
    // Because we deleted dashes, it's index will be >= the index in the extent parse tree
    CoreLabel l = (CoreLabel) extentHead.label();
    // Tree realHead = findTreeWithSpan(root, l.get(CoreAnnotations.BeginIndexAnnotation.class), l.get(CoreAnnotations.EndIndexAnnotation.class));
    Tree realHead = funkyFindLeafWithApproximateSpan(root, l.value(), l.get(CoreAnnotations.BeginIndexAnnotation.class), approximateness);
    if(realHead != null) logger.fine("Chosen head: " + realHead);
    return realHead;
  }

  private Tree findPartialSpan(Tree current, int start) {
    CoreLabel label = (CoreLabel) current.label();
    int startIndex = label.get(CoreAnnotations.BeginIndexAnnotation.class);
    if (startIndex == start) {
      logger.fine("findPartialSpan: Returning " + current);
      return current;
    }
    for (Tree kid : current.children()) {
      CoreLabel kidLabel = (CoreLabel) kid.label();
      int kidStart = kidLabel.get(CoreAnnotations.BeginIndexAnnotation.class);
      int kidEnd = kidLabel.get(CoreAnnotations.EndIndexAnnotation.class);
      // log.info("findPartialSpan: Examining " + kidLabel.value() + " from " + kidStart + " to " + kidEnd);
      if (kidStart <= start && kidEnd > start) {
        return findPartialSpan(kid, start);
      }
    }
    throw new RuntimeException("Shouldn't happen: " + start + " " + current);
  }

  private Tree funkyFindLeafWithApproximateSpan(Tree root, String token, int index, int approximateness) {
    logger.fine("Looking for " + token + " at pos " + index + " plus upto " + approximateness + " in tree: " + root.pennString());
    List<Tree> leaves = root.getLeaves();
    for (Tree leaf : leaves) {
      CoreLabel label = CoreLabel.class.cast(leaf.label());
      int ind = label.get(CoreAnnotations.BeginIndexAnnotation.class);
      // log.info("Token #" + ind + ": " + leaf.value());
      if (token.equals(leaf.value()) && ind >= index && ind <= index + approximateness) {
        return leaf;
      }
    }
    // this shouldn't happen
    // but it does happen (VERY RARELY) on some weird web text that includes SGML tags with spaces
    // TODO: does this mean that somehow tokenization is different for the parser? check this by throwing an Exception in KBP
    logger.severe("GenericDataSetReader: WARNING: Failed to find head token");
    logger.severe("  when looking for " + token + " at pos " + index + " plus upto " + approximateness + " in tree: " + root.pennString());
    return null;
  }

  /**
   * This is the original version of {@link #findSyntacticHead} before Chris's modifications.
   * There's no good reason to use it except for producing historical results.
   * It Finds the syntactic head of the given entity mention.
   *
   * @param ent The entity mention
   * @param root The Tree for the entire sentence in which it occurs.
   * @param tokens The Sentence in which it occurs
   * @return The tree object corresponding to the head. This MUST be a child of root.
   *     It will be a leaf in the parse tree.
   */
  public Tree originalFindSyntacticHead(EntityMention ent, Tree root, List<CoreLabel> tokens) {
    logger.fine("Searching for tree matching " + ent);
    Tree exactMatch = findTreeWithSpan(root, ent.getExtentTokenStart(), ent.getExtentTokenEnd());

    //
    // found an exact match
    //
    if (exactMatch != null) {
      logger.fine("Mention \"" + ent + "\" mapped to tree: " + printTree(exactMatch));
      return safeHead(exactMatch);
    }

    //
    // no exact match found
    // in this case, we parse the actual extent of the mention
    //
    List<CoreLabel> extentTokens = new ArrayList<>();
    for (int i = ent.getExtentTokenStart(); i < ent.getExtentTokenEnd(); i++)
      extentTokens.add(tokens.get(i));

    Tree tree = parse(extentTokens);
    logger.fine("No exact match found. Local parse:\n" + tree.pennString());
    convertToCoreLabels(tree);
    tree.indexSpans(ent.getExtentTokenStart());
    Tree extentHead = safeHead(tree);
    assert (extentHead != null);
    // extentHead is a child in the local extent parse tree. we need to find the
    // corresponding node in the main tree
    CoreLabel l = (CoreLabel) extentHead.label();
    Tree realHead = findTreeWithSpan(root, l.get(CoreAnnotations.BeginIndexAnnotation.class), l.get(CoreAnnotations.EndIndexAnnotation.class));
    assert (realHead != null);

    return realHead;
  }

  /**
   * Makes a fake corelabel with just the given token for annotations
   */
  private static CoreLabel initCoreLabel(String token) {
    CoreLabel label = new CoreLabel();
    label.setWord(token);
    label.setValue(token);
    label.set(CoreAnnotations.TextAnnotation.class, token);
    label.set(CoreAnnotations.ValueAnnotation.class, token);

    return label;
  }

  /**
   * Makes a fake corelabel with the given token and tag for annotations
   */
  private static CoreLabel initCoreLabel(String token, String tag) {
    CoreLabel label = initCoreLabel(token);
    label.set(CoreAnnotations.PartOfSpeechAnnotation.class, tag);
    return label;
  }

  protected Tree parseStrings(List<String> tokens) {
    List<CoreLabel> labels = new ArrayList<>();
    for (String t : tokens) {
      CoreLabel l = initCoreLabel(t);
      labels.add(l);
    }
    return parse(labels);
  }

  protected Tree parse(List<CoreLabel> tokens) {
    return parse(tokens, null);
  }

  protected Tree parse(List<CoreLabel> tokens,
                       List<ParserConstraint> constraints) {
    CoreMap sent = new Annotation("");
    sent.set(CoreAnnotations.TokensAnnotation.class, tokens);
    sent.set(ParserAnnotations.ConstraintAnnotation.class, constraints);
    Annotation doc = new Annotation("");
    List<CoreMap> sents = new ArrayList<>();
    sents.add(sent);
    doc.set(CoreAnnotations.SentencesAnnotation.class, sents);
    getParser().annotate(doc);
    sents = doc.get(CoreAnnotations.SentencesAnnotation.class);
    return sents.get(0).get(TreeCoreAnnotations.TreeAnnotation.class);
  }

  /**
   * Finds the tree with the given token span.
   * The tree must have CoreLabel labels and Tree.indexSpans must be called before this method.
   *
   * @param tree The tree to search in
   * @param start The beginning index
   * @param end
   * @return A child of tree if match; otherwise null
   */
  private static Tree findTreeWithSpan(Tree tree, int start, int end) {
    CoreLabel l = (CoreLabel) tree.label();
    if (l != null && l.containsKey(CoreAnnotations.BeginIndexAnnotation.class) && l.containsKey(CoreAnnotations.EndIndexAnnotation.class)) {
      int myStart = l.get(CoreAnnotations.BeginIndexAnnotation.class);
      int myEnd = l.get(CoreAnnotations.EndIndexAnnotation.class);
      if (start == myStart && end == myEnd){
        // found perfect match
        return tree;
      } else if (end < myStart) {
        return null;
      } else if (start >= myEnd) {
        return null;
      }
    }

    // otherwise, check inside children - a match is possible
    for (Tree kid : tree.children()) {
      if (kid == null) continue;
      Tree ret = findTreeWithSpan(kid, start, end);
      // found matching child
      if (ret != null) return ret;
    }

    // no match
    return null;
  }

}
