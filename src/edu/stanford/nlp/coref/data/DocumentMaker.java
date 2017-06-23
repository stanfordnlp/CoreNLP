package edu.stanford.nlp.coref.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.docreader.CoNLLDocumentReader;
import edu.stanford.nlp.coref.docreader.DocReader;
import edu.stanford.nlp.coref.md.CorefMentionFinder;
import edu.stanford.nlp.coref.md.DependencyCorefMentionFinder;
import edu.stanford.nlp.coref.md.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Class for creating {@link Document}s from raw {@link Annotation}s or from CoNLL input data.
 * @author Heeyoung Lee
 * @author Kevin Clark
 */
public class DocumentMaker {
  private final Properties props;
  private final DocReader reader;
  private final HeadFinder headFinder;
  private final Dictionaries dict;
  private final CorefMentionFinder md;

  public DocumentMaker(Properties props, Dictionaries dictionaries)
      throws ClassNotFoundException, IOException {
    this.props = props;
    this.dict = dictionaries;
    reader = getDocumentReader(props);
    headFinder = getHeadFinder(props);
    md = CorefProperties.useGoldMentions(props) ?
        new RuleBasedCorefMentionFinder(headFinder, props) : null;
  }

  private static DocReader getDocumentReader(Properties props) {
    String corpusPath = CorefProperties.getInputPath(props);
    if (corpusPath == null) {
      return null;
    }
    CoNLLDocumentReader.Options options = new CoNLLDocumentReader.Options();
    if (!PropertiesUtils.getBool(props,"coref.printConLLLoadingMessage",true))
      options.printConLLLoadingMessage = false;
    options.annotateTokenCoref = false;
    String conllFileFilter = props.getProperty("coref.conllFileFilter", ".*_auto_conll$");
    options.setFilter(conllFileFilter);
    options.lang = CorefProperties.getLanguage(props);
    return new CoNLLDocumentReader(corpusPath, options);
  }

  private static HeadFinder getHeadFinder(Properties props) {
    Locale lang = CorefProperties.getLanguage(props);
    if (lang == Locale.ENGLISH) return new SemanticHeadFinder();
    else if (lang == Locale.CHINESE) return new ChineseSemanticHeadFinder();
    else {
      throw new RuntimeException("Invalid language setting: cannot load HeadFinder");
    }
  }

  public Document makeDocument(Annotation anno) throws Exception {
    return makeDocument(new InputDoc(anno, null, null));
  }

  public Document makeDocument(InputDoc input) throws Exception {
    List<List<Mention>> mentions = new ArrayList<>() ;
    if (CorefProperties.useGoldMentions(props)) {
      List<CoreMap> sentences = input.annotation.get(CoreAnnotations.SentencesAnnotation.class);
      for (int i = 0; i < sentences.size(); i++) {
        CoreMap sentence = sentences.get(i);
        List<CoreLabel> sentenceWords = sentence.get(CoreAnnotations.TokensAnnotation.class);
        List<Mention> sentenceMentions = new ArrayList<>();
        mentions.add(sentenceMentions);
        for (Mention g : input.goldMentions.get(i)) {
          sentenceMentions.add(new Mention(-1, g.startIndex, g.endIndex, sentenceWords,
              null, null, new ArrayList<>(sentenceWords.subList(g.startIndex, g.endIndex))));
        }
        md.findHead(sentence, sentenceMentions);
      }
    } else {
      for (CoreMap sentence : input.annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        mentions.add(sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class));
      }
    }
    Document doc = new Document(input, mentions);

    if (input.goldMentions != null) {
      findGoldMentionHeads(doc);
    }
    DocumentPreprocessor.preprocess(doc, dict, null, headFinder);

    return doc;
  }

  private void findGoldMentionHeads(Document doc) {
    List<CoreMap> sentences = doc.annotation.get(SentencesAnnotation.class);
    for(int i=0 ; i<sentences.size() ; i++ ) {
      DependencyCorefMentionFinder.findHeadInDependency(sentences.get(i), doc.goldMentions.get(i));
    }
  }

  private StanfordCoreNLP coreNLP;
  private StanfordCoreNLP getStanfordCoreNLP(Properties props) {
    if (coreNLP != null) {
      return coreNLP;
    }

    Properties pipelineProps = new Properties(props);
    if (CorefProperties.conll(props)) {
      pipelineProps.put("annotators", (CorefProperties.getLanguage(props) == Locale.CHINESE ?
          "lemma, ner" : "lemma") + (CorefProperties.useGoldMentions(props) ? "" : ", mention"));
    } else {
      pipelineProps.put("annotators", "pos, lemma, ner, " +
          (CorefProperties.useConstituencyParse(props) ? "parse" : "depparse") +
          (CorefProperties.useGoldMentions(props) ? "" : ", mention"));
    }
    return (coreNLP = new StanfordCoreNLP(pipelineProps, false));
  }

  public Document nextDoc() throws Exception {
    InputDoc input = reader.nextDoc();
    if (input == null) {
      return null;
    }

    if (!CorefProperties.useConstituencyParse(props)) {
      for (CoreMap sentence : input.annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        sentence.remove(TreeCoreAnnotations.TreeAnnotation.class);
      }
    }

    getStanfordCoreNLP(props).annotate(input.annotation);
    if (CorefProperties.conll(props)) {
      input.annotation.set(CoreAnnotations.UseMarkedDiscourseAnnotation.class, true);
    }

    return makeDocument(input);
  }

  public void resetDocs() {
    reader.reset();
  }
}
