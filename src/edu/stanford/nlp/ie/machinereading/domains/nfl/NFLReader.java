package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.io.File;
import java.io.FileFilter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.stanford.nlp.ie.machinereading.structure.EntityMentionFactory;
import edu.stanford.nlp.ie.machinereading.GenericDataSetReader;
import edu.stanford.nlp.ie.machinereading.structure.RelationMentionFactory;
import edu.stanford.nlp.ie.machinereading.common.DomReader;
import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.StringUtils;

/**
 * Reads and parse entity and relation mentions from XML documents with NFL annotations
 * 
 * Example XML:
 * <relations docid="AFP_ENG_19950107.0244"> <relation type="teamInGame"
 * start="186" end="387"> By winning the National Football League (NFL) playoff
 * game, the 49ers will host the winner of Sunday's Dallas-Green Bay game on
 * January 15 to decide a berth in the January 29 championship game at Miami.
 * <arg type="NFLGame" start="280" end="309">Sunday's Dallas-Green Bay
 * game</arg> <arg type="NFLTeam" start="289" end="294">Dallas</arg> </relation>
 * .... </relations>
 * 
 * @author Andrey Gusev
 * @author Mason Smith
 * @author Mihai
 * @author David McClosky
 */
public class NFLReader extends GenericDataSetReader {
  // XML node tags
  private static final String RELATIONS_TOP = "relations";
  private static final String RELATION = "relation";
  private static final String ARG = "arg";

  // XML attributes
  private static final String DOC_ID_ATTRIBUTE = "docid";
  private static final String TYPE = "type";
  private static final String START = "start";
  private static final String END = "end";
  
  private static HashMap<String, String> NORMALIZE_TYPES;
  
  private final EntityMentionFactory entityMentionFactory;
  private final RelationMentionFactory relationMentionFactory;
  
  static {
  	NORMALIZE_TYPES = new HashMap<String, String>();
  	NORMALIZE_TYPES.put("teamFinalScore", "FinalScore");
  }
  
  public NFLReader() {
    this(true, true, true);
  }
  
  public NFLReader(Properties props) {
    this();
  }
  
  public NFLReader(boolean preProcessSentences, boolean calculateHeadSpan, boolean forceGenerationOfIndexSpans) {
    super(null, preProcessSentences, calculateHeadSpan, forceGenerationOfIndexSpans);
    // change the logger to one from our namespace
    logger = Logger.getLogger(NFLReader.class.getName());
    // run quietly by default
    logger.setLevel(Level.SEVERE);
    entityMentionFactory = new NFLEntityMentionFactory();
    relationMentionFactory = new NFLRelationMentionFactory();
  }

	@Override
	public Annotation read(String path) throws Exception {
		logger.info("Corpus location: \"" + path + "\"");
		File f = new File(path);
		Annotation corpus = new Annotation("");

		if (f.isDirectory()) {
			File[] files = f.listFiles(new FileFilter() {

				public boolean accept(File file) {
					return file.getName().endsWith(getFileAcceptExtension());
				}
			});
			for (File sf : files) {
				read(sf, corpus);
			}
		} else {
			read(f, corpus);
		}

		return corpus;
	}

	protected String getFileAcceptExtension() {
		return ".rel";
	}

