package edu.stanford.nlp.international.french;

import edu.stanford.nlp.pipeline.*;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * French tokenizer test
 */

public class FrenchTokenizerAnnotatorITest extends TestCase {


  private static List<String> frenchSentences = Arrays.asList(
      "Je sens qu'entre ça et les films de médecins et scientifiques fous que nous avons déjà vus, nous pourrions " +
          "emprunter un autre chemin pour l'origine.",
      "On pourra toujours parler à propos d'Averroès de \"décentrement du Sujet\".",
      "Consacrant, lui, l'essentiel de son temps et de son énérgie exclusivement aux grands chantiers économiques " +
          "destinés à procurer du travail aux jeunes générations.",
      "Plus, l'AQMI tente de nouer des rapports avec les mouvements subversifs dans le delta du Nigéria, autant " +
          "qu'avec des sectes d'inspiration religieuse au nord du pays.",
      "Après avoir examiné l'état des relations bilatérales, les deux chefs d'Etat ont réitéré leur volonté d'œuvrer " +
          "à leur renforcement et à leur diversification."
  );

  private static List<List<String>> frenchSentenceTokenLists = Arrays.asList(
      Arrays.asList("Je", "sens", "qu'", "entre", "ça", "et", "les", "films", "de", "médecins", "et",
          "scientifiques", "fous", "que", "nous", "avons", "déjà", "vus", ",", "nous", "pourrions", "emprunter",
          "un", "autre", "chemin", "pour", "l'", "origine", "."),
      Arrays.asList("On", "pourra", "toujours", "parler", "à", "propos", "d'", "Averroès", "de", "\"",
          "décentrement", "de", "le", "Sujet", "\"", "."),
      Arrays.asList("Consacrant", ",", "lui", ",", "l'", "essentiel", "de", "son", "temps", "et", "de", "son",
          "énérgie", "exclusivement", "à", "les", "grands", "chantiers", "économiques", "destinés", "à",
          "procurer", "de", "le", "travail", "à", "les", "jeunes", "générations", "."),
      Arrays.asList("Plus", ",", "l'", "AQMI", "tente", "de", "nouer", "des", "rapports", "avec", "les",
          "mouvements", "subversifs", "dans", "le", "delta", "de", "le", "Nigéria", ",", "autant", "qu'", "avec",
          "des", "sectes", "d'", "inspiration", "religieuse", "à", "le", "nord", "de", "le", "pays", "."),
      Arrays.asList("Après", "avoir", "examiné", "l'", "état", "des", "relations", "bilatérales", ",", "les",
          "deux", "chefs", "d'", "Etat", "ont", "réitéré", "leur", "volonté", "d'", "œuvrer", "à", "leur",
          "renforcement", "et", "à", "leur", "diversification", ".")
  );

  public void testFrench() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize");
    props.setProperty("ssplit.eolonly", "true");
    props.setProperty("tokenize.language", "fr");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    int sentNum = 0;
    for (String exampleSentence : frenchSentences) {
      List<String> exampleSentenceTokens = frenchSentenceTokenLists.get(sentNum);
      CoreDocument exampleSentenceCoreDocument = new CoreDocument(exampleSentence);
      pipeline.annotate(exampleSentenceCoreDocument);
      for (int i = 0; i < exampleSentenceTokens.size(); i++) {
        assertEquals(exampleSentenceTokens.get(i),
            exampleSentenceCoreDocument.tokens().get(i).word());
      }
      sentNum++;
    }
  }
}