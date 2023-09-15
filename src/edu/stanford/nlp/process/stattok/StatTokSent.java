package edu.stanford.nlp.process.stattok;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.RuntimeClassNotFoundException;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;


import java.io.*;
import java.util.*;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.StringUtils;



/**
 * The statistical tokenizer implementation.
 *<br>
 * It produces an UD compliant tokenization given a pre-trained UD model file,
 * and a file containing multi-word tokens that have to be treated with rules after tokenization.
 *<br>
 * It reads raw text from CoreAnnotations.textAnnotation,
 * and returns sentences and tokens in the form:
 * {@code List<List<CoreLabel>>}
 *<br>
 * Each {@code List<CoreLabel>} object contains Tokens Annotation for a single sentence.
 *
 * @author Alessandro Bondielli
*/

public class StatTokSent{
  ColumnDataClassifier cdc;
  CoreLabelTokenFactory factory = new CoreLabelTokenFactory();
  Map<String, String[]> multiWordRules = new HashMap<String, String[]>();
  int windowSize = 0;

  //private static final Redwood.RedwoodChannels logger = Redwood.channels(StanfordCoreNLP.class);
  private static final Redwood.RedwoodChannels logger = Redwood.channels(StatTokSent.class);

  public static final String SENTINEL = "\u00A7";

