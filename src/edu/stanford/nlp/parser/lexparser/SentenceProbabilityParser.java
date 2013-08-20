package edu.stanford.nlp.parser.lexparser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.*;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.RCDoubleMatrix2D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.stats.Sampleable;
import edu.stanford.nlp.stats.Sampler;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.Timing;


/** Works out the probability of a String under a PCFG grammar using the
 *  inside (sum-product) algorithm.
 *
 *  @author Teg Grenager (grenager@cs.stanford.edu)
 *  @author Jenny Finkel (parse sampling)
 */
public class SentenceProbabilityParser extends ExhaustivePCFGParser implements Sampleable<List<? extends HasWord>, Tree> {

  private transient List<UnaryRule>[] closedRulesUnderSumWithParent; // = null;
  private transient List<UnaryRule>[] closedRulesUnderSumWithChild; // = null;

  private transient UnaryRule[][] closedRulesUnderSumWithP; // = null;
  private transient UnaryRule[][] closedRulesUnderSumWithC; // = null;

  private static final boolean doExactClosure = false;
  private static final double closureApproxTol = 0.0001;

  public UnaryRule[] closedRulesUnderSumByParent(int state) {
    return closedRulesUnderSumWithP[state];
  }

  public UnaryRule[] closedRulesUnderSumByChild(int state) {
    return closedRulesUnderSumWithC[state];
  }

  /**
   * Closes all rules in coreRules under the sum operation, and updates
   * the closedRulesUnderSumWithP and closedRulesUnderSumWithC
   * data structures. Uses the COLT linear algebra package for matrix inversion.
   */
  protected void closeRulesUnderSum() {
    if (doExactClosure) {
      exactCloseRulesUnderSum();
    } else {
      approximateCloseRulesUnderSum();
    }

  }

  private void approximateCloseRulesUnderSum() {
    Timing.startTime();
    System.err.println("Starting to close rules");
    System.err.println("numStates = " + numStates);
    // first figure out which rows/columns we have to represent
    IntArrayList decompressionList = new IntArrayList();
    int[] compressionArray = new int[numStates];
    for (int i = 0; i < numStates; i++) {
      compressionArray[i] = -1;
    }
    List<UnaryRule>[] rulesWithParent = ug.rulesWithParent();
    for (int i = 0; i < rulesWithParent.length; i++) {
      if ( ! rulesWithParent[i].isEmpty()) {
        decompressionList.add(i);
        int index = decompressionList.size() - 1;
        compressionArray[i] = index;
      }
    }
    int numCompressed = decompressionList.size();
    System.err.println("numCompressed = " + numCompressed);

    // now go through all rules once to find max state num, in case it is lower than numStates
    // if the grammar was reconstructed from a text grammar, then the UnaryRules were added first to the
    // Index, and the maxState will be much smaller than numStates.
    int maxState = -1;
    for (UnaryRule tempRule: ug) {
      if (tempRule.parent > maxState) {
        maxState = tempRule.parent;
      }
      if (tempRule.child > maxState) {
        maxState = tempRule.child;
      }
    }
    maxState++; // so that this is the size of the arrays we need

    System.err.println("maxState = " + maxState);
    // make a square sparse COLT matrix
    DoubleMatrix2D pcompressed = new RCDoubleMatrix2D(numCompressed, numCompressed);
    //    System.out.println("pcompressed: " + pcompressed.rows() + " x " + pcompressed.columns());
    DoubleMatrix2D p = new SparseDoubleMatrix2D(maxState, maxState);
    //    System.out.println("p: " + p.rows() + " x " + p.columns());

    for (UnaryRule tempRule : ug) {
      // none of these entries should be on the diagonal
      // we have to convert from log to linear space so we can negate
      if (tempRule.parent == tempRule.child) {
        System.err.println("Uhoh, reflexive rule");
      }
      double linearScore = Math.exp(tempRule.score);
      p.setQuick(tempRule.parent, tempRule.child, linearScore);
      int compressedParent = compressionArray[tempRule.parent];
      int compressedChild = compressionArray[tempRule.child];
      if (compressedChild >= 0) {
        // this one can be mapped back
        pcompressed.setQuick(compressedParent, compressedChild, linearScore);
      }
    }

    // start with resultcompressed = pcompressed
    DoubleMatrix2D sumCompressed = pcompressed.copy();
    DoubleMatrix2D pow = pcompressed.copy();
    DoubleMatrix2D temp = new RCDoubleMatrix2D(numCompressed, numCompressed);
    System.err.println("Starting multiplication.");
    do {
      temp.assign(pow);
      //      System.out.println("pcompressed: " + pcompressed.rows() + " x " + pcompressed.columns());
      //      System.out.println("temp: " + temp.rows() + " x " + temp.columns());
      //      System.out.println("pow: " + pow.rows() + " x " + pow.columns());
      pcompressed.zMult(temp, pow);
      addMatrixInPlace(sumCompressed, pow);
      Timing.tick("Completed iteration.  norm: " + normOfMatrix(pow));
    } while (normOfMatrix(pow) > closureApproxTol);

    temp = pow = pcompressed = null;

    System.err.println("Inflating matrix back to full size.");
    // inflate sumCompressed back to full size in a sparse matrix
    IntArrayList parentList = new IntArrayList();
    IntArrayList childList = new IntArrayList();
    DoubleArrayList scoreList = new DoubleArrayList();
    sumCompressed.getNonZeros(parentList, childList, scoreList);
    DoubleMatrix2D sumExpanded = new RCDoubleMatrix2D(maxState, maxState); // default zeros
    //    System.out.println("sumExpanded: " + sumExpanded.rows() + " x " + sumExpanded.columns());
    //    System.out.println("scores: " + scoreList);
    for (int i = 0; i < scoreList.size(); i++) {
      //      System.out.println("Setting " + decompressionArray[parents[i]] + ", " + decompressionArray[children[i]] + " to " + scores[i]);
      sumExpanded.setQuick(decompressionList.get(parentList.get(i)), decompressionList.get(childList.get(i)), scoreList.get(i));
    }
    //    System.out.println("sumExpanded: " + sumExpanded.rows() + " x " + sumExpanded.columns());
    //    System.out.println("sumExpanded: " + sumExpanded);
    sumCompressed = null;

    // add identity and multiply by p
    // this is a cool trick to get the columns we removed back
    // based on the recurrence
    // result = (I+result)*result
    for (int i = 0; i < maxState; i++) {
      sumExpanded.setQuick(i, i, 1.0);
    }
    DoubleMatrix2D result = new RCDoubleMatrix2D(maxState, maxState);
    //    System.out.println("sumExpanded: " + sumExpanded.rows() + " x " + sumExpanded.columns());
    //    System.out.println("result: " + result.rows() + " x " + result.columns());
    //    System.out.println("p: " + p.rows() + " x " + p.columns());
    sumExpanded.zMult(p, result);
    sumExpanded = p = null;
    Timing.tick("Starting to populate data structures.");

    // set up data structures
    closedRulesUnderSumWithParent = new List[numStates];
    closedRulesUnderSumWithChild = new List[numStates];
    for (int i = 0; i < numStates; i++) {
      closedRulesUnderSumWithParent[i] = new ArrayList<UnaryRule>();
      closedRulesUnderSumWithChild[i] = new ArrayList<UnaryRule>();
    }

    // extract the closedRules into data structures
    parentList = new IntArrayList();
    childList = new IntArrayList();
    scoreList = new DoubleArrayList();
    result.getNonZeros(parentList, childList, scoreList);
    result = null; // to save memory
    int numClosedRules = scoreList.size();
    for (int i = 0; i < numClosedRules; i++) {
      // we must go back to log space
      UnaryRule tempRule = new UnaryRule(parentList.get(i), childList.get(i), Math.log(scoreList.get(i)));
      closedRulesUnderSumWithParent[tempRule.parent].add(tempRule);
      closedRulesUnderSumWithChild[tempRule.child].add(tempRule);
    }
    // make closedRulesUnderSumWithP and closedRulesUnderSumWithC
    closedRulesUnderSumWithP = new UnaryRule[numStates][];
    closedRulesUnderSumWithC = new UnaryRule[numStates][];
    for (int i = 0; i < numStates; i++) {
      closedRulesUnderSumWithP[i] = closedRulesUnderSumWithParent[i].toArray(new UnaryRule[closedRulesUnderSumWithParent[i].size()]);
      closedRulesUnderSumWithC[i] = closedRulesUnderSumWithChild[i].toArray(new UnaryRule[closedRulesUnderSumWithChild[i].size()]);
    }
    Timing.tick("Finished closing rules");
  }

