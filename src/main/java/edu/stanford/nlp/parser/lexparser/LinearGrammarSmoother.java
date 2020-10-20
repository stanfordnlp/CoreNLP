package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Arrays;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * Implements linear rule smoothing a la Petrov et al. (2006).
 * 
 * @author Spence Green
 *
 */
public class LinearGrammarSmoother implements Function<Pair<UnaryGrammar,BinaryGrammar>, Pair<UnaryGrammar,BinaryGrammar>>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(LinearGrammarSmoother.class);

  private static final boolean DEBUG = false;
  
  private double ALPHA = 0.01;

//  private static final String SYNTH_NODE_MARK = "@";
//  
//  private static final Pattern pContext = Pattern.compile("(\\|.+)$");
  
  // Do not include @ in this list! @ marks synthetic nodes!
  // Stole these from PennTreebankLanguagePack
  private final String[] annotationIntroducingChars = {"-", "=", "|", "#", "^", "~", "_"};
  private final Set<String> annoteChars = Generics.newHashSet(Arrays.asList(annotationIntroducingChars));

  private final TrainOptions trainOptions;

  private final Index<String> stateIndex;
  private final Index<String> tagIndex;

  public LinearGrammarSmoother(TrainOptions trainOptions, Index<String> stateIndex, Index<String> tagIndex) {
    this.trainOptions = trainOptions;
    this.stateIndex = stateIndex;
    this.tagIndex = tagIndex;
  }

  /**
   * Destructively modifies the input and returns it as a convenience.
   */
  public Pair<UnaryGrammar,BinaryGrammar> apply(Pair<UnaryGrammar,BinaryGrammar> bgug) {
    
    ALPHA = trainOptions.ruleSmoothingAlpha;
    Counter<String> symWeights = new ClassicCounter<>();
    Counter<String> symCounts = new ClassicCounter<>();

    //Tally unary rules
    for (UnaryRule rule : bgug.first()) {
      if ( ! tagIndex.contains(rule.parent)) {
        updateCounters(rule, symWeights,symCounts);  
      }
    }

    //Tally binary rules
    for (BinaryRule rule : bgug.second()) {
      updateCounters(rule, symWeights, symCounts);  
    }

    //Compute smoothed rule scores, unary
    for (UnaryRule rule : bgug.first()) {
      if ( ! tagIndex.contains(rule.parent)) {
        rule.score = smoothRuleWeight(rule,symWeights,symCounts);
      }
    }

    //Compute smoothed rule scores, binary
    for (BinaryRule rule : bgug.second()) {
      rule.score = smoothRuleWeight(rule,symWeights,symCounts);  
    }
    
    if(DEBUG) {
      System.err.printf("%s: %d basic symbols in the grammar%n",this.getClass().getName(),symWeights.keySet().size());
      for(String s : symWeights.keySet())
        log.info(s + ",");
      log.info();
    }
    
    return bgug;
  }


  private void updateCounters(Rule rule, Counter<String> symWeights,
      Counter<String> symCounts) {
    String label = stateIndex.get(rule.parent());
    String basicCat = basicCategory(label);
    symWeights.incrementCount(basicCat, Math.exp(rule.score()));
    symCounts.incrementCount(basicCat);
  }


  private float smoothRuleWeight(Rule rule, Counter<String> symWeights, Counter<String> symCounts) {  
    String label = stateIndex.get(rule.parent());
    String basicCat = basicCategory(label);

    double pSum = symWeights.getCount(basicCat);
    double n = symCounts.getCount(basicCat);

    double pRule = Math.exp(rule.score());

    double pSmooth = (1.0 - ALPHA)*pRule;
    pSmooth += ALPHA*(pSum / n);
    pSmooth = Math.log(pSmooth);
    
    if(DEBUG)
      System.err.printf("%s\t%.4f%n", rule.toString(),pSmooth);

    return (float) pSmooth;
  }


  private int postBasicCategoryIndex(String category) {
    boolean sawAtZero = false;
    String seenAtZero = "\u0000";
    int i;
    for (i = 0; i < category.length(); i++) {
      String ch = category.substring(i,i+1);
      if (annoteChars.contains(ch)) {
        if (i == 0) {
          sawAtZero = true;
          seenAtZero = ch;
        } else if (sawAtZero && ch == seenAtZero) {
          sawAtZero = false;
        } else {
          break;
        }
      }
    }
    return i;
  }

  public String basicCategory(String category) {
    if (category == null) {
      return null;

    } else {
      String basicCat = category.substring(0, postBasicCategoryIndex(category));
      
//wsg2011: Tried adding the context of synthetic nodes to the basic category, but this lowered F1.
//      if(String.valueOf(category.charAt(0)).equals(SYNTH_NODE_MARK)) {
//        Matcher m = pContext.matcher(category);
//        if(m.find()) {
//          String context = m.group(1);
//          basicCat = basicCat + context;
//        
//        } else {
//          throw new RuntimeException(String.format("%s: Synthetic label lacks context: %s",this.getClass().getName(),category));
//        }
//      } 
      
      return basicCat;
    }
  }
}