  /**
   * This is the constructor for the StatTokSent object.
   * Parameters:
   * 	modelFile: a string containing the path to the model;
   * 	multiWordRulesFile: a string containing the path to the file with multi-word tokens.
   */
  public StatTokSent(String modelFile, String multiWordRulesFile) {
    logger.info("Loading StatTokSent model from " + modelFile);
    if (multiWordRulesFile == null) {
      logger.info("Using default multi word rules");
    } else {
      logger.info("Using multi word rules from " + multiWordRulesFile);
      try {
        multiWordRules = this.readMultiWordRules(multiWordRulesFile);
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }

    ObjectInputStream ois;

    try {
      ois = IOUtils.readStreamFromString(modelFile);
      cdc = ColumnDataClassifier.getClassifier(ois);
      this.windowSize = ois.readInt();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeClassNotFoundException(e);
    }

    logger.info("Found window size of " + this.windowSize);
  }

  public StatTokSent(String modelFile) {
    this(modelFile, null);
  }


  /**
   * The file reader for multi-word tokens rules file
   * The reader accept the following formatting:
   * {@code <token>\t<part>,...,<part>}
   */
  private Map<String, String[]> readMultiWordRules(String multiWordRulesFile) throws IOException {
    Map<String, String[]> multiWordRules = new HashMap<String, String[]>();

    InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(multiWordRulesFile);
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    //BufferedReader reader = new BufferedReader(new FileReader(multiWordRulesFile));
    String line;
    while ((line = reader.readLine()) != null){
      String[] parts = line.split("\t");
      String token = parts[0];
      String[] tokenComponents = parts[1].split(",");
      multiWordRules.put(token, tokenComponents);
    }

    return multiWordRules;
  }

  /**
   * Method to perform classification of each character(with features).
   * The argument is a list of string. each string represent one character and its features.
   * The string <character>\t<features> is used to build the Datum for the classifier.
   * The method returns a list of <character,results> pairs
   */
  private ArrayList<Pair<String, String>> classify(List<String> featurizedText){
    ArrayList<Pair<String, String>> classificationResults = new ArrayList<Pair<String, String>>();

    for (String line : featurizedText){
      String character = line.split("\t")[1];
      Datum<String,String> d = cdc.makeDatumFromLine(line);

      String result = cdc.classOf(d);
      Pair<String, String> charAndResult = new Pair<String,String>(character, result);
      classificationResults.add(charAndResult);
    }
    return classificationResults;
  }

  /**
   * split the original text in characters and extract features for each character
   * output is a list of strings. Each string is in the form "char\tfeatures"
   */
  private List<String> textToFeatures(String text, int windowSize){
    List<String> featurizedText = new ArrayList<String>();
    String[] splitted = text.split("");
    List<String> splittedText = Arrays.asList(splitted); 

    String[] window = new String[windowSize*2+1];
    String toWrite = "";
    for (int i = 0; i < splittedText.size(); i++){
      String currentCharacter = splittedText.get(i);
      Boolean isUpperCase = Character.isUpperCase(currentCharacter.charAt(0));
      toWrite = "";
      for (int j= -(windowSize); j <= windowSize; j++){
        try{
          window[j+windowSize] = splittedText.get(i+j);
        }
        catch (ArrayIndexOutOfBoundsException e){
          window[j+windowSize] = SENTINEL;
        }
      }
      int index = 0;
      for (String character : window){
        if (index == windowSize){
          index++;
          continue;
        }
        toWrite+=character+"\t";
        index++;
      }
      toWrite = "?\t"+currentCharacter+"\t" + toWrite+Integer.toString(isUpperCase ? 1 : 0);

      featurizedText.add(toWrite);
    }

    return featurizedText;
  }


  /** Simple utility method to check if token is in the multiWordRules map*/
  private Boolean tokenToSplit(CoreLabel token, Map<String, String[]> multiWordRules){
    if (multiWordRules.get(token.word()) != null) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * The method splits one multi word token.
   * It takes as arguments the pair <token, originalClass> and the multiWordRules map.
   * The method returns a list of pair: <part-1, originalClass>, <part-2, C>, ..., <part-n, C>
   * Each sub-token is provided with a begin and end position (required annotation).
   * Token position is assigned from the last to the first token, based on the original begin and end points and length of sub-tokens.
   */
  private ArrayList<Pair<CoreLabel, String>> splitToken(Pair<CoreLabel, String> tokenAndClass, Map<String, String[]> multiWordRules){
    /*Return list of tokens and their class*/
    ArrayList<Pair<CoreLabel, String>> splittedTokenAndClass = new ArrayList<>();

    /*Read token properties and class of the original token*/
    CoreLabel token = tokenAndClass.first();
    String originalClass = tokenAndClass.second();

    int tokenBeginPosition = token.beginPosition();
    int tokenEndPosition = token.endPosition();
    // Get token parts from dictionary. Convert String[] into List<String>
    // TODO: add List<String> to multiWordRules Map to avoid conversion
    String[] splitted = multiWordRules.get(token.word());
    List<String> splittedList = new ArrayList<String>();			
    for (String word : splitted){
      splittedList.add(word);
    }
    // Iterate over the list of token parts backwards.
    // Each token is created from the part. Its length is used to
    // assign begin and end position, starting from the end position
    // of the original token.
    // The last considered token's (the first) begin and end position
    // are set based on the remaining characters.
    // Then, the token is added in first position of the return list,
    // with the corresponding class (C if is not the last token, the
    // original class of the main token if it is the last)
    ListIterator<String> backwardsPartsIterator = null;
    backwardsPartsIterator = splittedList.listIterator(splittedList.size());
    while (backwardsPartsIterator.hasPrevious()){
      Pair<CoreLabel, String> partTokenAndClass = new Pair<CoreLabel,String>();	
      CoreLabel partToken = new CoreLabel();
      String part = backwardsPartsIterator.previous();
      // if token is not the last one, add class C
      if (backwardsPartsIterator.hasPrevious()){
        int partLength = part.length();
        partToken = factory.makeToken(part, token.originalText(), tokenEndPosition-partLength, partLength+1);
        tokenEndPosition = tokenEndPosition-partLength;
        partTokenAndClass = new Pair<CoreLabel,String>(partToken, "C");	
      }
      // if last token (first part), add original class (S or T)
      else{
        int partLength = part.length();
        partToken = factory.makeToken(part, token.originalText(), tokenBeginPosition, partLength+1);
        partTokenAndClass = new Pair<CoreLabel, String>(partToken, originalClass);	
      }			
      splittedTokenAndClass.add(0, partTokenAndClass);			
    }
    return splittedTokenAndClass;
  }

  /**
   * This is the method that returns the final list of sentences and tokens.
   * It has to iterate on the <token, class> list a few times to:
   * 1) Identify tokens that has to be splitted 
   * 2) Index tokens
   * 3) Add CoreAnnotations.CoNLLUTokenSpanAnnotation for representation of multi word tokens in the CoNLL-U format
   * 4) Generate the List<CoreLabel> for a sentence
   */
  private List<CoreLabel> makeSentenceTokens(ArrayList<Pair<CoreLabel, String>> tokensAndClasses){

    // First pass over tokens. Each token is seached in the multiWordRules map.
    // If the token is found, only its parts are added to the output list.
    // While adding to the output list, tokens for each sentence are indexed (starting from 1).
    ArrayList<Pair<CoreLabel, String>> multiTokensAndClasses = new ArrayList<Pair<CoreLabel,String>>();
    int index = 1;
    for (Pair<CoreLabel, String> tokenAndClass : tokensAndClasses){
      CoreLabel token = tokenAndClass.first();
      //split the token if needed and store it in new list, increment index
      if (this.tokenToSplit(token, multiWordRules)){
        ArrayList<Pair<CoreLabel, String>> multiTokenAndClass = this.splitToken(tokenAndClass, multiWordRules);
        for (Pair<CoreLabel, String> partTokenAndClass : multiTokenAndClass){
          //set the index of each token part
          partTokenAndClass.first().setIndex(index);
          index++;
          multiTokensAndClasses.add(partTokenAndClass);
        }
      }
      //only store token in new list and increment index
      else{
        tokenAndClass.first().setIndex(index);
        index++;
        multiTokensAndClasses.add(tokenAndClass);
      }
    }

    // Second pass over tokens.
    // Tokens are iterated from last to first to identify spans of multi-word tokens.
    // Whenever a C class is found, span counter is started until a T or S token is found.
    // Then, all affected token are assigned with OriginalTextAnnotation and CoNLLUTokenSpanAnnotation.
    List<CoreLabel> sentenceTokens = new ArrayList<>();
    ListIterator<Pair<CoreLabel,String>> backwardsMultiTokensAndClasses = multiTokensAndClasses.listIterator(multiTokensAndClasses.size());
    int spanLenght = 0;
    String origText = "";
    boolean changeOrigText = false;
    while (backwardsMultiTokensAndClasses.hasPrevious()){
      Pair<CoreLabel,String> tokenAndClass = backwardsMultiTokensAndClasses.previous();
      CoreLabel token = tokenAndClass.first();
      String beginClass = tokenAndClass.second();

      if (beginClass.equals("C")){
        spanLenght++;
        if (token.word() == token.originalText()){
          origText = token.word()+origText;
          changeOrigText = true;
        }
      } else {
        if (spanLenght > 0){
          int spanBegin = token.index();
          int spanEnd = token.index()+spanLenght;
          IntPair tokenSpan = new IntPair(spanBegin, spanEnd);
          origText = token.word()+origText;
          for (int i = 0; i <= spanLenght; i++){
            if (changeOrigText){
              multiTokensAndClasses.get(token.index()-1+i).first().set(CoreAnnotations.OriginalTextAnnotation.class, origText);
            }
            multiTokensAndClasses.get(token.index()-1+i).first().set(CoreAnnotations.CoNLLUTokenSpanAnnotation.class, tokenSpan);
          }
          origText = "";
          changeOrigText = false;
          spanLenght = 0;
        }
      }
    }

    // Final pass over tokens. Simply add tokens (without classes) to the output List<CoreLabel>
    for (Pair<CoreLabel,String> tokenAndClass : multiTokensAndClasses){
      CoreLabel token = tokenAndClass.first();
      String beginClass = tokenAndClass.second();

      sentenceTokens.add(token);
    }

    return sentenceTokens;
  }

  /**
   * The core tokenization function called via the statistical tokenizer annotator. Given a text and window size as input, 
   * returns a {@code List<List<CoreLabel>>} corresponding to sentences and tokens within each sentence.
   * First, features are extracted for each character of the text, and the model is used to classify each character as: <br>
   * 	S: begin of sentence; <br>
   * 	T: begin of token; <br>
   * 	C: begin of part in a multi-word token; <br>
   * 	I: inside of a token; <br>
   * 	O: outside any token. <br>
   * Given the classification, characters are joined in tokens.
   * For each sentence, tokens further analyzed to identify possibily misclassified multi-word tokens via rules, and indexed.
   */
  public List<List<CoreLabel>> tokenize(String text){
    // Call textToFeatures function to generate features for each character.
    List<String> featurizedText = this.textToFeatures(text, this.windowSize);
		
    // Call the classify function, return a list of pairs <character, class>.
    ArrayList<Pair<String, String>> classificationResults = this.classify(featurizedText);

    // initialize list to store results for each sentence and for the whole text
    List<CoreLabel> sentenceTokens 	= new ArrayList<>();
    List<List<CoreLabel>> ret 		= new ArrayList<>();
		
    int i 		= 0;
    int beginToken 	= 0;
    int endToken 	= 0;

    String currentWord = "";
    String lastBeginChar = "";
    int tokensCounter = 0;

    //Initialize [(tok, class)] data structure
    ArrayList<Pair<CoreLabel, String>> sentenceTokensBase = new ArrayList<Pair<CoreLabel, String>>();

    // Iterate over <character,class>.
    // If class is S, analyze tokens for the previous sentence and start new sentence
    // If class is T or C, and current word is empty, generate previous token
    // If class is I, add character to current token
    // If class is O, generate previous token
    // at the end of the list, analyze tokens for the last sentence.
    while (i < classificationResults.size()){
      //store current values for character and class
      String currentChar  = classificationResults.get(i).first();
      String currentClass = classificationResults.get(i).second();
      // sometimes the classifier will get this wrong :/
      // TODO: add a parameter to make double sentinels,
      // then tokenize that as a new sentence
      if (currentChar.equals(SENTINEL) && currentClass.equals("I")) {
        currentClass = "O";
      }

      //Beginning of sentence
      if (currentClass.equals("S")){
        lastBeginChar = currentClass;
        //In case of S as first character, do not add any sentence
        if (i == 0){
          currentWord+=currentChar;
          i++;
          continue;
        } else {
          //If there hasn't been O class between tokens, create token for last word
          if (currentWord != ""){
            endToken = i-1;
            CoreLabel newToken = factory.makeToken(currentWord, currentWord, beginToken, endToken-beginToken+1);
            Pair<CoreLabel, String> tokenAndClass = new Pair<CoreLabel, String>(newToken,lastBeginChar);
            sentenceTokensBase.add(tokenAndClass);
            tokensCounter++;
          }

          //generate sentence tokens with clitics, spans etc.
          sentenceTokens = this.makeSentenceTokens(sentenceTokensBase);
          ret.add(sentenceTokens);
        }	
        //clean list for new sentence and word
        sentenceTokensBase = new ArrayList<>();
        currentWord = "";
        beginToken = i;
        if (!currentChar.equals(SENTINEL)) {
          currentWord+=currentChar;
        }
      }

      //Normal token or clitic token
      if (currentClass.equals("T") || currentClass.equals("C")){
        //If there hasn't been O class between tokens, create token for last word
        if (currentWord != ""){
          endToken = i-1;
          CoreLabel newToken = factory.makeToken(currentWord, currentWord, beginToken, endToken-beginToken+1);
          Pair<CoreLabel, String> tokenAndClass = new Pair<CoreLabel, String>(newToken,lastBeginChar);
          sentenceTokensBase.add(tokenAndClass);
          tokensCounter++;
        }
        //Initialize variables for new token
        beginToken = i;
        endToken = i;
        currentWord = "";
        if (!currentChar.equals(SENTINEL)) {
          currentWord+=currentChar;
        }
        lastBeginChar = currentClass;
      }

      //Inside token
      if (currentClass.equals("I")){
        //append to current word
        currentWord+=currentChar;
      }

      //Outside token
      if (currentClass.equals("O")){
        endToken = i-1;
        //Create new token with previous character, add it with its class to list, increment token counter
        CoreLabel newToken = factory.makeToken(currentWord, currentWord, beginToken, endToken-beginToken+1);
        Pair<CoreLabel, String> tokenAndClass = new Pair<CoreLabel, String>(newToken,lastBeginChar);
        sentenceTokensBase.add(tokenAndClass);
        tokensCounter++;

        //empty word
        currentWord="";
      }

      //End of text
      if (i==(classificationResults.size()-1)) {
        endToken = i-1;
        CoreLabel newToken = factory.makeToken(currentWord, currentWord, beginToken, endToken-beginToken+1);
        Pair<CoreLabel, String> tokenAndClass = new Pair<CoreLabel, String>(newToken,lastBeginChar);
        sentenceTokensBase.add(tokenAndClass);
        tokensCounter++;
        sentenceTokens = this.makeSentenceTokens(sentenceTokensBase);
        ret.add(sentenceTokens);
      }
      i++;
    }
    return ret;
  }


  //main function, only used as demo
  public static void main(String[] args) throws Exception{
    Map<String, String[]> arguments = StringUtils.argsToMap(args);
    String textFile = null;
    int windowSize = 0;
    String multiWordRulesFile = null;
    try{
      textFile = arguments.get("-textFile")[0];
    }catch (NullPointerException ex){
      System.out.println("You have not specified a text file.\nUse -textFile option.");
      ex.printStackTrace();
    }
    final String modelFile;
    try {
      modelFile = arguments.get("-model")[0];
    } catch (NullPointerException ex) {
      System.out.println("You have not specified a model.\nUse -model option.");
      throw ex;
    }
    try{
      windowSize = Integer.parseInt(arguments.get("-windowSize")[0]);
    }catch (NullPointerException ex){
      System.out.println("You have not specified a window size.\nUse -windowSize option.");
      ex.printStackTrace();
    }

    String text = "";
    try{
      BufferedReader reader = new BufferedReader(new FileReader(textFile));
      String line;
      while ((line = reader.readLine()) != null){
        text = text+line;
      }
    }catch (Exception e){
      e.printStackTrace();
    }
    try{
      multiWordRulesFile = arguments.get("-multiWordRules")[0];
    }catch (NullPointerException ex){
      System.out.println("No multiWordRules file specified.");
    }

    StatTokSent tokenizer = null;
    if (multiWordRulesFile != null){
      tokenizer = new StatTokSent(modelFile, multiWordRulesFile);
    }else{
      tokenizer = new StatTokSent(modelFile);
    }

    List<List<CoreLabel>> sentences = tokenizer.tokenize(text);

    for (List<CoreLabel> sentence: sentences){
      for (CoreLabel token : sentence){
        System.out.println(token);
      }
      System.out.println("");
    }
  }
}