  /**
   * Sets A := A + B
   */
  private static void addMatrixInPlace(DoubleMatrix2D A, DoubleMatrix2D B) {
    IntArrayList rowList = new IntArrayList();
    IntArrayList colList = new IntArrayList();
    DoubleArrayList valList = new DoubleArrayList();
    B.getNonZeros(rowList, colList, valList);
    for (int i = 0; i < valList.size(); i++) {
      double oldVal = A.getQuick(rowList.getQuick(i), colList.getQuick(i));
      A.setQuick(rowList.getQuick(i), colList.getQuick(i), oldVal + valList.getQuick(i));
    }
  }

  private static double normOfMatrix(DoubleMatrix2D A) {
    IntArrayList rowList = new IntArrayList();
    IntArrayList colList = new IntArrayList();
    DoubleArrayList valList = new DoubleArrayList();
    A.getNonZeros(rowList, colList, valList);
    double total = 0;
    for (int i = 0; i < valList.size(); i++) {
      total += valList.getQuick(i) * valList.getQuick(i);
    }
    return total;
  }

  protected void exactCloseRulesUnderSum() {
    Timing.startTime();
    System.err.println("Starting to close rules");
    // first figure out which rows/columns we have to represent
    IntArrayList decompressionList = new IntArrayList();
    int[] compressionArray = new int[numStates];
    for (int i = 0; i < numStates; i++) {
      compressionArray[i] = -1;
    }
    int index;
    List<UnaryRule>[] rulesWithParent = ug.rulesWithParent();
    for (int i = 0; i < rulesWithParent.length; i++) {
      if (rulesWithParent[i].size() > 0) {
        decompressionList.add(i);
        index = decompressionList.size() - 1;
        compressionArray[i] = index;
      }
    }
    decompressionList = null; // to free memory //TODO: um... but then you'll get null pointer exceptions...
    int numCompressed = decompressionList.size();
    System.err.println("numStates: " + numStates + " numCompressed " + numCompressed);

    // make a square sparse COLT matrix
    RCDoubleMatrix2D p = new RCDoubleMatrix2D(numStates, numStates);
    RCDoubleMatrix2D pprime = new RCDoubleMatrix2D(numCompressed, numCompressed);
    // now we add ones on the diagonal to pprime
    for (int i = 0; i < numCompressed; i++) {
      pprime.setQuick(i, i, 1.0);
    }
    UnaryRule tempRule;
    double linearScore;
    int compressedParent, compressedChild;
    for (Iterator<UnaryRule> ruleIter = ug.iterator(); ruleIter.hasNext();) {
      // none of these entries should be on the diagonal
      tempRule = ruleIter.next();
      // we have to convert from log to linear space so we can negate
      if (tempRule.parent == tempRule.child) {
        System.err.println("Uhoh, reflexive rule");
      }
      linearScore = Math.exp(tempRule.score);
      p.setQuick(tempRule.parent, tempRule.child, linearScore);
      compressedParent = compressionArray[tempRule.parent];
      compressedChild = compressionArray[tempRule.child];
      if (compressedChild >= 0) {
        // this one can be mapped back
        pprime.setQuick(compressedParent, compressedChild, -linearScore);
      }
    }

    // invert pprime
    System.err.print("Inverting matrix...");
    Timing.startTime();
    Algebra alg = new Algebra();
    DoubleMatrix2D rprime = alg.inverse(pprime); // this outputs a DenseDoubleMatrix2D
    pprime = null; // to save memory
    Timing.tick("done");

    // inflate rprime back to full size in a sparse matrix
    IntArrayList parentList = new IntArrayList();
    IntArrayList childList = new IntArrayList();
    DoubleArrayList scoreList = new DoubleArrayList();
    rprime.getNonZeros(parentList, childList, scoreList);
    RCDoubleMatrix2D rprimebig = new RCDoubleMatrix2D(numStates, numStates); // default zeros
    for (int i = 0; i < scoreList.size(); i++) {
      rprimebig.setQuick(decompressionList.get(parentList.get(i)), decompressionList.get(childList.get(i)), scoreList.get(i));
    }
    rprime = null; // to save memory
    parentList = null; // to save memory
    childList = null; // to save memory
    scoreList = null; // to save memory

    // multiply rprimebig by p
    System.err.print("Doing matrix multiplication...");
    Timing.startTime();
    RCDoubleMatrix2D result = (RCDoubleMatrix2D) rprimebig.zMult(p, new RCDoubleMatrix2D(numStates, numStates), 1.0, 0.0, false, false);
    Timing.tick("done");
    rprimebig = null;
    p = null;
    rprimebig = null;

    // add the identity matrix to result
    for (int i = 0; i < numStates; i++) {
      result.setQuick(i, i, (1.0 + result.getQuick(i, i)));
    }

    // set up data structures
    closedRulesUnderSumWithParent = new List[numStates];
    closedRulesUnderSumWithChild = new List[numStates];
    for (int i = 0; i < numStates; i++) {
      closedRulesUnderSumWithParent[i] = new ArrayList<UnaryRule>();
      closedRulesUnderSumWithChild[i] = new ArrayList<UnaryRule>();
    }

    // extract the closedRules into data structures
    System.err.println("Starting to populate data structures");
    parentList = new IntArrayList();
    childList = new IntArrayList();
    scoreList = new DoubleArrayList();
    result.getNonZeros(parentList, childList, scoreList);
    result = null; // to save memory
    int numClosedRules = scoreList.size();
    for (int i = 0; i < numClosedRules; i++) {
      // we must go back to log space
      tempRule = new UnaryRule(parentList.get(i), childList.get(i), Math.log(scoreList.get(i)));
      closedRulesUnderSumWithParent[tempRule.parent].add(tempRule);
      closedRulesUnderSumWithChild[tempRule.child].add(tempRule);
    }
    System.err.println("done");
    result = null;
    parentList = null; // to save memory
    childList = null; // to save memory
    scoreList = null; // to save memory

    // make closedRulesUnderSumWithP and closedRulesUnderSumWithC
    closedRulesUnderSumWithP = new UnaryRule[numStates][];
    closedRulesUnderSumWithC = new UnaryRule[numStates][];
    for (int i = 0; i < numStates; i++) {
      closedRulesUnderSumWithP[i] = closedRulesUnderSumWithParent[i].toArray(new UnaryRule[closedRulesUnderSumWithParent[i].size()]);
      closedRulesUnderSumWithC[i] = closedRulesUnderSumWithChild[i].toArray(new UnaryRule[closedRulesUnderSumWithChild[i].size()]);
    }
    System.err.println("Finished closing rules");
  }

