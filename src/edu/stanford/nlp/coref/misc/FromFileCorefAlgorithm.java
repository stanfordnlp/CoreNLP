package edu.stanford.nlp.coref.misc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.coref.CorefAlgorithm;
import edu.stanford.nlp.coref.CorefSystem;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.DocumentMaker;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * Class for loading coreference links from a file and then performing them on CoNLL data.
 * Each line of the file should contain a document id followed by a tab followed by a
 * space-separated list of pairs of mention ids, separated by commas, to be merged
 * (e.g., 0\t2,3 2,5 4,9).
 * @author Kevin Clark
 */
public class FromFileCorefAlgorithm implements CorefAlgorithm {
  private final Map<Integer, List<Pair<Integer, Integer>>> toMerge = new HashMap<>();
  private int currentDocId = 0;

  public FromFileCorefAlgorithm(String savedLinkPath) {
    try(BufferedReader br = new BufferedReader(new FileReader(savedLinkPath))) {
      br.lines().forEach(line -> {
        String[] split = line.split("\t");
        int did = Integer.valueOf(split[0]);

        List<Pair<Integer, Integer>> docMerges = toMerge.get(did);
        if (docMerges == null) {
          docMerges = new ArrayList<>();
          toMerge.put(did, docMerges);
        }

        if (split.length > 1) {
          String[] pairs = split[1].split(" ");
          for (String pair : pairs) {
            String[] ms = pair.split(",");
            docMerges.add(new Pair<>(Integer.valueOf(ms[0]), Integer.valueOf(ms[1])));
          }
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Error reading saved links", e);
    }
  }

  @Override
  public void runCoref(Document document) {
    if (toMerge.containsKey(currentDocId)) {
      for (Pair<Integer, Integer> pair : toMerge.get(currentDocId)) {
        CorefUtils.mergeCoreferenceClusters(pair, document);
      }
    }
    currentDocId += 1;
  }

  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(new String[] {"-props", args[0]});
    new CorefSystem(new DocumentMaker(props, new Dictionaries(props)),
        new FromFileCorefAlgorithm(args[1]), true, false).runOnConll(props);
  }
}