  /**
   * Reads the contents of one file
   * @param f The file
   * @param corpus All structures will be stored in this corpus object
   */
  protected void read(File f, Annotation corpus) throws Exception {
    // parse the dom document
    logger.info("Parsing file " + f.getAbsolutePath() + " ...");
    Document document = DomReader.readDocument(f);

    // now extract relations and arg types
    Node relationsElement = document.getElementsByTagName(RELATIONS_TOP).item(0);
    String docId = DomReader.getAttributeValue(relationsElement, DOC_ID_ATTRIBUTE);

    // this stores the final document that we generate here
    NodeList rels = document.getElementsByTagName(RELATION);
    
    // caches previously seen sentences; indexed by start offset
    HashMap<Integer, Annotation> seenSentences = new HashMap<Integer, Annotation>();
    // caches previously seen entity mentions; indexed by extent span
    HashMap<Integer, EntityMention> seenMentions = new HashMap<Integer, EntityMention>();
    // counts the total number of tokens in this document
    int tokenOffset = 0;

    for (int relNum = 0; relNum < rels.getLength(); relNum++) {
      Node relationNode = rels.item(relNum);
      // the relation type
      String relType = getType(relationNode);
      if (relType == null) {
        logger.warning("Relation type is null -- this is likely because it couldn't be inferred from the .gui.xml files, skipping relation.");
        continue;
      }

      // fetch the parent sentence
      Annotation relSent = null;
      int sentenceOffset;
      try {
        sentenceOffset = Integer.parseInt(DomReader.getAttributeValue(relationNode, START));
      } catch (NumberFormatException nfe) {
        logger.severe("Sentence has null start offset, skipping.");
        continue;
      }
      
      logger.info("Sentence offset = " + sentenceOffset);
      if(seenSentences.containsKey(sentenceOffset)){
        relSent = seenSentences.get(sentenceOffset);
      } else {      
        String textContent = relationNode.getFirstChild().getTextContent().trim();
        logger.fine("Creating sentence for offset " + sentenceOffset + ": " + textContent);
        NFLTokenizer tokenizer = new NFLTokenizer(textContent);
        List<CoreLabel> tokens = tokenizer.tokenize();
        AnnotationUtils.updateOffsetsInCoreLabels(tokens, sentenceOffset);
        relSent = new Annotation(textContent);
        logger.info("Tokenized sentence: " + AnnotationUtils.tokensToString(tokens));
        relSent.set(CoreAnnotations.DocIDAnnotation.class, docId);
        relSent.set(CoreAnnotations.TokensAnnotation.class, tokens);
        // set character and token offsets for each sentence! 
        // these are needed for SUTime 
        if(tokens.size() > 0){
          relSent.set(CharacterOffsetBeginAnnotation.class, 
              tokens.get(0).get(CharacterOffsetBeginAnnotation.class));
          relSent.set(CharacterOffsetEndAnnotation.class,
              tokens.get(tokens.size() - 1).get(CharacterOffsetEndAnnotation.class));
        }
        relSent.set(TokenBeginAnnotation.class, tokenOffset);
        relSent.set(TokenEndAnnotation.class, tokenOffset + tokens.size());
        tokenOffset += tokens.size();
        seenSentences.put(sentenceOffset, relSent);
        AnnotationUtils.addSentence(corpus, relSent);
      }
      assert(relSent != null);
      
      //
      // extract the arguments of the current relation
      //
      Span relExtent = new Span(Integer.MAX_VALUE, Integer.MIN_VALUE);
      NodeList args = relationNode.getChildNodes();
      List<ExtractionObject> argList = new ArrayList<ExtractionObject>();
      boolean hasMissingArguments = false;
      for (int argNum = 0; argNum < args.getLength(); argNum++) {
        Node argNode = args.item(argNum);
        // only look at arg nodes
        if (!ARG.equals(argNode.getNodeName())) {
          continue;
        }

        // create one argument here
        String argType = getType(argNode);
        String value = argNode.getTextContent().trim();
        int argStart = Integer.parseInt(DomReader.getAttributeValue(argNode, START));
        int argEnd = Integer.parseInt(DomReader.getAttributeValue(argNode, END));
        
        if (argStart == -1 && argEnd == -1 && value.equals("*MISSING*")) {
          hasMissingArguments = true;
          continue;
        }
        
        logger.fine("Found argument " + argType + "[" + argStart + ", " + argEnd + "]: " + value);
        
        int seenKey = (10000000 * argStart + argEnd);
        EntityMention arg = seenMentions.get(seenKey);
        Span extent = null;
        if(arg == null) {
          // map the argument to sentence tokens
          extent = mapArgumentToSentence(value, argStart, argEnd, relSent.get(CoreAnnotations.TokensAnnotation.class));
          if(extent == null){
            logger.info("Failed to map argument " + argType + "[" + argStart + ", " + argEnd + "]: \"" + value + "\" to sentence: " + relSent);
            logger.fine("The tokens in the above sentence are:");
            for(CoreLabel t: relSent.get(TokensAnnotation.class)){
              logger.fine("\t" + t.word() + "\t" + t.beginPosition() + "\t" + t.endPosition());
            }
            hasMissingArguments = true;
            continue;
          }

          // using value for identifier
          arg = entityMentionFactory.constructEntityMention(
              EntityMention.makeUniqueId(),
              relSent,
              extent,
              extent, // head span is the same as extent span in NFL
              normalizeType(argType),
              null,
              null);
          
          logger.info("Corresponding EntityMention: " + arg);
          seenMentions.put(seenKey, arg);
          AnnotationUtils.addEntityMention(relSent, arg);
        } else {
          extent = arg.getExtent();
        }
        assert(extent != null);
        assert(arg != null);
        
        // update the relation extent
        relExtent.expandToInclude(extent);
        
        //
        // In the NFL domain, relation arguments are sorted ALPHABETICALLY based on their type
        // This provides a non-ambiguous ordering of arguments that does not require the storage of actual argument names
        //
        int i = 0;
        while (i < argList.size()) {
          if (argList.get(i).getType().compareTo(arg.getType()) > 0) {
            // insert and shift
            argList.add(i, arg);
            break;
          }
          i++;
        }
        if (i == argList.size()) {
          argList.add(i, arg);
        }
        
      } // finished scanning all args of this relation

      if (!hasMissingArguments) {
        // using relType for identifier
        RelationMention rel = relationMentionFactory.constructRelationMention(
            RelationMention.makeUniqueId(),
            relSent,
            relExtent,
            relType,
            null,
            argList, 
            null);

        // store the relation in the sentence
        AnnotationUtils.addRelationMention(relSent, rel);
      }
    } // finished scanning all the relations in this file
  }
  