  public Sampler<Tree> getSampler(List<? extends HasWord> sentence) {

    List allWords = new ArrayList(sentence);
    allWords.add(Lexicon.BOUNDARY);
    try {
      boolean parsed = parse(allWords);
      System.err.println("sent prob: "+getSentenceProbability());
      if (!parsed) {
        throw new RuntimeException("Error parsing sentence!");
      }
      ParseSampler sampler = new ParseSampler(iScore, this.sentence);
      try {
        sampler.drawSample();
      } catch (Exception e) {
        e.printStackTrace();
        System.err.println("removing tags and re-parsing!");
        for (Object o : allWords) {
          if (o instanceof HasTag) {
            ((HasTag)o).setTag(null);
          }
        }
        parsed = parse(allWords);
        sampler = new ParseSampler(iScore, this.sentence);
        try {
          sampler.drawSample();
        } catch (Exception e1) {
          throw new RuntimeException("Error parsing sentence!", e);
        }
      }
      return sampler;
    } catch (UnsupportedOperationException uoe) {
      throw new RuntimeException("Sentence Too Long!", uoe);
    }
  }

  /**
   * Generates sample trees for a given sentence with given inside probabilities
   * (e.g. from the PCFG parser .. TBD factored parser).
   *
   * @author jrfinkel
   */
  class ParseSampler implements Sampler<Tree> {

    /** Inside probabilities from PCFG */
    private float[][][] iScore;

    /** Sentence to parse */
    private List sentence;

    public ParseSampler(float[][][] iScore, List sentence) {
       this.iScore = ArrayUtils.copy(iScore);
       this.sentence = new ArrayList(sentence);
    }

    public Tree drawSample() { return getSampleParse(); }

    private Tree getSampleParse() {
      int goal = stateIndex.indexOf(goalStr);
      Tree tree = getSampleParse(goal, 0, sentence.size());
      tree = new Debinarizer(op.forceCNF).transformTree(tree);
      tree = op.tlpParams.subcategoryStripper().transformTree(tree);
      return tree;
    }

