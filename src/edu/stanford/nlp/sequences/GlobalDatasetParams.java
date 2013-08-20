package edu.stanford.nlp.sequences;
import java.util.*;
import java.io.Serializable;

public class GlobalDatasetParams implements Serializable {
		/**
   * 
   */
  private static final long serialVersionUID = -5292341676650354270L;
    DatasetMetaInfo metaInfo;
		SeqClassifierFlags flags;
		Map<LabeledClique, int[]> timitFeatureMap;
		double[] Ehat;
}

