package edu.stanford.nlp.trees;

import java.io.IOException;
import java.util.List;

public interface DependencyReader {
  public List<GrammaticalStructure> readDependencies(String fileName) throws IOException;
}
