package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.machinereading.BasicEntityExtractor;
import edu.stanford.nlp.ie.machinereading.structure.EntityMentionFactory;
import edu.stanford.nlp.ie.machinereading.GenericDataSetReader;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.BeginIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EndIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

public class NFLMaxRecallEntityExtractor extends BasicEntityExtractor {
	private static final long serialVersionUID = 1L;
	
	/** 
   * Static logger for this entire class
   */
  public static final Logger logger = Logger.getLogger(NFLMaxRecallEntityExtractor.class.getName());
	
	private HashMap<String, String> gazetteer;
	
	private EntityMentionFactory entityMentionFactory;
	
	public NFLMaxRecallEntityExtractor(String gazetteerLocation) {
		super(gazetteerLocation, false, null, false, new NFLEntityMentionFactory());
		gazetteer = NFLGazetteer.loadGazetteer(gazetteerLocation);
		entityMentionFactory = new NFLEntityMentionFactory();
	}
	
	
	@Override
	public void train(Annotation dataset) {
		// nothing to do
	}
	
	@Override
	public void save(String path) throws IOException {
		// nothing to do
	}
	
	@Override
  public void annotate(Annotation doc) {
	  // make sure forceGenerationOfIndexSpans = true for this c'tor
	  // processor can be null. this means we will use the default parser in the pipeline pool
	  GenericDataSetReader dr = new GenericDataSetReader(null, false, false, true); 
	  Annotator parser = dr.getParser();
	  parser.annotate(doc);
	  
    List<CoreMap> sents = doc.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence : sents) {
      extractMaxRecallEntities(sentence);
    }
    
