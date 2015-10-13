package edu.stanford.nlp.scoref;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;

public class FeatureExtractorRunner implements DocumentProcessor {
  private final FeatureExtractor extractor;
  private final Compressor<String> compressor;

  private final Map<Integer, Map<Pair<Integer, Integer>, Boolean>> dataset;
  private final List<DocumentExamples> documents;

  public FeatureExtractorRunner(Properties props, Dictionaries dictionaries) {
    documents = new ArrayList<>();
    compressor = new Compressor<>();
    extractor = new FeatureExtractor(props, dictionaries, compressor);
    try {
      dataset = IOUtils.readObjectFromFile(StatisticalCorefTrainer.datasetFile);
    } catch(Exception e) {
      throw new RuntimeException("Error initializing FeatureExtractorRunner", e);
    }
  }

  @Override
  public void process(int id, Document document) {
    if (dataset.containsKey(id)) {
      documents.add(extractor.extract(id, document, dataset.get(id)));
    }
  }

  @Override
  public void finish() throws Exception {
    IOUtils.writeObjectToFile(documents, StatisticalCorefTrainer.extractedFeaturesFile);
    IOUtils.writeObjectToFile(compressor, StatisticalCorefTrainer.compressorFile);
  }
}
