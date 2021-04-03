package edu.stanford.nlp.scenegraph;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageAttribute;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageObject;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRelationship;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

public class SceneGraphImageCleaner {


  private static String FINAL_PUNCT_REGEX = "\\.+$";
  private static String INITIAL_DET_REGEX = "^(an?|the) ";
  private static String FINAL_DET_REGEX = " (an?|the)$";
  private static String TRAILING_NUMBER_REGEX = " [0-9]+$";


  private static Set<String> ALL_ATTRIBUTES = Generics.newHashSet();

  private static StanfordCoreNLP pipeline;

  private static StanfordCoreNLP tokenizerPipeline;


  private static StanfordCoreNLP getPipeline() {
    if (pipeline == null) {
      Properties props = new Properties();
      props.put("annotators", "tokenize,ssplit,pos,lemma,ner");
      //props.put("tokenize.whitespace", "true");
      props.put("ssplit.eolonly", "true");
      pipeline = new StanfordCoreNLP(props);
    }
    return pipeline;
  }

  private static StanfordCoreNLP getTokenizerPipeline() {
    if (tokenizerPipeline == null) {
      Properties props = new Properties();
      props.put("annotators", "tokenize,ssplit,pos,lemma,ner");
      props.put("ssplit.eolonly", "true");
      tokenizerPipeline = new StanfordCoreNLP(props);
    }
    return tokenizerPipeline;
  }


  public static void extractAllAttributes(List<SceneGraphImage> images) {
    for (SceneGraphImage img : images) {
      for (SceneGraphImageAttribute attr : img.attributes) {
        ALL_ATTRIBUTES.add(attr.attributeLemmaGloss());
      }
    }
  }


  public void cleanupImage(SceneGraphImage img) {

    /* Remove punctuation at the end of predicates and objects and
     * remove determiners before objects. */
    //removePunctuationAndDeterminers(img);

    lemmatize(img);

  }


  private String removeFinalPunctuation(String str) {
    return str.replaceAll(FINAL_PUNCT_REGEX, "");
  }

  private String removeDeterminersAndNumbers(String str) {
    str =  str.replaceAll(INITIAL_DET_REGEX, "");
    str =  str.replaceAll(FINAL_DET_REGEX, "");
    return str.replaceAll(TRAILING_NUMBER_REGEX, "");
  }

