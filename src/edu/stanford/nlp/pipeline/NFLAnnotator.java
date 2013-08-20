package edu.stanford.nlp.pipeline;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ie.machinereading.BasicEntityExtractor;
import edu.stanford.nlp.ie.machinereading.BasicRelationExtractor;
import edu.stanford.nlp.ie.machinereading.Extractor;
import edu.stanford.nlp.ie.machinereading.ExtractorMerger;
import edu.stanford.nlp.ie.machinereading.MachineReading;
import edu.stanford.nlp.ie.machinereading.domains.nfl.BasicNFLInference;
import edu.stanford.nlp.ie.machinereading.domains.nfl.ConsistencyChecker;
import edu.stanford.nlp.ie.machinereading.domains.nfl.NFLEntityExtractor;
import edu.stanford.nlp.ie.machinereading.domains.nfl.NFLMaxRecallEntityExtractor;
import edu.stanford.nlp.ie.machinereading.domains.nfl.NFLReader;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

/**
 * Implements both entity and relation extraction for the NFL domain
 * @author Mihai
 */
public class NFLAnnotator implements Annotator {
  MachineReading mr;
  
  private static boolean verbose = false;
  private static boolean useMaxRecall = false;
  
  Map<String, Set<String>> partialToFullTeamNames;
  
  public NFLAnnotator(Properties props) {
    verbose = Boolean.parseBoolean(props.getProperty("nfl.verbose", "false"));
    useMaxRecall = Boolean.parseBoolean(props.getProperty("nfl.relations.use.max.recall", "false"));
    boolean useModelMerging = Boolean.parseBoolean(props.getProperty("nfl.relations.use.model.merging", "false"));
    boolean useBasicInference = Boolean.parseBoolean(props.getProperty("nfl.relations.use.basic.inference", "true"));
    String gazetteer = props.getProperty("nfl.gazetteer", DefaultPaths.DEFAULT_NFL_GAZETTEER);
    String entityModel = props.getProperty("nfl.entity.model", DefaultPaths.DEFAULT_NFL_ENTITY_MODEL);
    String relationModel = props.getProperty("nfl.relation.model", DefaultPaths.DEFAULT_NFL_RELATION_MODEL);
    String[] relationModels = props.getProperty("nfl.relation.model", DefaultPaths.DEFAULT_NFL_RELATION_MODEL).split(",");
    try {
      Extractor entityExtractor = null;
      if (useMaxRecall) {
        entityExtractor = new NFLMaxRecallEntityExtractor(gazetteer);
      }
      else entityExtractor = BasicEntityExtractor.load(entityModel, NFLEntityExtractor.class, true);
      
      Extractor relationExtractor;
      if (useModelMerging) {
        relationExtractor = ExtractorMerger.buildRelationExtractorMerger(relationModels);
      } else {
        relationExtractor = BasicRelationExtractor.load(relationModel);
      }
      Extractor consistencyChecker = new ConsistencyChecker();
      Extractor inference = useBasicInference ? new BasicNFLInference() : null;
      mr = MachineReading.makeMachineReadingForAnnotation(new NFLReader(props), entityExtractor, relationExtractor, null, consistencyChecker,
          inference, true, verbose);
      loadTeamNames(gazetteer);
    } catch(Exception e){
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public void annotate(Annotation annotation) {
    // extract entities and relations
    Annotation output = mr.annotate(annotation);
    
    // transfer entities/relations back to the original annotation
    List<CoreMap> outputSentences = output.get(SentencesAnnotation.class);
    List<CoreMap> origSentences = annotation.get(SentencesAnnotation.class);
    for(int i = 0; i < outputSentences.size(); i ++){
      CoreMap outSent = outputSentences.get(i);
      CoreMap origSent = origSentences.get(i);
      
      // set entities
      List<EntityMention> entities = outSent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
      origSent.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, entities);
      if(verbose && entities != null){
        System.err.println("Extracted the following entities:");
        for(EntityMention e: entities){
          System.err.println("\t" + e);
        }
      }
      
      // generate the normalized forms of entity names
      // this sets NormalizedEntityNameAnnotation
      if(origSent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class) != null){
        for(EntityMention em: origSent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class)){
          normalizeTeamName(em, origSentences, i);
        }
      }
      
      // set relations
      List<RelationMention> relations = outSent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
      origSent.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, relations);
      if(verbose && relations != null){
        System.err.println("Extracted the following relations:");
        for(RelationMention r: relations){
          if(! r.getType().equals(RelationMention.UNRELATED)){
            System.err.println(r);
          }
        }
      }
      
