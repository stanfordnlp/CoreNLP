package edu.stanford.nlp.hcoref;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.hcoref.data.InputDoc;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.hcoref.docreader.CoNLLDocumentReader;
import edu.stanford.nlp.hcoref.docreader.DocReader;
import edu.stanford.nlp.hcoref.md.CorefMentionFinder;
import edu.stanford.nlp.hcoref.md.DependencyCorefMentionFinder;
import edu.stanford.nlp.hcoref.md.HybridCorefMentionFinder;
import edu.stanford.nlp.hcoref.md.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.TreeLemmatizer;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.Redwood;

/** 
 * 
 * make Document for coref input from Annotation and optional info
 * read input (raw, conll etc) with DocReader, mention detection, and document preprocessing will be done here
 *  
 * @author heeyoung
 */
public class CorefDocMaker {
  
  Properties props;
  DocReader reader;
  final HeadFinder headFinder;
  CorefMentionFinder md;
  Dictionaries dict;
  StanfordCoreNLP corenlp;
  final TreeLemmatizer treeLemmatizer;
  LogisticClassifier<String, String> singletonPredictor;
  // Should we call corenlp to add missing annotations?
  // HACK so that when the CorefDocMaker is called from annotator, it doesn't override old annotations
  boolean needMissingAnnotations = true;

  public CorefDocMaker(Properties props, Dictionaries dictionaries) throws ClassNotFoundException, IOException {
    this.props = props;
    this.dict = dictionaries;
    reader = getDocumentReader(props);
    headFinder = getHeadFinder(props);
    md = getMentionFinder(props, dictionaries, headFinder);
//    corenlp = new StanfordCoreNLP(props, false);
    corenlp = loadStanfordProcessor(props);
    treeLemmatizer = new TreeLemmatizer();
    singletonPredictor = (CorefProperties.useSingletonPredictor(props))? 
        getSingletonPredictorFromSerializedFile(CorefProperties.getPathSingletonPredictor(props)) : null;
  }

  public void setNeedMissingAnnotations(boolean needMissingAnnotations) {
    this.needMissingAnnotations = needMissingAnnotations;
  }

  /** Load Stanford Processor: skip unnecessary annotator */
  protected StanfordCoreNLP loadStanfordProcessor(Properties props) {

    Properties pipelineProps = new Properties(props);
    StringBuilder annoSb = new StringBuilder("");
    if (!CorefProperties.useGoldPOS(props))  {
      annoSb.append("pos, lemma");
    } else {
      annoSb.append("lemma");
    }   
    if(CorefProperties.USE_TRUECASE) {
      annoSb.append(", truecase");
    }   
    if (!CorefProperties.useGoldNE(props) || CorefProperties.getLanguage(props)==Locale.CHINESE)  {
      annoSb.append(", ner");
    }   
    if (!CorefProperties.useGoldParse(props))  {
      if(CorefProperties.useConstituencyTree(props)) annoSb.append(", parse");
      else annoSb.append(", depparse");
    }   
    String annoStr = annoSb.toString();
    Redwood.log("MentionExtractor ignores specified annotators, using annotators=" + annoStr);
    pipelineProps.put("annotators", annoStr);
    return new StanfordCoreNLP(pipelineProps, false);
  }


  private static DocReader getDocumentReader(Properties props) {
    switch (CorefProperties.getInputType(props)) {
      case CONLL:
        String corpusPath = CorefProperties.getPathInput(props);
        CoNLLDocumentReader.Options options = new CoNLLDocumentReader.Options();
        options.annotateTokenCoref = false;
        if (CorefProperties.useCoNLLAuto(props)) options.setFilter(".*_auto_conll$");
        options.lang = CorefProperties.getLanguage(props);
        return new CoNLLDocumentReader(corpusPath, options);

      case ACE:
        // TODO
        return null;
      
      case MUC:
        // TODO
        return null;
      
      case RAW:
      default:  // default is raw text
        // TODO
        return null;
    }
  }
  
  private static HeadFinder getHeadFinder(Properties props) {
    Locale lang = CorefProperties.getLanguage(props);
    if(lang == Locale.ENGLISH) return new SemanticHeadFinder();
    else if(lang == Locale.CHINESE) return new ChineseSemanticHeadFinder();
    else {
      throw new RuntimeException("Invalid language setting: cannot load HeadFinder");
    }
  }

