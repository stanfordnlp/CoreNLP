package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.StringUtils;

public abstract class AbstractPronounResolver {

  private static Pattern WORD_POS_IDX_PATTERN = Pattern.compile("^(?<word>.*)_(?<tag>[^_]+)_(?<idx>[0-9]+)$");


  protected abstract HashMap<Integer, Integer> resolvePronouns(List<CoreLabel> tokens);

  protected abstract HashMap<Integer, Integer> resolvePronouns(SemanticGraph sg);


  public double score(TestExample gold, HashMap<Integer, Integer> predicted) {

    double s = 0.0;
    double c = 0.0;

    for (CoreLabel pron : gold.pronouns) {
      c = c + 1;
      int pronIdx = pron.index();
      if (gold.corefPairs.containsKey(pronIdx) && predicted.containsKey(pronIdx)) {
        if (gold.corefPairs.get(pronIdx) == predicted.get(pronIdx)) {
          s = s + 1;
        }
      } else if ( ! gold.corefPairs.containsKey(pronIdx) && ! predicted.containsKey(pronIdx)) {
        s = s + 1;
      }
    }

    return c > 0.0 ? s / c : 1.0;
  }


  public void run(String[] args) throws IOException {


    List<TestExample> examples = new LinkedList<TestExample>();

    BufferedReader br = IOUtils.readerFromString(args[0]);
    String line;
    int count = 0;
    double score = 0.0;
    while ((line = br.readLine()) != null) {
      TestExample ex = new TestExample(line);
      examples.add(ex);
      System.err.println(ex);
      HashMap<Integer, Integer> pronouns = resolvePronouns(ex.tokens);
      double sentScore = this.score(ex, pronouns);
      count++;
      score += sentScore;
      System.out.println(sentScore);
    }

    System.out.println("Macro-averaged accuracy:");
    System.out.println(score / count);

    br.close();

  }



  protected static class TestExample {

    private List<CoreLabel> tokens;
    private List<CoreLabel> pronouns;
    private HashMap<Integer, Integer> corefPairs;


    public TestExample(String line) {

      this.corefPairs = new HashMap<Integer,Integer>(2);

      String[] parts = line.split("\\t");

      String[] taggedTokens = parts[0].split("\\s+");

      tokens = new ArrayList<CoreLabel>(taggedTokens.length);
      pronouns = new ArrayList<CoreLabel>(1);

      for (String taggedToken : taggedTokens) {
        Matcher matcher =  WORD_POS_IDX_PATTERN.matcher(taggedToken);
        while (matcher.find()) {
          CoreLabel cl = new CoreLabel();
          cl.setValue(matcher.group("word"));
          cl.setWord(matcher.group("word"));
          cl.setTag(matcher.group("tag"));
          cl.setIndex(Integer.parseInt(matcher.group("idx")));
          tokens.add(cl);

          if (cl.tag().startsWith("PRP")) {
            pronouns.add(cl);
          }

        }
      }

      if (parts.length > 1) {
        String[] cPairs = parts[1].split("\\s+");
        for (String cPair : cPairs) {
          if (cPair.indexOf("-") != -1) {
            String[] cPairParts = cPair.split("-");
            Integer pronIdx = Integer.parseInt(cPairParts[0]);
            Integer mentionIdx = Integer.parseInt(cPairParts[1]);
            corefPairs.put(pronIdx, mentionIdx);
          }
        }
      }
    }

    @Override
    public String toString() {
      return StringUtils.join(this.tokens) + " " + this.corefPairs + " " + StringUtils.join(this.pronouns, ", ");
    }

  }

}