      // the NFLTokenizer might have changed some of the token texts (e.g., "10-5" -> "10 to 5")
      // revert all tokens to their original texts
      boolean verboseRevert = false;
      String origText = annotation.get(CoreAnnotations.TextAnnotation.class);
      if(origText == null) throw new RuntimeException("Found corpus without text!");
      if(verboseRevert) System.err.println("REVERTING SENT: " + origSent.get(TextAnnotation.class));
      List<CoreLabel> tokens = origSent.get(TokensAnnotation.class);
      List<Pair<Integer, String>> changes = new ArrayList<Pair<Integer,String>>();
      int position = 0;
      for(CoreLabel token: tokens) {
        String tokenText = token.word();
        if(verboseRevert) System.err.println("TOKEN " + tokenText + " " + token.beginPosition() + " " + token.endPosition());
        String origToken = origText.substring(token.beginPosition(), token.endPosition());
        if(! origToken.equals(tokenText)){
          if(verboseRevert) System.err.println("Found difference at position #" + position + ": token [" + tokenText + "] vs text [" + origToken + "]");
          token.set(TextAnnotation.class, origToken);
          changes.add(new Pair<Integer, String>(position, origToken));
        }
        position ++;
      }
      // revert Tree leaves as well, if tokens were modified
      Tree tree = origSent.get(TreeAnnotation.class);
      if(tree != null && changes.size() > 0){
        List<Tree> leaves = tree.getLeaves();
        for(Pair<Integer, String> change: changes) {
          Tree leaf = leaves.get(change.first);
          if(verboseRevert) System.err.println("CHANGING LEAF " + leaf);
          leaf.setValue(change.second);
          if(verboseRevert) System.err.println("NEW LEAF: " + leaf);
        }
      }
    }
  }

  private void loadTeamNames(String fn) throws IOException {
    partialToFullTeamNames = new HashMap<String, Set<String>>();
    // try to load the file from the CLASSPATH first
    InputStream is = getClass().getClassLoader().getResourceAsStream(fn);
    // if not found in the CLASSPATH, load from the file system
    if (is == null) is = new FileInputStream(fn);
    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
    
    String line;
    while((line = rd.readLine()) != null){
      String [] tokens = line.split("[ \t\n]+");
      if(tokens.length == 0) continue;
      String label = tokens[0];
      if(! label.equals("NFLTeam")) continue;
      
      StringBuffer fullName = new StringBuffer();
      for(int i = 1; i < tokens.length; i ++){
    	  if(i > 1) fullName.append(" ");
    	  fullName.append(tokens[i]);
      }
      
      for(int i = tokens.length; i > 1; i --){
        addEntry(tokens, 1, i, fullName.toString());
      }
      for(int i = 2; i < tokens.length; i ++){
        addEntry(tokens, i, tokens.length, fullName.toString());
      }
    }
    rd.close();
    is.close();
  }
  
  private void addEntry(String [] tokens, int start, int end, String fullName){
    StringBuffer os = new StringBuffer();
    for(int i = start; i < end; i ++){
      if(i > start) os.append(" ");
      os.append(tokens[i].toLowerCase());
    }
    String name = os.toString();
    if(! STOP_WORDS.contains(name)){
      Set<String> fullNames = partialToFullTeamNames.get(name);
      if(fullNames == null){
        fullNames = new HashSet<String>();
        partialToFullTeamNames.put(name, fullNames);
      }
      fullNames.add(fullName.replace(".", ""));
    }
  }
  
  private static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(new String[] {"st", "st.", "san", "new", "old", "the" }));

  /**
   * For entity mentions that are NFLTeam set NormalizedEntityNameAnnotation to the full team name
   * Also, in case the extent is set incorrectly to a subsequence of the name, e.g., "Bay" in "Green Bay won..." we adjust it as well
   * @param mention
   */
  private void normalizeTeamName(EntityMention mention, List<CoreMap> sentences, int sentPosition) {
    if(! mention.getType().equals("NFLTeam")){
      // nothing to do
      return;
    }
    
    List<CoreLabel> tokens = mention.getSentence().get(TokensAnnotation.class);
    int endToken = mention.getExtentTokenEnd();
    int startToken = mention.getExtentTokenStart();
    //System.err.println("Normalizing mention: " + joinTokens(tokens, startToken, endToken));
    
    // expand mention to the right, if possible
    for(endToken = endToken + 1; endToken <= tokens.size(); endToken ++) {
      String nameCandidate = joinTokens(tokens, startToken, endToken);
      //System.err.println("Name candidate (right): " + nameCandidate);
      // if this candidate does not match a team name stop
      if(! partialToFullTeamNames.containsKey(nameCandidate)){
        break;
      }
    }
    endToken --;
    
    // expand mention to the left, if possible 
    for(startToken = startToken - 1; startToken >= 0; startToken --){
      String nameCandidate = joinTokens(tokens, startToken, endToken);
      //System.err.println("Name candidate (left): " + nameCandidate);
      // if this candidate does not match a team name stop
      if(! partialToFullTeamNames.containsKey(nameCandidate)){
        break;
      }
    }
    startToken ++;
    
    // store the new mention boundaries
    if(startToken != mention.getExtentTokenStart() || endToken != mention.getExtentTokenEnd()) {
      if(verbose) System.err.println("Mention extended from " + joinTokens(tokens, mention.getExtentTokenStart(), mention.getExtentTokenEnd()) + " to " + joinTokens(tokens, startToken, endToken));
      mention.setExtent(new Span(startToken, endToken));
    }
    
    // if this span matches a team name, store the normalized team name
    // note that we might have multiple team names that match this mention (in practice this happens only for Jets and Giants)
    // if the above is seen, disambiguate the names by picking the closest mention of a team to this
    String nameCandidate = joinTokens(tokens, startToken, endToken);
    Set<String> fullNames = partialToFullTeamNames.get(nameCandidate);
    if(fullNames != null && fullNames.size() > 0){
      if(fullNames.size() > 1){
    	  fullNames = disambiguateNames(sentences, sentPosition, startToken, endToken, nameCandidate, new ArrayList<String>(fullNames));
    	  // note: disambiguation may fail, in which case the set size is still larger than 1
      }
      StringBuffer fullNameAnnotation = new StringBuffer();
      List<String> sortedNames = new ArrayList<String>(fullNames);
      Collections.sort(sortedNames);
      boolean first = true;
      for(String n: sortedNames){
        if(! first) fullNameAnnotation.append("|");
        fullNameAnnotation.append(n);
        first = false;
      }
      if(verbose) System.err.println("Mention " + nameCandidate + " mapped to normalized name: " + fullNameAnnotation.toString());
      mention.setNormalizedName(fullNameAnnotation.toString().replaceAll("\\s+", ""));
    }
  }
  
  /**
   * Attempts to disambiguate incomplete team names, e.g., nameCandidate = "New York", which can match "New York Jets" or "New York Giants"
   * @param sentences
   * @param sentPosition
   * @param startToken
   * @param endToken
   * @param nameCandidate
   * @param fullNames
   * @return Set of full names after disambiguation. If disambiguation suceeds, this set has size == 1
   */
  private Set<String> disambiguateNames(List<CoreMap> sentences, 
		  int sentPosition, 
		  int startToken, 
		  int endToken, 
		  String nameCandidate, 
		  List<String> fullNames) {
	List<String> stringsToMatch = new ArrayList<String>();
	for(String fullName: fullNames) {
		String diff = fullName.toLowerCase().replaceAll(nameCandidate, "").trim();
		stringsToMatch.add(diff);
	}
	assert(stringsToMatch.size() == fullNames.size());
	if(verbose) System.err.println("stringsToMatch: " + stringsToMatch);
	startToken --;
	while(sentPosition >= 0){
		List<CoreLabel> tokens = sentences.get(sentPosition).get(TokensAnnotation.class);
		assert(tokens != null);
		assert(startToken < tokens.size());
		for(; startToken >= 0; startToken --){
			for(int i = 0; i < stringsToMatch.size(); i ++){
				if(matches(stringsToMatch.get(i), tokens, startToken)){
					System.err.println("Name " + nameCandidate + " disambiguated to: " + fullNames.get(i));
					Set<String> disambiguated = new HashSet<String>();
					disambiguated.add(fullNames.get(i));
					return disambiguated;
				}
			}
		}
		sentPosition --;
		if(sentPosition >= 0) startToken = sentences.get(sentPosition).get(TokensAnnotation.class).size() - 1;
	}
	
	return new HashSet<String>(fullNames);
  }
  
  /**
   * Verifies if the given text (which might contain more than one token) matches the token sequence at the given offset 
   * @param text
   * @param tokens
   * @param offset
   */
  private boolean matches(String text, List<CoreLabel> tokens, int offset) {
	  String [] textBits = text.split("\\s+");
	  if(textBits.length > tokens.size() - offset) return false;
	  for(int i = 0; i < textBits.length; i ++){
		  if(! textBits[i].equalsIgnoreCase(tokens.get(offset + i).word())) return false;
	  }
	  return true;
  }
  
  private static String joinTokens(List<CoreLabel> tokens, int start, int end) {
    StringBuffer os = new StringBuffer();
    for(int i = start; i < end; i ++){
      if(i > start) os.append(" ");
      os.append(tokens.get(i).getString(TextAnnotation.class).toLowerCase());
    }
    return os.toString();
  }
}
