package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.Function;

import java.util.ArrayList;
import java.util.List;

/**
 * The AnCora Spanish corpus contains thousands of multi-word
 * expressions. In CoreNLP we don't deal with this MWEs and instead
 * preprocess our training data so that they are split into their
 * constituent token.
 *
 * The remaining problem is to determine how to amend the
 * dependency-parsed sentences containing these MWEs. This class
 * is a classifier which determines how the non-head tokens of a
 * multi-word expression should be labeled in a corrected dependency
 * treebank.
 *
 * @author Jon Gauthier
 */
public class HamleDTMultiWordClassifier {

  private static final List<Function<String, List<String>>> featureFunctions = new
    ArrayList<Function<String, List<String>>>() {{

    }};

  public Datum<String, String> makeDatumFromLine(String line) {
    String[] fields = line.split("\t");
    String gold = fields[0];
    String expression = fields[1];

    List<String> features = new ArrayList<String>();

    // TODO add features

    return new BasicDatum<String, String>(features, gold);
  }

}
