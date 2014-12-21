/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.stanford.nlp.sentiment;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import java.util.Iterator;
import java.util.List;

/**
 * Evaluate predictions made outside of the RNTN.
 * 
 * @author Michael Haas <haas@cl.uni-heidelberg.de>
 */
public class ExternalEvaluate extends AbstractEvaluate {

    List<Tree> predicted;

    public ExternalEvaluate(RNNOptions op, List<Tree> predictedTrees) {
        super(op);
        this.predicted = predictedTrees;
    }

    @Override
    public void populatePredictedLabels(List<Tree> trees) {
        if (trees.size() != this.predicted.size()) {
            throw new IllegalArgumentException("Number of gold and predicted trees not equal!");
        }
        for (int i = 0; i < trees.size(); i++) {
            Iterator<Tree> goldTree = trees.get(i).iterator();
            Iterator<Tree> predictedTree = this.predicted.get(i).iterator();
            while (goldTree.hasNext() || predictedTree.hasNext()) {
                Tree goldNode = goldTree.next();
                Tree predictedNode = predictedTree.next();
                if (goldNode == null || predictedNode == null) {
                    throw new IllegalArgumentException("Trees not of equal length");
                }
                if (goldNode.isLeaf()) {
                    continue;
                }
                CoreLabel label = (CoreLabel) goldNode.label();
                label.set(RNNCoreAnnotations.PredictedClass.class,
                        RNNCoreAnnotations.getPredictedClass(predictedNode));
            }
        }
    }

    /**
     * Expected arguments are <code> -gold gold -predicted predicted </code>
     * <br>
     *
     * For example <br>
     * <code>
     *  java edu.stanford.nlp.sentiment.ExternalEvaluate
     *   annotatedTrees.txt
     *   predictedTrees.txt
     * </code>
     */
    public static void main(String[] args) {
        RNNOptions curOptions = new RNNOptions();
        String goldPath = null;
        String predictedPath = null;
        for (int argIndex = 0; argIndex < args.length;) {
            if (args[argIndex].equalsIgnoreCase("-gold")) {
                goldPath = args[argIndex + 1];
                argIndex += 2;
            } else if (args[argIndex].equalsIgnoreCase("-predicted")) {
                predictedPath = args[argIndex + 1];
                argIndex += 2;
            } else {
                int newArgIndex = curOptions.setOption(args, argIndex);
                if (newArgIndex == argIndex) {
                    throw new IllegalArgumentException("Unknown argument " + args[argIndex]);
                }
                argIndex = newArgIndex;
            }
        }
        if (goldPath == null) {
            System.err.println("goldPath not set. Exit.");
            System.exit(-1);

        }
        if (predictedPath == null) {
            System.err.println("predictedPath not set. Exit.");
            System.exit(-1);
        }
        // filterUnknown not supported because I'd need to know which sentences
        // are removed to remove them from predicted
        List<Tree> goldTrees = SentimentUtils.readTreesWithGoldLabels(goldPath);
        List<Tree> predictedTrees = SentimentUtils.readTreesWithPredictedLabels(predictedPath);
        ExternalEvaluate evaluator = new ExternalEvaluate(curOptions, predictedTrees);
        evaluator.eval(goldTrees);
        evaluator.printSummary();

    }

}
