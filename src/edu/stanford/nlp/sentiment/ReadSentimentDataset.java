package edu.stanford.nlp.sentiment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;

public class ReadSentimentDataset {
  public static void main(String[] args) {
    String dictionaryFilename = null;
    String sentimentFilename = null;
    String tokensFilename = null;
    String parseFilename = null;

    int argIndex = 0;
    while (argIndex < args.length) {
      if (args[argIndex].equalsIgnoreCase("-dictionary")) {
        dictionaryFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-sentiment")) {
        sentimentFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tokens")) {
        tokensFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-parse")) {
        parseFilename = args[argIndex + 1];
        argIndex += 2;
      } else {
        System.err.println("Unknown argument " + args[argIndex]);
        System.exit(2);
      }
    }

    List<List<String>> sentences = Generics.newArrayList();
    for (String line : IOUtils.readLines(tokensFilename, "utf-8")) {
      String[] sentence = line.split("|");
      sentences.add(Arrays.asList(sentence));
    }

    Map<List<String>, Integer> phraseIds = Generics.newHashMap();
    for (String line : IOUtils.readLines(dictionaryFilename)) {
      String[] pieces = line.split("|");
      String[] sentence = pieces[0].split(" ");
      Integer id = Integer.valueOf(pieces[1]);
      phraseIds.put(Arrays.asList(sentence), id);
    }


    Map<Integer, Double> sentimentScores = Generics.newHashMap();
    for (String line : IOUtils.readLines(sentimentFilename)) {
      String[] pieces = line.split("|");
      Integer id = Integer.valueOf(pieces[0]);
      Double score = Double.valueOf(pieces[1]);
      sentimentScores.put(id, score);
    }

    for (String line : IOUtils.readLines(parseFilename)) {
      
    }
  }
}