    /**
     * Recursively generates (top-down from root) over for a particular
     * goal label (S, NP, VP, etc) over  words[start..end] (start inclusive
     * end exclusive).
     */
    private Tree getSampleParse(int goal, int start, int end) {
      double sampleProb = 0.0;
      String goalStr = stateIndex.get(goal);
      if (end - start <= op.testOptions.maxSpanForTags && tagIndex.contains(goalStr)) {
        if (op.testOptions.maxSpanForTags > 1) {
          Tree wordNode = null;
          if (sentence != null) {
            StringBuilder word = new StringBuilder();
            for (int i = start; i < end; i++) {
              if (sentence.get(i) instanceof StringLabel) {
                word.append(((StringLabel) sentence.get(i)).value());
              } else {
                word.append((String) sentence.get(i));
              }
            }
            wordNode = tf.newLeaf(new StringLabel(word.toString()));
          } else {
            throw new RuntimeException("attempt to get word when sentence is null!");
          }
          Tree tagNode = tf.newTreeNode(new StringLabel(goalStr), Collections.singletonList(wordNode));
          return tagNode;
        } else {
          IntTaggedWord tagging = new IntTaggedWord(words[start], tagIndex.indexOf(goalStr));
          float tagScore = lex.score(tagging, start, wordIndex.get(words[start]), null);
          sampleProb += tagScore;
          if (tagScore > Float.NEGATIVE_INFINITY || floodTags) {
            // return a pre-terminal tree
            String wordStr = wordIndex.get(words[start]);
            Tree wordNode = tf.newLeaf(new StringLabel(wordStr));
            List<Tree> childList = new ArrayList<Tree>();
            childList.add(wordNode);
            Tree tagNode = tf.newTreeNode(new StringLabel(goalStr), childList);
            return tagNode;
          }
        }
      }

      double totalProb = 0.0;
      List<UnaryRule> unaryRules = ug.rulesByParent(goal);
      List<BinaryRule> binaryRules = bg.ruleListByParent(goal);

      List<Double> probs = new ArrayList<Double>();

      for (UnaryRule ur : unaryRules) {
        double logProb = ur.score()+iScore[start][end][ur.child];
        probs.add(Math.exp(logProb));
      }

      for (BinaryRule br : binaryRules) {
        for (int split = start+1; split < end; split++) {
          double logProb = br.score()+iScore[start][split][br.leftChild]+iScore[split][end][br.rightChild];
          probs.add(Math.exp(logProb));
        }
      }
      double[] dist = new double[probs.size()];
      int i = 0;
      for (Double d : probs) {
        dist[i++] = d;
      }

      try {
        ArrayMath.normalize(dist);
      } catch (Exception e) {
        for (double d : dist) {
          System.err.print(d+" ");
        }
        throw new RuntimeException(e);
      }
      int sample = -1;
      try {
        sample = ArrayMath.sampleFromDistribution(dist);
      } catch (Exception e) {
        System.err.println(goalStr);
        for (double d : probs) {
          System.err.print(d+" ");
        }
        throw new RuntimeException(e);
      }
      if (sample < unaryRules.size()) {
        UnaryRule ur = unaryRules.get(sample);
        sampleProb += ur.score();
        Tree childTree = getSampleParse(ur.child, start, end);
        List<Tree> children = new ArrayList<Tree>();
        children.add(childTree);
        Tree result = tf.newTreeNode(new StringLabel(goalStr), children);
        return result;
      } else {
        sample -= unaryRules.size();
        int rule = sample / (end-start-1);
        BinaryRule br = binaryRules.get(rule);
        sampleProb += br.score();
        int split = (sample % (end-start-1)) + (start+1);
        Tree leftChildTree = getSampleParse(br.leftChild, start, split);
        Tree rightChildTree = getSampleParse(br.rightChild, split, end);
        List<Tree> children = new ArrayList<Tree>();
        children.add(leftChildTree);
        children.add(rightChildTree);
        Tree result = tf.newTreeNode(new StringLabel(goalStr), children);
        return result;
      }

    }
  }

