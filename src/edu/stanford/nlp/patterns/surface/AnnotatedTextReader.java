package edu.stanford.nlp.patterns.surface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TypesafeMap;

/**
 * CanNOT handle overlapping labeled text (that is one token cannot belong to
 * multiple labels)! Note that there has to be spaces around the tags <label>
 * and </label> for the reader to work correctly!
 * 
 * @author Sonal Gupta (sonalg@stanford.edu)
 * 
 */
public class AnnotatedTextReader {
  public static List<CoreMap> parseFile(
      BufferedReader reader,
      Set<String> categoriesAllowed,
      Map<String, Class<? extends TypesafeMap.Key<String>>> setClassForTheseLabels,
      boolean setGoldClass, String sentIDprefix)
      throws IOException {

    Pattern startingLabelToken = Pattern.compile("<("
        + StringUtils.join(categoriesAllowed, "|") + ")>");
    Pattern endLabelToken = Pattern.compile("</("
        + StringUtils.join(categoriesAllowed, "|") + ")>");
    String backgroundSymbol = "O";

    List<CoreMap> sentences = new ArrayList<CoreMap>();
    int lineNum = -1;
    String l = null;

    while ((l = reader.readLine()) != null) {
      lineNum++;
      String[] t = l.split("\t", 2);
      String id = null;
      String text = null;
      if (t.length == 2) {
        id = t[0];
        text = t[1];
      } else if (t.length == 1) {
        text = t[0];
        id = String.valueOf(lineNum);
      }
      id = sentIDprefix + id;
      DocumentPreprocessor dp = new DocumentPreprocessor(new StringReader(text));
      PTBTokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizerFactory
          .newCoreLabelTokenizerFactory("ptb3Escaping=false,normalizeParentheses=false,escapeForwardSlashAsterisk=false");
      dp.setTokenizerFactory(tokenizerFactory);

      String label = backgroundSymbol;
      int sentNum = -1;

      for (List<HasWord> sentence : dp) {
        sentNum++;
        String sentStr = "";
        List<CoreLabel> sent = new ArrayList<CoreLabel>();
        for (HasWord tokw : sentence) {
          String tok = tokw.word();
          Matcher startingMatcher = startingLabelToken.matcher(tok);
          Matcher endMatcher = endLabelToken.matcher(tok);
          if (startingMatcher.matches()) {
            label = startingMatcher.group(1);
          } else if (endMatcher.matches()) {
            label = backgroundSymbol;
          } else {

            CoreLabel c = new CoreLabel();

            List<String> toks = new ArrayList<String>();

            toks.add(tok);

            for (String toksplit : toks) {

              sentStr += " " + toksplit;

              c.setWord(toksplit);
              c.setLemma(toksplit);
              c.setValue(toksplit);
              c.set(CoreAnnotations.TextAnnotation.class, toksplit);
              c.set(CoreAnnotations.OriginalTextAnnotation.class, tok);

              if (setGoldClass){
                 
                c.set(CoreAnnotations.GoldAnswerAnnotation.class, label);
              }
              
              if (setClassForTheseLabels != null
                  && setClassForTheseLabels.containsKey(label))
                c.set(setClassForTheseLabels.get(label), label);

              sent.add(c);
            }
          }
        }
        CoreMap sentcm = new ArrayCoreMap();
        sentcm.set(CoreAnnotations.TextAnnotation.class, sentStr.trim());
        sentcm.set(CoreAnnotations.TokensAnnotation.class, sent);
        sentcm.set(CoreAnnotations.DocIDAnnotation.class, id + "-" + sentNum);
        sentences.add(sentcm);
      }
    }
    return sentences;
  }
}
