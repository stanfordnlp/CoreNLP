package edu.stanford.nlp.parser.dvparser;

import junit.framework.TestCase;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.TestPaths;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class DVParserCostAndGradientITest extends TestCase {
  static LexicalizedParser parser = null;
  static Options op = null;

  public void setUp() throws Exception {
    synchronized(DVParserCostAndGradientITest.class) {
      if (parser == null) {
        parser = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
        op = parser.getOp();
	op.lexOptions.wordVectorFile = 
	  String.format("%s/deeplearning/datasets/turian/embeddings-scaled.EMBEDDING_SIZE=25.txt", 
			TestPaths.testHome());
        op.lexOptions.numHid = 3;
      }
    }
  }

  public void testGradientCheck(String correct, String hypothesis, int expectedBinary, int expectedUnary) {
    DVModel dvModel = new DVModel(op, parser.stateIndex, parser.ug, parser.bg);

    Tree correctTree = Tree.valueOf(correct);
    List<Tree> trainingBatch = new ArrayList<Tree>();
    trainingBatch.add(correctTree);

    Tree hypothesisTree = Tree.valueOf(hypothesis);
    List<Tree> hypotheses = new ArrayList<Tree>();
    hypotheses.add(hypothesisTree);
    IdentityHashMap<Tree, List<Tree>> topParses = new IdentityHashMap<Tree, List<Tree>>();
    topParses.put(correctTree, hypotheses);

    List<Tree> allTrees = new ArrayList<Tree>();
    allTrees.addAll(trainingBatch);
    allTrees.addAll(hypotheses);
    
    dvModel.filterRulesForBatch(allTrees);

    assertEquals(expectedBinary, dvModel.binaryTransform.size());
    assertEquals(expectedBinary, dvModel.binaryScore.size());
    assertEquals(expectedUnary, dvModel.unaryTransform.size());
    assertEquals(expectedUnary, dvModel.unaryScore.size());
    
    DVParserCostAndGradient gcFunc = new DVParserCostAndGradient(trainingBatch, topParses, dvModel, op);
    assertTrue(gcFunc.gradientCheck(dvModel.totalParamSize(), 0, dvModel.paramsToVector()));
  }

  public void testBinaryNoRecursion() {
    testGradientCheck("(NP (JJ blue) (NP dog))", "(NP (JJ blue) (VP dog))", 2, 0);
  }

  public void testUnaryNoRecursion() {
    testGradientCheck("(VP (VBD running))", "(VP (VBG running))", 0, 2);
  }

  public void testUnaryRecursionBottom() {
    testGradientCheck("(NP (VP (VBD running)))", "(NP (VP (VBG running)))", 0, 3);
  }

  public void testUnaryRecursionTop() {
    testGradientCheck("(NP (VP (VBD running)))", "(NP (ADJP (VBD running)))", 0, 3);
  }

  public void testUnaryRecursionBoth() {
    testGradientCheck("(NP (VP (VBG running)))", "(NP (ADJP (JJ running)))", 0, 4);
  }

  public void testBinaryRecursionBottom() {
    testGradientCheck("(NP (JJ blue) (NP (JJ grey) (NP dog)))", "(NP (JJ blue) (NP (JJ grey) (VP dog)))", 2, 0);
  }

  public void testBinaryUnary() {
    testGradientCheck("(NP (JJ blue) (NP (NN dog)))", "(NP (JJ blue) (VP (VBD dog)))", 2, 2);
  }

  public void testUnaryBinaryUnary() {
    testGradientCheck("(VP (NP (JJ blue) (NP (NN dog))))", "(NP (VP (JJ blue) (VP (VBD dog))))", 2, 4);
  }

}