  private static String normalizeType(String type) {
    String val = NORMALIZE_TYPES.get(type);
    if(val != null) return val;
    return type;
  }
  
  private String getType(Node node) {
    return DomReader.getAttributeValue(node, TYPE);
  }

  /**
   * Finds the token span that matches the given text and character offsets
   * @param text The text to match
   * @param start The start character offset
   * @param end The end character offset (this is inclusive! but the token end offsets are exclusive) 
   * @param tokens The tokens in the given sentence
   * @return A span containing the positions of the start and end tokens if matched; null otherwise
   */
  protected Span mapArgumentToSentence(String text, int start, int end, List<CoreLabel> tokens) {
    int tokenStart = -1;
    int tokenEnd = -1;
    logger.fine("Mapping argument " + text + " [" + start + ", " + end + ")");
    for(int i = 0; i < tokens.size(); i ++){
      CoreLabel l = tokens.get(i);
      CoreLabel next = (i == tokens.size() - 1 ? null : tokens.get(i + 1));
      
      // exact match of start
      if(l.beginPosition() == start){
        tokenStart = i;
        logger.fine("Found token start: " + l.word());
      } 
      // approximate match of start
      else if(l.beginPosition() < start && l.endPosition() - 1 >= start){ 
        tokenStart = i;
        logger.fine("Found token start: " + l.word());
        logger.info("WARNING: approximate START match of character span {" + start + ", " + end + "} to tokens: " + StringUtils.join(tokens, " "));
      }
      
      // exact match of end
      if(l.endPosition() - 1 == end){
        tokenEnd = i + 1;
        logger.fine("Found token end: " + l.word());
        break;
      }
      // approximate match of end
      else if(l.endPosition() > end && l.beginPosition() < end){
        tokenEnd = i + 1;
        logger.fine("Found token end: " + l.word());
        logger.info("WARNING: approximate END match of character span {" + start + ", " + end + "} to tokens: " + StringUtils.join(tokens, " "));
        break;
      }
      // approximate match of end where the argument includes the spacing character after token end
      else if(l.endPosition() == end && tokenStart != -1 && (next == null || next.beginPosition() > end)){
        tokenEnd = i + 1;
        logger.fine("Found token end: " + l.word());
        logger.info("WARNING: approximate END match of character span {" + start + ", " + end + "} to tokens: " + StringUtils.join(tokens, " "));
        break;
      }
    }
    if(tokenStart != -1 && tokenEnd != -1){
      return new Span(tokenStart, tokenEnd);
    }
    return null;
  }
  