  private String lemmaGloss(List<CoreLabel> lst) {
    return StringUtils.join(lst.stream().map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }


  /**
   * Splits attributes of the form X and Y or X, Y and Z if
   * all elements were observed somewhere in some other image.
   */
  public void splitAttributeConjunctions(SceneGraphImage img) {
    if (ALL_ATTRIBUTES.isEmpty()) {
      System.err.println("WARNING: List of attributes is empty! Won't split any conjunctions.");
      return;
    }

    List<SceneGraphImageAttribute> newAttrs = Generics.newLinkedList();
    for (SceneGraphImageAttribute attr : img.attributes) {
      if (SceneGraphImageUtils.containsLemma(attr.attributeGloss, "and")
          || SceneGraphImageUtils.containsLemma(attr.attributeGloss, "&")) {

        List<List<CoreLabel>> parts = Generics.newLinkedList();
        boolean shouldSplit = true;
        List<CoreLabel> current = Generics.newLinkedList();

        for (int i = 0, sz = attr.attributeGloss.size();  i <= sz; i++) {
          CoreLabel word = i < sz ? attr.attributeGloss.get(i) : null;
          if (word == null
              || word.lemma().equals("and")
              || word.lemma().equals(",")
              || word.lemma().equals("&")) {

            if (current.isEmpty()) {
              continue;
            }
            if ( ! ALL_ATTRIBUTES.contains(lemmaGloss(current))) {
              shouldSplit = false;
              break;
            }
            parts.add(current);
            current = Generics.newLinkedList();
          } else {
            current.add(word);
          }
        }
        if (shouldSplit && parts.size() > 0) {
         attr.attributeGloss = parts.get(0);
         attr.attribute = attr.attributeGloss();
         attr.object = attr.attributeGloss();
         attr.text[2] = attr.attributeGloss();
         for (int i = 1, sz = parts.size(); i < sz; i++) {
           SceneGraphImageAttribute attr2 = attr.clone();
           attr2.attributeGloss = parts.get(i);
           attr2.attribute = attr2.attributeGloss();
           attr2.object = attr2.attributeGloss();
           attr2.text[2] = attr2.attributeGloss();
           newAttrs.add(attr2);
          }
        }
      }
    }

    for (SceneGraphImageAttribute attr : newAttrs) {
      img.addAttribute(attr);
    }
  }

  public void lemmatize(SceneGraphImage img) {

    StanfordCoreNLP pipeline = getPipeline();


    /* attributes */
    for (SceneGraphImageAttribute attr : img.attributes) {
      String attribute = removeDeterminersAndNumbers(removeFinalPunctuation(attr.attribute));
      String sentence = String.format("She is %s .\n", attribute);
      Annotation doc = new Annotation(sentence);
      pipeline.annotate(doc);

      CoreMap sentenceAnn = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      List<CoreLabel> tokens = sentenceAnn.get(CoreAnnotations.TokensAnnotation.class);
      attr.attributeGloss = tokens.subList(2, tokens.size() - 1);

      String subject = removeDeterminersAndNumbers(removeFinalPunctuation(attr.text[0]));
      sentence = String.format("The %s is tall .", subject);
      doc = new Annotation(sentence);
      pipeline.annotate(doc);

      sentenceAnn = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      tokens = sentenceAnn.get(CoreAnnotations.TokensAnnotation.class);
      attr.subjectGloss = tokens.subList(1, tokens.size() - 3);
      attr.subject.labels.add(attr.subjectGloss);

    }

    /* relations */
    for (SceneGraphImageRelationship reln : img.relationships) {
      String object = removeDeterminersAndNumbers(removeFinalPunctuation(reln.text[2]));
      String sentence = String.format("She is the %s .\n", object);
      Annotation doc = new Annotation(sentence);
      pipeline.annotate(doc);

      CoreMap sentenceAnn = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      List<CoreLabel> tokens = sentenceAnn.get(CoreAnnotations.TokensAnnotation.class);
      reln.objectGloss = tokens.subList(3, tokens.size() - 1);
      reln.object.labels.add(reln.objectGloss);


      String subject = removeDeterminersAndNumbers(removeFinalPunctuation(reln.text[0]));
      sentence = String.format("The %s is tall .", subject);
      doc = new Annotation(sentence);
      pipeline.annotate(doc);

      sentenceAnn = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      tokens = sentenceAnn.get(CoreAnnotations.TokensAnnotation.class);
      reln.subjectGloss = tokens.subList(1, tokens.size() - 3);
      reln.subject.labels.add(reln.subjectGloss);


      String predicate = removeDeterminersAndNumbers(removeFinalPunctuation(reln.predicate));
      sentence = String.format("A horse %s an apple .", predicate);
      doc = new Annotation(sentence);
      pipeline.annotate(doc);

      sentenceAnn = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      tokens = sentenceAnn.get(CoreAnnotations.TokensAnnotation.class);
      reln.predicateGloss = tokens.subList(2, tokens.size() - 3);
    }

    for (SceneGraphImageObject object : img.objects) {
      if (object.names.size() > object.labels.size()) {
        for (String name : object.names) {
          String x = removeDeterminersAndNumbers(removeFinalPunctuation(name));
          String sentence = String.format("The %s is tall .", x);
          Annotation doc = new Annotation(sentence);
          pipeline.annotate(doc);
          CoreMap sentenceAnn = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
          List<CoreLabel> tokens = sentenceAnn.get(CoreAnnotations.TokensAnnotation.class);
          object.labels.add(tokens.subList(1, tokens.size() - 3));
        }
      }
    }

    StanfordCoreNLP tokenizerPipeline = getTokenizerPipeline();

    for (SceneGraphImageRegion region : img.regions) {


      Annotation doc = new Annotation(region.phrase.toLowerCase());
      tokenizerPipeline.annotate(doc);

      CoreMap sentenceAnn = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      region.tokens = sentenceAnn.get(CoreAnnotations.TokensAnnotation.class);
    }
  }

  /**
   * removes leading "be"s and trailing determiners from
   * the relation name.
   */
  public void trimFunctionWords(SceneGraphImage img) {

    for (SceneGraphImageRelationship reln : img.relationships) {
      CoreLabel firstWord = reln.predicateGloss.get(0);

      if (firstWord.lemma().matches("be|an?|the") && reln.predicateGloss.size() > 1) {
        reln.predicateGloss = reln.predicateGloss.subList(1, reln.predicateGloss.size());
      }
    }
  }
}