    // identify the syntactic heads of the predicted mentions (needed by other extractors, e.g., relation extraction)
    dr.preProcessSentences(doc);
	}
	
	private static final Pattern SCORE = Pattern.compile("[0-9]+");
	
	static final String NFL_TEAM = "NFLTeam";
	static final String NFL_GAME = "NFLGame";
	static final String NFL_PLAYOFF_GAME = "NFLPlayoffGame";
	static final String FINAL_SCORE = "FinalScore";
	static final String DATE = "Date";
	
	private void extractMaxRecallEntities(CoreMap sentence) {
		List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
		List<EntityMention> mentions = new ArrayList<EntityMention>();
		Tree tree = sentence.get(TreeAnnotation.class);
		if(tree == null){
		  throw new RuntimeException("Syntactic analysis is required for the NFL domain!");
		}
		// convert tree labels to CoreLabel if necessary
    // we need this because we store additional info in the CoreLabel, such as the spans of each tree
		GenericDataSetReader.convertToCoreLabels(tree);
    
    // store the tree spans, if not present already
    CoreLabel l = (CoreLabel) tree.label();
    if(! l.containsKey(BeginIndexAnnotation.class) && ! l.containsKey(EndIndexAnnotation.class)){
      tree.indexSpans(0);
    }
		
		// find all the head words of NP phrases
		Set<Integer> npHeads = new HashSet<Integer>();
		HeadFinder hf = new SemanticHeadFinder();
		extractNpHeads(tree, npHeads, hf);

		// print words and original NE labels
		logger.info("SENTENCE TO TAG:");
		StringBuffer os = new StringBuffer();
		for(CoreLabel word: words){
			os.append(word.word());
			String tag = word.getString(NamedEntityTagAnnotation.class);
			if(tag != null && ! tag.equals("O")) os.append("/" + tag);
			os.append(" ");
		}
		logger.info(os.toString());
		
		for(int start = 0; start < words.size(); start ++){
			String label = null;
			int end = -1;
			for(end = Math.min(start + NFLGazetteer.MAX_MENTION_LENGTH, words.size()); end > start; end --){
				String text = join(words, start, end);
				String gazTag = gazetteer.get(text);
				String nerTag = findUniqueNerTag(words, start, end);
				Matcher m = SCORE.matcher(text);
				if(gazTag != null /* && ! hasVb */ && isNpHead(start, end, npHeads)){
					logger.info("Found entity mention candidate from gazetteer: " + text);
					if(start > 0 && gazTag.equalsIgnoreCase(NFL_GAME) && words.get(start - 1).word().equalsIgnoreCase("playoff")){
						logger.info("\tFound playoff!");
						label = NFL_PLAYOFF_GAME;
					} else {
						label = gazTag;
					}
					break;
				} else if(m.matches() && // must be a digit pattern 
				    "NUMBER".equals(nerTag) && // must be tagged as NUMBER and not something else 
				    validScoreValue(text) && // must be a valid value 
				    ! nflMeasure(words, end)){ // must NOT be followed by a measurement unit
					logger.info("Found score mention candidate: " + text);
					label = FINAL_SCORE;
					break;
				}
			}
			
			// found a candidate for an NFL entity between [start, end)
			if(label != null){
				EntityMention m = entityMentionFactory.constructEntityMention(
				    EntityMention.makeUniqueId(), 
				    sentence,
            new Span(start, end),
            new Span(start, end),
            label, null, null);
				logger.info("Created NFL entity mention: " + m);
				start = end - 1;
				mentions.add(m);
			}
			
			// look for generic dates
			else {
				for(end = start; end < words.size(); end ++){
					String ne = words.get(end).get(NamedEntityTagAnnotation.class);
					if(! ne.equals("DATE")){
						break;
					}
				}
				// found a date!
				if(end > start){
					EntityMention m = entityMentionFactory.constructEntityMention(
							EntityMention.makeUniqueId(),
							sentence,
							new Span(start, end),
							new Span(start, end),
							DATE, null, null);
					logger.info("Created DATE entity mention: " + m);
					start = end - 1;
					mentions.add(m);
				}
			}			
		}
		
    sentence.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, mentions);
	}
	
	private static void extractNpHeads(Tree tree, Set<Integer> heads, HeadFinder hf) {
	  if(tree.label().value().equals("NP")){
	    Tree h = tree.headTerminal(hf);
	    logger.info("Found head: " + h.pennString());
	    CoreLabel l = (CoreLabel) h.label();
	    heads.add(l.get(BeginIndexAnnotation.class));
	  }
	  
	  Tree [] kids = tree.children();
	  for(Tree t: kids){
	    extractNpHeads(t, heads, hf);
	  }
	}
	private static boolean isNpHead(int start, int end, Set<Integer> heads) {
	  for(int i = start; i < end; i ++){
	    if(heads.contains(i)){
	      return true;
	    }
	  }
	  return false;
	}
	
	private static boolean validScoreValue(String text) {
	  try {
	    int val = Integer.parseInt(text);
	    if(val >= 0 && val < 100) return true;
	  } catch(NumberFormatException e) {
	    
	  }
	  return false;
	}
	private static Set<String> NFL_MEASURES = new HashSet<String>(Arrays.asList("yard", "yards"));
	private static boolean nflMeasure(List<CoreLabel> words, int offset) {
	  // skip dashes
	  if(offset < words.size() && words.get(offset).word().equals("-")) offset ++;
	  if(offset < words.size() && NFL_MEASURES.contains(words.get(offset).word().toLowerCase())) return true;
	  return false;
	}
	
	@SuppressWarnings("unused")
  private static boolean containsTag(List<CoreLabel> words, int start, int end, String tag) {
		for(int i = start; i < end; i ++){
			String pos = words.get(i).getString(PartOfSpeechAnnotation.class);
			if(pos.startsWith(tag)){
				return true;
			}
		}
		return false;
	}
	
  private String findUniqueNerTag(List<CoreLabel> words, int start, int end) {
		String tag = null;
		
		for(int i = start; i < end; i ++){
			String ne = words.get(i).get(NamedEntityTagAnnotation.class);
			if(tag == null) {
				tag = ne;
			} else if(! tag.equals(ne)){
				return null;
			}
		}
		
		return tag;
	}
	
	private String join(List<CoreLabel> words, int start, int end) {
		StringBuffer os = new StringBuffer();
		for(int i = start; i < end; i ++){
			if(i > start) os.append(" ");
			os.append(words.get(i).word().toLowerCase());
		}
		return os.toString();
	}
	
	@Override
	public void setLoggerLevel(Level level) {
    logger.setLevel(level);
  }
}
