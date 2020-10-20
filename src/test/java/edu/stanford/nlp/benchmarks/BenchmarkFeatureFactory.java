package edu.stanford.nlp.benchmarks;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.util.PaddedList;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by keenon on 6/19/15.
 *
 * Simple feature factory to enable benchmarking of the CRF classifier as it currently is.
 */
public class BenchmarkFeatureFactory extends FeatureFactory<CoreLabel> {
    @Override
    public Collection<String> getCliqueFeatures(PaddedList<CoreLabel> info, int position, Clique clique) {
        Set<String> features = new HashSet<>();
        for (CoreLabel l : info) {
            for (int i = 0; i < 10; i++) {
                features.add("feat"+i+":"+l.word());
            }
        }
        return features;
    }
}