  /**
   * Parses the sentence, returning true if and only if there is a valid
   * parse of the sentence. Overrides <code>parse(List)</code> in
   * ExhaustivePCFGParser.
   */
  @Override
  public boolean parse(List sentence) {
    if (sentence != this.sentence) {
      this.sentence = sentence;
      floodTags = false;
    }
    if (op.testOptions.verbose) {
      Timing.tick("Starting pcfg parse.");
    }
    if (spillGuts) {
      tick("Starting PCFG parse...");
    }

    length = sentence.size();
    if (length > arraySize) {
      if (length > op.testOptions.maxLength + 1 || length >= myMaxLength) {
        System.err.println(op.testOptions.maxLength+" "+myMaxLength);
        throw new OutOfMemoryError("Refusal to create such large arrays.");
      } else {
        try {
          createArrays(length + 1);
        } catch (OutOfMemoryError e) {
          myMaxLength = length;
          if (arraySize > 0) {
            try {
              createArrays(arraySize);
            } catch (OutOfMemoryError e2) {
              throw new RuntimeException("CANNOT EVEN CREATE ARRAYS OF ORIGINAL SIZE!!!");
            }
          }
          throw e;
        }
      }
      arraySize = length + 1;
      if (op.testOptions.verbose) {
        System.err.println("Created PCFG parser arrays of size " + arraySize);
      }
    }

    int goal = stateIndex.indexOf(goalStr);
    if (op.testOptions.verbose) {
      System.out.println(numStates + " states, " + goal + " is the goal state.");
      System.out.println(bg.numRules() + " binary rules, " + ug.numRules() + " unary rules.");
      //System.out.println(new ArrayList(ug.coreRules.keySet()));
      System.out.print("Initializing...");
    }
    // map to words
    words = new int[length];
    int unk = 0;
    StringBuffer unkWords = new StringBuffer("[");
    for (int i = 0; i < length; i++) {
      Object o = sentence.get(i);
      if (o instanceof HasWord) {
        o = ((HasWord) o).word();
      } else if (!(o instanceof String)) {
        throw new RuntimeException("Needs to have a word!");
      }
      if (op.testOptions.verbose && (!wordIndex.contains(o.toString()) || !lex.isKnown(wordIndex.indexOf(o.toString())))) {
        System.err.println("UNK");
        unk++;
        unkWords.append(" ");
        unkWords.append(o);
      }
      words[i] = wordIndex.indexOf(o.toString());
    }
    // initialize inside and outside score arrays
    if (spillGuts) {
      tick("Wiping arrays...");
    }
    for (int start = 0; start < length; start++) {
      for (int end = start + 1; end <= length; end++) {
        Arrays.fill(iScore[start][end], Float.NEGATIVE_INFINITY);
        /*
        if (op.doDep) {
          Arrays.fill(oScore[start][end], Float.NEGATIVE_INFINITY);
        }
        */
      }
    }
    for (int loc = 0; loc <= length; loc++) {
      Arrays.fill(narrowLExtent[loc], -1); // the rightmost left with state s ending at i that we can get is the beginning
      Arrays.fill(wideLExtent[loc], length + 1); // the leftmost left with state s ending at i that we can get is the end
    }
    for (int loc = 0; loc < length; loc++) {
      Arrays.fill(narrowRExtent[loc], length + 1); // the leftmost right with state s starting at i that we can get is the end
      Arrays.fill(wideRExtent[loc], -1); // the rightmost right with state s starting at i that we can get is the beginning
    }
    // int puncTag = stateIndex.indexOf(".");
    // boolean lastIsPunc = false;
    if (op.testOptions.verbose) {
      Timing.tick("done.");
      unkWords.append(" ]");
      System.out.println("Unknown words: " + unk + " " + unkWords);
      System.out.print("Starting filters...");
    }
    // do tags
    if (spillGuts) {
      tick("Tagging...");
    }
    for (int start = 0; start + 1 <= length; start++) {
      int word = words[start];
      int end = start + 1;
      Arrays.fill(tags[start], false);
      // setting the below line to false generates ALL tags in lexicon for
      // every word (though some may still be scored at -Inf).  Beware! XXXX
      for (Iterator<IntTaggedWord> taggingI = lex.ruleIteratorByWord(word, start, null); taggingI.hasNext();) {
        IntTaggedWord tagging = taggingI.next();
        int state = stateIndex.indexOf(tagIndex.get(tagging.tag));
        try {
          iScore[start][end][state] = lex.score(tagging, start, wordIndex.get(tagging.word), null);
          if (iScore[start][end][state] > Float.NEGATIVE_INFINITY) {
            narrowRExtent[start][state] = end;
            narrowLExtent[end][state] = start;
            wideRExtent[start][state] = end;
            wideLExtent[end][state] = start;
          }
        } catch (Exception e) {
          System.out.println("State: " + state + " tags " + tagIndex.get(tagging.tag));
        }
        int tag = tagging.tag;
        tags[start][tag] = true;
        if (dumpTagging) {
          System.out.println("Word " + start + " tagging " + tagging + " score " + iScore[start][start + 1][state] + " -- " + state + " = " + stateIndex.get(state));
        }
      }
      // null out any tags that violate prespecified basic tags
      if (!floodTags && sentence.get(start) instanceof HasTag && ((HasTag)sentence.get(start)).tag() != null &&  ((HasTag)sentence.get(start)).tag().length() > 0) {
        String tagStr = ((HasTag) sentence.get(start)).tag();
        TreebankLanguagePack tlp = op.langpack();
        for (int state = 0; state < numStates; state++) {
          if (!isTag[state]) {
            continue;
          }
          String stateStr = stateIndex.get(state);
          if (!tlp.basicCategory(stateStr).equals(tagStr)) {
            iScore[start][end][state] -= 1000.0f;
            //iScore[start][end][state] = Float.NEGATIVE_INFINITY;
            //narrowRExtent[start][state] = length+1;
            //narrowLExtent[end][state] = -1;
            //wideRExtent[start][state] = -1;
            //wideLExtent[end][state] = length+1;
          } else {
            //System.out.println("At "+start+" allowed "+stateStr+" for "+tagStr);
          }
        }
      }
      // if parse failed because of tag coverage
      if (floodTags && (!op.testOptions.noRecoveryTagging)) { // sends to recovery parse with more flexible tagging
        for (int state = 0; state < numStates; state++) {
          float iS = iScore[start][end][state];
          if (iS == Float.NEGATIVE_INFINITY && isTag[state]) {
            iScore[start][end][state] = -1000.0f;
            narrowRExtent[start][state] = end;
            narrowLExtent[end][state] = start;
            wideRExtent[start][state] = end;
            wideLExtent[end][state] = start;
          }
        }
      }
      if (spillGuts) {
        tick("Terminal Unary...");
      }
      for (int state = 0; state < numStates; state++) {
        float iS = iScore[start][end][state];
        if (iS == Float.NEGATIVE_INFINITY) {
          continue;
        }
        UnaryRule[] unaries = closedRulesUnderSumByChild(state);

        //        UnaryRule[] unaries2 = ug.closedRulesByChild(state);
        for (UnaryRule ur : unaries) {
          //UnaryRule ur2 = unaries2[r];
          if (ur.parent == ur.child) {
            continue;
          }
          int parentState = ur.parent;
          float pS = ur.score;
          float tot = iS + pS;
          iScore[start][end][parentState] = SloppyMath.logAdd(iScore[start][end][parentState], tot); // New!!
          //if (tot > iScore[start][end][parentState]) {
          //            iScore[start][end][parentState] = tot;
          if (tot > Float.NEGATIVE_INFINITY) {
            narrowRExtent[start][parentState] = end;
            narrowLExtent[end][parentState] = start;
            wideRExtent[start][parentState] = end;
            wideLExtent[end][parentState] = start;
          }
        }
      }
      if (spillGuts) {
        tick("Next word...");
      }
    }
    //if (op.testOptions.outsideFilter)
    // buildOFilter();
    if (op.testOptions.verbose) {
      Timing.tick("done.");
      System.err.print("Starting insides...");
    }
    // do the inside probabilities
    for (int diff = 2; diff <= length; diff++) {
      for (int start = 0; start + diff <= length; start++) {
        if (spillGuts) {
          tick("Binaries for " + diff + "...");
        }
        int end = start + diff;
        if (end == length && start != 0) {
          continue;
        }
        // do binary rules
        // do left restricted rules
        for (int leftState = 0; leftState < numStates; leftState++) {
          int narrowR = narrowRExtent[start][leftState];
          boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
          if (!iPossibleL) {
            continue;
          }
          BinaryRule[] leftRules = bg.splitRulesWithLC(leftState);
          for (BinaryRule r : leftRules) {
            int narrowL = narrowLExtent[end][r.rightChild];
            boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
            if (!iPossibleR) {
              continue;
            }
            int min1 = narrowR;
            int min2 = wideLExtent[end][r.rightChild];
            int min = (min1 > min2 ? min1 : min2);
            if (min > narrowL) // can this right constituent stretch far enough to reach the left constituent?
            {
              continue;
            }
            int max1 = wideRExtent[start][leftState];
            int max2 = narrowL;
            int max = (max1 < max2 ? max1 : max2);
            if (min > max) // can this left constituent stretch far enough to reach the right constituent?
            {
              continue;
            }
            float pS = r.score;
            int parentState = r.parent;
            float oldIScore = iScore[start][end][parentState];
            float curIScore = oldIScore;
            for (int split = min; split <= max; split++) { // find the split that can use this rule to make the max score
              float lS = iScore[start][split][leftState];
              if (lS == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float rS = iScore[split][end][r.rightChild];
              if (rS == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float tot = pS + lS + rS;
              if (tot > curIScore) {
                curIScore = tot;
              }
              iScore[start][end][parentState] = SloppyMath.logAdd(iScore[start][end][parentState], tot); // New!!
            }
            //	    if (curIScore > oldIScore) { // this way of making "parentState" is better than previous
            //	      iScore[start][end][parentState] = curIScore;
            if (oldIScore == Float.NEGATIVE_INFINITY) {
              if (start > narrowLExtent[end][parentState]) {
                narrowLExtent[end][parentState] = start;
                wideLExtent[end][parentState] = start;
              } else {
                if (start < wideLExtent[end][parentState]) {
                  wideLExtent[end][parentState] = start;
                }
              }
              if (end < narrowRExtent[start][parentState]) {
                narrowRExtent[start][parentState] = end;
                wideRExtent[start][parentState] = end;
              } else {
                if (end > wideRExtent[start][parentState]) {
                  wideRExtent[start][parentState] = end;
                }
              }
            }
            //	    }
          }
        }
        // do right restricted rules
        for (int rightState = 0; rightState < numStates; rightState++) {
          int narrowL = narrowLExtent[end][rightState];
          boolean iPossibleR = (narrowL > start);
          if (!iPossibleR) {
            continue;
          }
          BinaryRule[] rightRules = bg.splitRulesWithRC(rightState);
          for (BinaryRule r : rightRules) {
            int narrowR = narrowRExtent[start][r.leftChild];
            boolean iPossibleL = (narrowR <= narrowL);
            if (!iPossibleL) {
              continue;
            }
            int min1 = narrowR;
            int min2 = wideLExtent[end][rightState];
            int min = (min1 > min2 ? min1 : min2);
            if (min > narrowL) {
              continue;
            }
            int max1 = wideRExtent[start][r.leftChild];
            int max2 = narrowL;
            int max = (max1 < max2 ? max1 : max2);
            if (min > max) {
              continue;
            }
            float pS = r.score;
            int parentState = r.parent;
            float oldIScore = iScore[start][end][parentState];
            float curIScore = oldIScore;
            for (int split = min; split <= max; split++) {
              float lS = iScore[start][split][r.leftChild];
              if (lS == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float rS = iScore[split][end][rightState];
              if (rS == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float tot = pS + lS + rS;
              if (tot > curIScore) {
                curIScore = tot;
              }
              iScore[start][end][parentState] = SloppyMath.logAdd(iScore[start][end][parentState], tot); // New!!
            }
            //	    if (curIScore > oldIScore) {
            //	      iScore[start][end][parentState] = curIScore;
            if (oldIScore == Float.NEGATIVE_INFINITY) {
              if (start > narrowLExtent[end][parentState]) {
                narrowLExtent[end][parentState] = start;
                wideLExtent[end][parentState] = start;
              } else {
                if (start < wideLExtent[end][parentState]) {
                  wideLExtent[end][parentState] = start;
                }
              }
              if (end < narrowRExtent[start][parentState]) {
                narrowRExtent[start][parentState] = end;
                wideRExtent[start][parentState] = end;
              } else {
                if (end > wideRExtent[start][parentState]) {
                  wideRExtent[start][parentState] = end;
                }
              }
            }
            //	    }
          }
        }
        if (spillGuts) {
          tick("Unaries for " + diff + "...");
        }
        // do unary rules -- one could promote this loop and put start inside
        for (int state = 0; state < numStates; state++) {
          float iS = iScore[start][end][state];
          if (iS == Float.NEGATIVE_INFINITY) {
            continue;
          }
          UnaryRule[] unaries = closedRulesUnderSumByChild(state);
          //          UnaryRule[] unaries2 = ug.closedRulesByChild(state);
          for (UnaryRule ur : unaries) {
            //            UnaryRule ur2 = unaries2[r];
            if (ur.parent == ur.child) {
              continue;
            }
            int parentState = ur.parent;
            float pS = ur.score;
            float tot = iS + pS;
            float cur = iScore[start][end][parentState];
            iScore[start][end][parentState] = SloppyMath.logAdd(iScore[start][end][parentState], tot); // New!!
            //            if (tot > cur) {
            //              iScore[start][end][parentState] = tot;
            if (cur == Float.NEGATIVE_INFINITY) {
              if (start > narrowLExtent[end][parentState]) {
                narrowLExtent[end][parentState] = start;
                wideLExtent[end][parentState] = start;
              } else {
                if (start < wideLExtent[end][parentState]) {
                  wideLExtent[end][parentState] = start;
                }
              }
              if (end < narrowRExtent[start][parentState]) {
                narrowRExtent[start][parentState] = end;
                wideRExtent[start][parentState] = end;
              } else {
                if (end > wideRExtent[start][parentState]) {
                  wideRExtent[start][parentState] = end;
                }
              }
            }
            //            }
          }
        } // for unary rules
      } // for start
    } // for diff

    if (op.testOptions.verbose) {
      // insideTime += Timing.tick("done.");
      Timing.tick("done.");
      System.out.println("PCFG " + length + " words (incl. stop) iScore " + iScore[0][length][goal]);
    }
    bestScore = iScore[0][length][goal];
    boolean succeeded = hasParse();
    /*
    if (bestScore == Float.NEGATIVE_INFINITY && !floodTags) {
      floodTags = true; // sentence will try to reparse
      System.err.println("Trying recovery parse...");
      //System.out.println("Trying recovery parse...");
      return parse(sentence);
    }
    if (! Options.get().doDep)
      return succeeded;
    if (op.testOptions.verbose) {
      System.err.print("Starting outsides...");
    }
    // outside scores
    oScore[0][length][goal] = 0.0f;
    for (int diff = length; diff >= 1; diff--) {
      for (int start = 0; start+diff <= length; start++) {
        int end = start+diff;
        // do unaries
	for (int s = 0; s < numStates; s++) {
          float oS = oScore[start][end][s];
          if (oS == Float.NEGATIVE_INFINITY)
            continue;
	  UnaryRule[] rules = ug.closedRulesByParent(s);
	  for (int r=0; r<rules.length; r++) {
	    UnaryRule ur = rules[r];
	    float pS = (float) ur.score;
	    float tot = oS+pS;
	    if (tot > oScore[start][end][ur.child] &&
		iScore[start][end][ur.child] > Float.NEGATIVE_INFINITY)
	      oScore[start][end][ur.child] = tot;
          }
        }
        // do binaries
        for (int s = 0; s < numStates; s++) {
	  int min1 = narrowRExtent[start][s];
	  if (end < min1)
	    continue;
	  BinaryRule[] rules = bg.splitRulesWithLC(s);
          for (int r=0; r<rules.length; r++) {
            BinaryRule br = rules[r];
	    float oS = oScore[start][end][br.parent];
	    if (oS == Float.NEGATIVE_INFINITY)
	      continue;
	    int max1 = narrowLExtent[end][br.rightChild];
	    if (max1 < min1)
	      continue;
	    int min = min1;
	    int max = max1;
	    if (max-min > 2) {
	      int min2 = wideLExtent[end][br.rightChild];
	      min = (min1 > min2 ? min1 : min2);
	      if (max1 < min)
		continue;
	      int max2 = wideRExtent[start][br.leftChild];
	      max = (max1 < max2 ? max1 : max2);
	      if (max < min)
		continue;
	    }
            float pS = (float) br.score;
            for (int split = min; split <= max; split++) {
              float lS = iScore[start][split][br.leftChild];
              if (lS == Float.NEGATIVE_INFINITY)
		continue;
              float rS = iScore[split][end][br.rightChild];
	      if (rS == Float.NEGATIVE_INFINITY)
                continue;
              float totL = pS+rS+oS;
              if (totL > oScore[start][split][br.leftChild]) {
                oScore[start][split][br.leftChild] = totL;
              }
              float totR = pS+lS+oS;
              if (totR > oScore[split][end][br.rightChild]) {
                oScore[split][end][br.rightChild] = totR;
              }
            }
          }
        }
        for (int s = 0; s < numStates; s++) {
	  int max1 = narrowLExtent[end][s];
	  if (max1 < start)
	    continue;
	  BinaryRule[] rules = bg.splitRulesWithRC(s);
          for (int r=0; r<rules.length; r++) {
            BinaryRule br = rules[r];
	    float oS = oScore[start][end][br.parent];
	    if (oS == Float.NEGATIVE_INFINITY)
	      continue;
	    int min1 = narrowRExtent[start][br.leftChild];
	    if (max1 < min1)
	      continue;
	    int min = min1;
	    int max = max1;
	    if (max-min > 2) {
	      int min2 = wideLExtent[end][br.rightChild];
	      min = (min1 > min2 ? min1 : min2);
	      if (max1 < min)
		continue;
	      int max2 = wideRExtent[start][br.leftChild];
	      max = (max1 < max2 ? max1 : max2);
	      if (max < min)
		continue;
	    }
            float pS = (float) br.score;
            for (int split = min; split <= max; split++) {
              float lS = iScore[start][split][br.leftChild];
              if (lS == Float.NEGATIVE_INFINITY)
		continue;
              float rS = iScore[split][end][br.rightChild];
	      if (rS == Float.NEGATIVE_INFINITY)
                continue;
              float totL = pS+rS+oS;
              if (totL > oScore[start][split][br.leftChild]) {
                oScore[start][split][br.leftChild] = totL;
              }
              float totR = pS+lS+oS;
              if (totR > oScore[split][end][br.rightChild]) {
                oScore[split][end][br.rightChild] = totR;
              }
            }
          }
        }
      }
    }
    //System.out.println("State rate: "+((int)(1000*ohits/otries))/10.0);
    //System.out.println("Traversals: "+ohits);
    if (op.testOptions.verbose) {
      // outsideTime += Timing.tick("Done.");
      Timing.tick("Done.");
      System.out.print("Starting half-filters...");
    }
    for (int loc=0; loc<=length; loc++) {
      Arrays.fill(iPossibleByL[loc],false);
      Arrays.fill(iPossibleByR[loc],false);
      Arrays.fill(oPossibleByL[loc],false);
      Arrays.fill(oPossibleByR[loc],false);
    }
    for (int start=0; start<length; start++) {
      for (int end=start+1; end<=length; end++) {
        for (int state=0; state<numStates; state++) {
          if (iScore[start][end][state] > Float.NEGATIVE_INFINITY &&
              oScore[start][end][state] > Float.NEGATIVE_INFINITY) {
            iPossibleByL[start][state] = true;
            iPossibleByR[end][state] = true;
            oPossibleByL[start][state] = true;
            oPossibleByR[end][state] = true;
          }
        }
      }
    }
    if (op.testOptions.verbose) {
      Timing.tick("done.");
    }
    if (false) {
      long iNZ = 0;
      long oNZ = 0;
      long bNZ = 0;
      long tot = 0;
      for (int start=0; start<length; start++) {
        for (int end=start+1; end<=length; end++) {
          for (int s=0; s<numStates; s++) {
            tot++;
            if (iScore[start][end][s] != Double.NEGATIVE_INFINITY)
              iNZ++;
            if (oScore[start][end][s] != Double.NEGATIVE_INFINITY)
              oNZ++;
            if (oScore[start][end][s] != Double.NEGATIVE_INFINITY &&
                iScore[start][end][s] != Double.NEGATIVE_INFINITY)
              bNZ++;
          }
        }
      }
      System.out.println("Zero Saturation (i): "+((int)(1000*iNZ/tot))/10.0);
      System.out.println("Zero Saturation (o): "+((int)(1000*oNZ/tot))/10.0);
      System.out.println("Zero Saturation (b): "+((int)(1000*bNZ/tot))/10.0);
      Timing.tick("Done with zero scan.");
    }
    */
    return succeeded;
  }

  /**
   * Returns the total probability of the last sentence parsed
   * parsing to the goalState.
   */
  public float getSentenceProbability(String goalState) {
    int goal = stateIndex.indexOf(goalState);
    //    for (int i=0; i<iScore[0][length].length; i++) {
    //      if (iScore[0][length][i] > Double.NEGATIVE_INFINITY) {
    //        System.out.println("score for " + stateIndex.get(i) + " is " + iScore[0][length][i]);
    //      }
    //    }
    return iScore[0][length][goal];
  }

  /**
   * Returns the total probability of the last sentence parsed
   * parsing to the default goalState.
   */
  public float getSentenceProbability() {
    return bestScore;
  }

  /**
   * Creates new parser from serializedParser in file located at path.
   */
  public SentenceProbabilityParser(String path) {
    this(prepareParserData(path));
  }

  /**
   * Creates a new parser from the given ParserData. Assumes the Index
   * and Options have been set up.
   * @param pd The parser data to build a parser from
   */
  public SentenceProbabilityParser(LexicalizedParser pd) {
    super(pd.bg, pd.ug, pd.lex, pd.getOp(), pd.stateIndex, pd.wordIndex, pd.tagIndex);
    closeRulesUnderSum();
  }


  /**
   * Return a TreePrint for formatting parsed output trees.
   */
  public TreePrint getTreePrint() {
    return op.testOptions.treePrint(op.tlpParams);
  }

  public void setOptionFlags(String[] flags) {
    op.setOptions(flags);
  }


  private static LexicalizedParser prepareParserData(String path) {
    LexicalizedParser pd;
    Options op = new Options();
    pd = LexicalizedParser.getParserFromFile(path, op);
    pd.bg.splitRules();
    System.out.println("got parser data");
    return pd;
  }


  /**
   * For testing. Usage:<blockquote><code>
   * java SentenceProbabilityParser serializedPCFGParser testToParse
   * </code></blockquote>
   * Needs approximately 500m of memory to run, due to the matrix inversion.
   */
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("usage: SentenceProbabilityParser serializedPCFG [testSentences [numGenerate]]");
    }
    // make the parser
    SentenceProbabilityParser spparser = new SentenceProbabilityParser(args[0]);
    int numGenerate = 2;
    if (args.length > 2) {
      numGenerate = Integer.parseInt(args[2]);
    }

    if (args.length > 1) {
      // now parse the sentences
      BufferedReader trainFile = new BufferedReader(new FileReader(args[1]));
      String sentenceLine;

      List<Sampler<Tree>> samplers = new ArrayList<Sampler<Tree>>();

      while ((sentenceLine = trainFile.readLine()) != null) {
        List allWordsCopy = PTBTokenizer.newPTBTokenizer(new StringReader(sentenceLine)).tokenize();

        List allWords = new ArrayList(allWordsCopy);
        //Sentence s = new Sentence(allWords);
        //s.add(Lexicon.BOUNDARY);
        System.err.println("Parsing [len. " + allWords.size() + "]: " + (Sentence.listToString(allWords)));
        allWords.add(Lexicon.BOUNDARY);
        try {
          boolean parsed = spparser.parse(allWords);
          System.out.println("Parsed: " + parsed);
          float score = spparser.getSentenceProbability();
          System.out.println("Score: " + score);

          Sampler<Tree> sampler = spparser.getSampler(allWords);
          samplers.add(sampler);
        } catch (UnsupportedOperationException uoe) {
          System.err.println("Whoops, sentence too long.");
        }
      }

      for (int i = 0; i < numGenerate; i++) {
        for (Sampler<Tree> s : samplers) {
          s.drawSample().pennPrint();
        }
      }
    } else {
      List<Sampler<Tree>> samplers = new ArrayList<Sampler<Tree>>();
      List<CoreLabel> taggedSent = new ArrayList<CoreLabel>();
      CoreLabel fl = new CoreLabel();
      fl.setWord("Phil");
      fl.setTag("NNP");
      taggedSent.add(fl);
      fl = new CoreLabel();
      fl.setWord("Wittman");
      // fl.setTag("");
      taggedSent.add(fl);
      fl = new CoreLabel();
      fl.setWord("works");
//      fl.setTag("VBZ");
      taggedSent.add(fl);
      fl = new CoreLabel();
      fl.setWord("for");
//      fl.setTag("IN");
      taggedSent.add(fl);
      fl = new CoreLabel();
      fl.setWord("Tom");
//      fl.setTag("NN");
      taggedSent.add(fl);
      fl = new CoreLabel();
      fl.setWord("Benson");
//      fl.setTag("NNP");
      taggedSent.add(fl);
      fl = new CoreLabel();
      fl.setWord(".");
      fl.setTag(".");
      taggedSent.add(fl);

      boolean parsed = spparser.parse(taggedSent);
      System.out.println("Parsed: " + parsed);
      float score = spparser.getSentenceProbability();
      System.out.println("Score: " + score);

      Sampler<Tree> sampler = spparser.getSampler(taggedSent);
      samplers.add(sampler);

      for (int i = 0; i < numGenerate; i++) {
        for (Sampler<Tree> s : samplers) {
          s.drawSample().pennPrint();
        }
      }
    }
  } // end main()

} // end class SentenceProbabilityParser