  /**
   * Implements NFL-specific tokenization rules while maintaining the original character offsets
   * Note: it is crucial to maintain the character offsets of the original tokens because they are used for aligning the annotations against text
   * @author Mihai
   *
   */
  public static class NFLTokenizer {
    AbstractTokenizer<CoreLabel> tokenizer;
    CoreLabelTokenFactory tokenFactory = new CoreLabelTokenFactory();

    private static final Pattern DASH_PATTERN = Pattern.compile("\\d+\\s*(-)\\s*\\d+");
    private static final Pattern LETTERS_DIGITS_PATTERN = Pattern.compile("[a-zA-Z]+(\\d+)");
    private static final Pattern DIGITS_DOTS_PATTERN = Pattern.compile("\\d+(\\.+)");
    private static final Pattern ANYDASH_PATTERN = Pattern.compile("\\w+\\s*(-)\\s*\\w+");
    
    /** Do not break words at dashes if the prefix is in this set */
    private static final HashSet<String> VALID_PREFIXES = new HashSet<String>(Arrays.asList(new String[]{ "semi", "quarter" }));
    private static final Pattern PAREN_PATTERN = Pattern.compile("-(LRB|RRB|LSB|RSB|LCB|RCB)-", Pattern.CASE_INSENSITIVE);
    
    public NFLTokenizer(String buffer) {
      StringReader sr = new StringReader(buffer);
      String options = "ptb3Escaping=false";
      tokenizer = new PTBTokenizer<CoreLabel>(sr, tokenFactory, options);
    }
    
    public NFLTokenizer() {
      tokenizer = null;
    }
    
    public List<CoreLabel> tokenize() {
      List<CoreLabel> tokens = tokenizer.tokenize();
      return postprocess(tokens);
    }

    public List<CoreLabel> postprocess(List<CoreLabel> tokens) {
      tokens = breakScores(tokens);
      tokens = breakLettersDigits(tokens);
      tokens = breakDigitsDot(tokens);
      tokens = breakDashes(tokens);
      return tokens;
    }

    /**
     * Separate tokens that look like "digits-digits". These are NFL scores.
     * @param tokens
     * @return
     */
    private List<CoreLabel> breakScores(List<CoreLabel> tokens) {
      List<CoreLabel> output = new ArrayList<CoreLabel>();
      for(int i = 0; i < tokens.size(); i ++){
        CoreLabel t = tokens.get(i);
        Matcher m = DASH_PATTERN.matcher(t.word());
        if(m.matches()){ // do not use find() here. this may match inside phone numbers with find()
          int dashPos = m.start(1);
          String s1 = t.word().substring(0, dashPos);
          output.add(tokenFactory.makeToken(s1, t.beginPosition(), dashPos));
          String s2 = "to";
          output.add(tokenFactory.makeToken(s2, "-", t.beginPosition() + dashPos, 1));
          String s3 = t.word().substring(dashPos + 1);
          output.add(tokenFactory.makeToken(s3, t.beginPosition() + dashPos + 1, t.endPosition() - t.beginPosition() - dashPos - 1));
        } else {
          output.add(t);
        }
      }
      return output;
    } 
      
    /**
     * Separate tokens that look like "lettersdigits". These are incorrect merges of scores to words.
     */
    private List<CoreLabel> breakLettersDigits(List<CoreLabel> tokens) {
      List<CoreLabel> output = new ArrayList<CoreLabel>();
      for(int i = 0; i < tokens.size(); i ++){
        CoreLabel t = tokens.get(i);
        Matcher m = LETTERS_DIGITS_PATTERN.matcher(t.word());
        if(m.matches()){ // prefer to use matches() instead of find(). it is stricter
          int digitPos = m.start(1);
          String s1 = t.word().substring(0, digitPos);
          output.add(tokenFactory.makeToken(s1, t.beginPosition(), digitPos));
          String s2 = t.word().substring(digitPos);
          output.add(tokenFactory.makeToken(s2, t.beginPosition() + digitPos, t.endPosition() - t.beginPosition() - digitPos));
        } else {
          output.add(t);
        }
      }
      return output;
    }
    