  private static CorefMentionFinder getMentionFinder(Properties props, Dictionaries dictionaries, HeadFinder headFinder) throws ClassNotFoundException, IOException {
    
    switch (CorefProperties.getMDType(props)) {
      case RULE:
        return new RuleBasedCorefMentionFinder(headFinder, props);
      
      case HYBRID:
        return new HybridCorefMentionFinder(headFinder, props);
      
      case DEPENDENCY:
      default:  // default is dependency
        return new DependencyCorefMentionFinder(props); 
    }
  }
  
  public Document makeDocument(Annotation anno) throws Exception {
    return makeDocument(new InputDoc(anno, null, null));
  }
  
  /**
   *  Make Document for coref (for method coref(Document doc, StringBuilder[] outputs)).
   *  Mention detection and document preprocessing is done here.
   * @throws Exception
   */
  public Document makeDocument(InputDoc input) throws Exception {
    if (input == null) return null;
    Annotation anno = input.annotation;
    
    // add missing annotation
    if (needMissingAnnotations) {
      addMissingAnnotation(anno);
    }

    if (Boolean.parseBoolean(props.getProperty("hcoref.useMarkedDiscourse", "false"))) {
      anno.set(CoreAnnotations.UseMarkedDiscourseAnnotation.class, true);
    }

    // remove nested NP with same headword except newswire document for chinese
    
    if(input.conllDoc != null && CorefProperties.getLanguage(props)==Locale.CHINESE){
      CorefProperties.setRemoveNested(props, !input.conllDoc.documentID.contains("nw"));
    }
    
    // mention detection: MD gives following information about mentions: mention start/end index, span, headword
    // rest information will be set in preprocess step
    List<List<Mention>> mentions = md.findMentions(anno, dict, props);
    Document doc = new Document(input, mentions);
    
    // find headword for gold mentions
    if(input.goldMentions!=null) findGoldMentionHeads(doc);
    
    // document preprocessing: initialization (assign ID), mention processing (gender, number, type, etc), speaker extraction, etc
    Preprocessor.preprocess(doc, dict, singletonPredictor, headFinder);
    
    return doc;
  }
  
  private void findGoldMentionHeads(Document doc) {
    List<CoreMap> sentences = doc.annotation.get(SentencesAnnotation.class);
    for(int i=0 ; i<sentences.size() ; i++ ) { 
//      md.findHead(sentences.get(i), doc.goldMentions.get(i));
      DependencyCorefMentionFinder.findHeadInDependency(sentences.get(i), doc.goldMentions.get(i));
    }   
  }

  private void addMissingAnnotation(Annotation anno) {
    boolean useConstituency = CorefProperties.useConstituencyTree(props);
    final boolean LEMMATIZE = true;

    List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence:sentences) {
      boolean hasTree = sentence.containsKey(TreeCoreAnnotations.TreeAnnotation.class);
      Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
      
      if(!useConstituency) {     // TODO: temp for dev: make sure we don't use constituency tree
        sentence.remove(TreeCoreAnnotations.TreeAnnotation.class);
      }
      if(LEMMATIZE && hasTree && useConstituency) treeLemmatizer.transformTree(tree);   // TODO don't need?
    }
    corenlp.annotate(anno);
  }

  public void resetDocs() {
    reader.reset();
  }

  public Document nextDoc() throws Exception {
    InputDoc input = reader.nextDoc();
    return (input == null)? null : makeDocument(input);
  }
  
  public static LogisticClassifier<String, String> getSingletonPredictorFromSerializedFile(String serializedFile) {
    try {
      ObjectInputStream ois = IOUtils.readStreamFromString(serializedFile);
      Object o = ois.readObject();
      if (o instanceof LogisticClassifier<?, ?>) {
        return (LogisticClassifier<String, String>) o;
      }    
      throw new ClassCastException("Wanted SingletonPredictor, got " + o.getClass());
    } catch (IOException e) { 
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) { 
      throw new RuntimeException(e);
    }    
  }

}