    /**
     * Separate tokens that look like "digitsdots". These are incorrect merges of scores to EOS.
     */
    private List<CoreLabel> breakDigitsDot(List<CoreLabel> tokens) {
      List<CoreLabel> output = new ArrayList<CoreLabel>();
      for(int i = 0; i < tokens.size(); i ++){
        CoreLabel t = tokens.get(i);
        Matcher m = DIGITS_DOTS_PATTERN.matcher(t.word());
        if(m.matches()){ // prefer to use matches() instead of find(). it is stricter
          int digitPos = m.start(1);
          String s1 = t.word().substring(0, digitPos);
          output.add(tokenFactory.makeToken(s1, t.beginPosition(), digitPos));
          String s2 = t.word().substring(digitPos);
          output.add(tokenFactory.makeToken(s2, t.beginPosition() + digitPos, t.endPosition() - t.beginPosition() - digitPos));
        } else {
          output.add(t);
        }
      }
      return output;
    }
    
    /**
     * Separate tokens that look like "stuff-stuff". These may include relevant information.
     * Do NOT break valid parentheses replacements: -LRB- -RRB- -LCB- -RCB- -LSB- -RSB-
     * @param tokens
     * @return
     */
    private List<CoreLabel> breakDashes(List<CoreLabel> tokens) {
      List<CoreLabel> output = new ArrayList<CoreLabel>();
      for(int i = 0; i < tokens.size(); i ++){
        CoreLabel t = tokens.get(i);
        Set<Integer> validDashes = new HashSet<Integer>();
        extractValidDashes(validDashes, t.word());
        
        Matcher m = ANYDASH_PATTERN.matcher(t.word());
        if(m.matches()){ // prefer to use matches() instead of find(). it is stricter
          int dashPos = m.start(1);
          String s1 = t.word().substring(0, dashPos);
          if(VALID_PREFIXES.contains(s1)){
            output.add(t);
          } else if(validDashes.contains(dashPos)){ // do not break dashes that are parts of paren replacements 
            output.add(t);
          } else {
            output.add(tokenFactory.makeToken(s1, t.beginPosition(), dashPos));
            String s2 = "-";
            output.add(tokenFactory.makeToken(s2, t.beginPosition() + dashPos, 1));
            String s3 = t.word().substring(dashPos + 1);
            output.add(tokenFactory.makeToken(s3, t.beginPosition() + dashPos + 1, t.endPosition() - t.beginPosition() - dashPos - 1));
          }
        } else {
          output.add(t);
        }
      }
      return output;
    } 
    
    private void extractValidDashes(Set<Integer> validDashes, String text) {
      Matcher m = PAREN_PATTERN.matcher(text);
      while(m.find()){
        int start = m.start();
        int end = m.end();
        //System.err.println("FOUND PAREN: " + start + " " + end);
        for(int i = start; i < end; i ++){
          validDashes.add(i);
        }
      }
    }
  }
  
  public static void main(String[] args) throws Exception {
    // just a simple test, to make sure stuff works
    Properties props = StringUtils.argsToProperties(args);
    NFLReader reader = new NFLReader();
    reader.setProcessor(new StanfordCoreNLP(props));
    reader.setLoggerLevel(Level.INFO);
    Annotation doc = reader.parse("/scr/nlp/data/machine-reading/Machine_Reading_P1_Reading_Task_V2.0/data/SportsDomain/NFLScoring_UseCase/syntax_mapping/");
    //Annotation doc = reader.parse("/Users/Mihai/corpora/machine-reading/Machine_Reading_P1_Reading_Task_V2.0/data/SportsDomain/NFLScoring_UseCase/syntax_mapping/");
    System.out.println(AnnotationUtils.datasetToString(doc));
  }
  
}
