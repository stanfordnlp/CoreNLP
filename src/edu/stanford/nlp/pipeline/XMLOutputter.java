package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.coref.CorefCoreAnnotations;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import nu.xom.*;


/**
 * An outputter to XML format.
 * This is not intended to be de-serialized back into annotations; for that,
 * see {@link edu.stanford.nlp.pipeline.AnnotationSerializer}; e.g.,
 * {@link edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer}.
 */
public class XMLOutputter extends AnnotationOutputter  {

  // the namespace is set in the XSLT file
  private static final String NAMESPACE_URI = null;
  private static final String STYLESHEET_NAME = "CoreNLP-to-HTML.xsl";

  public XMLOutputter() {}

  /** {@inheritDoc} */
  @Override
  public void print(Annotation annotation, OutputStream os, Options options) throws IOException {
    Document xmlDoc = annotationToDoc(annotation, options);
    Serializer ser = new Serializer(os, options.encoding);
    if (options.pretty) {
      ser.setIndent(2);
    } else {
      ser.setIndent(0);
    }
    ser.setMaxLength(0);
    ser.write(xmlDoc);
    ser.flush();
  }

  public static void xmlPrint(Annotation annotation, OutputStream os) throws IOException {
    new XMLOutputter().print(annotation, os);
  }

  public static void xmlPrint(Annotation annotation, OutputStream os, StanfordCoreNLP pipeline) throws IOException {
    new XMLOutputter().print(annotation, os, pipeline);
  }

  public static void xmlPrint(Annotation annotation, OutputStream os, Options options) throws IOException {
    new XMLOutputter().print(annotation, os, options);
  }

  /**
   * Converts the given annotation to an XML document using options taken from the StanfordCoreNLP pipeline
   */
  public static Document annotationToDoc(Annotation annotation, StanfordCoreNLP pipeline) {
    Options options = getOptions(pipeline.getProperties());
    return annotationToDoc(annotation, options);
  }

  /**
   * Converts the given annotation to an XML document using the specified options
   */
  public static Document annotationToDoc(Annotation annotation, Options options) {
    //
    // create the XML document with the root node pointing to the namespace URL
    //
    Element root = new Element("root", NAMESPACE_URI);
    Document xmlDoc = new Document(root);
    ProcessingInstruction pi = new ProcessingInstruction("xml-stylesheet",
            "href=\"" + STYLESHEET_NAME + "\" type=\"text/xsl\"");
    xmlDoc.insertChild(pi, 0);
    Element docElem = new Element("document", NAMESPACE_URI);
    root.appendChild(docElem);

    setSingleElement(docElem, "docId", NAMESPACE_URI, annotation.get(CoreAnnotations.DocIDAnnotation.class));
    setSingleElement(docElem, "docDate", NAMESPACE_URI, annotation.get(CoreAnnotations.DocDateAnnotation.class));
    setSingleElement(docElem, "docSourceType", NAMESPACE_URI, annotation.get(CoreAnnotations.DocSourceTypeAnnotation.class));
    setSingleElement(docElem, "docType", NAMESPACE_URI, annotation.get(CoreAnnotations.DocTypeAnnotation.class));
    setSingleElement(docElem, "author", NAMESPACE_URI, annotation.get(CoreAnnotations.AuthorAnnotation.class));
    setSingleElement(docElem, "location", NAMESPACE_URI, annotation.get(CoreAnnotations.LocationAnnotation.class));

    if (options.includeText) {
      setSingleElement(docElem, "text", NAMESPACE_URI, annotation.get(CoreAnnotations.TextAnnotation.class));
    }

    Element sentencesElem = new Element("sentences", NAMESPACE_URI);
    docElem.appendChild(sentencesElem);

    //
    // save the info for each sentence in this doc
    //
    if(annotation.get(CoreAnnotations.SentencesAnnotation.class) != null){
      int sentCount = 1;
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        Element sentElem = new Element("sentence", NAMESPACE_URI);
        sentElem.addAttribute(new Attribute("id", Integer.toString(sentCount)));
        Integer lineNumber = sentence.get(CoreAnnotations.LineNumberAnnotation.class);
        if (lineNumber != null) {
          sentElem.addAttribute(new Attribute("line", Integer.toString(lineNumber)));
        }
        sentCount ++;

        // add the word table with all token-level annotations
        Element wordTable = new Element("tokens", NAMESPACE_URI);
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        for(int j = 0; j < tokens.size(); j ++){
          Element wordInfo = new Element("token", NAMESPACE_URI);
          addWordInfo(wordInfo, tokens.get(j), j + 1, NAMESPACE_URI, options);
          wordTable.appendChild(wordInfo);
        }
        sentElem.appendChild(wordTable);

        // add tree info
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);

        if(tree != null) {
          // add the constituent tree for this sentence
          Element parseInfo = new Element("parse", NAMESPACE_URI);
          addConstituentTreeInfo(parseInfo, tree, options.constituencyTreePrinter);
          sentElem.appendChild(parseInfo);
        }

        SemanticGraph basicDependencies = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);

        if (basicDependencies != null) {
          // add the dependencies for this sentence
          Element depInfo = buildDependencyTreeInfo("basic-dependencies", sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class), tokens, NAMESPACE_URI);
          if (depInfo != null) {
            sentElem.appendChild(depInfo);
          }

          depInfo = buildDependencyTreeInfo("collapsed-dependencies", sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class), tokens, NAMESPACE_URI);
          if (depInfo != null) {
            sentElem.appendChild(depInfo);
          }

          depInfo = buildDependencyTreeInfo("collapsed-ccprocessed-dependencies", sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class), tokens, NAMESPACE_URI);
          if (depInfo != null) {
            sentElem.appendChild(depInfo);
          }

          depInfo = buildDependencyTreeInfo("enhanced-dependencies", sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class), tokens, NAMESPACE_URI);
          if (depInfo != null) {
            sentElem.appendChild(depInfo);
          }

          depInfo = buildDependencyTreeInfo("enhanced-plus-plus-dependencies", sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class), tokens, NAMESPACE_URI);
          if (depInfo != null) {
            sentElem.appendChild(depInfo);
          }
        }

        // add Open IE triples
        Collection<RelationTriple> openieTriples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
        if (openieTriples != null) {
          Element openieElem = new Element("openie", NAMESPACE_URI);
          addTriples(openieTriples, openieElem, NAMESPACE_URI);
          sentElem.appendChild(openieElem);
        }

        // add KBP triples
        Collection<RelationTriple> kbpTriples = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
        if (kbpTriples != null) {
          Element kbpElem = new Element("kbp", NAMESPACE_URI);
          addTriples(kbpTriples, kbpElem, NAMESPACE_URI);
          sentElem.appendChild(kbpElem);
        }

        // add the MR entities and relations
        List<EntityMention> entities = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
        List<RelationMention> relations = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
        if (entities != null && ! entities.isEmpty()) {
          Element mrElem = new Element("MachineReading", NAMESPACE_URI);
          Element entElem = new Element("entities", NAMESPACE_URI);
          addEntities(entities, entElem, NAMESPACE_URI);
          mrElem.appendChild(entElem);

          if (relations != null) {
            Element relElem = new Element("relations", NAMESPACE_URI);
            addRelations(relations, relElem, NAMESPACE_URI, options.relationsBeam);
            mrElem.appendChild(relElem);
          }

          sentElem.appendChild(mrElem);
        }

        // Adds sentiment as an attribute of this sentence.
        Tree sentimentTree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
        if (sentimentTree != null) {
          addSentiment(sentence, sentimentTree, sentElem, NAMESPACE_URI);
        }

        // add the sentence to the root
        sentencesElem.appendChild(sentElem);
      }
    }

    //
    // add the coref graph
    //
    Map<Integer, CorefChain> corefChains =
            annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    if (corefChains != null) {
      List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      Element corefInfo = new Element("coreference", NAMESPACE_URI);
      addCorefGraphInfo(options, corefInfo, sentences, corefChains, NAMESPACE_URI);
      docElem.appendChild(corefInfo);
    }

    //
    // save any document-level annotations here
    //

    return xmlDoc;
  }

  /**
   * Add an element to the sentence with the sentiment class and a
   * list of scores for each of the possible classes
   */
  private static void addSentiment(CoreMap sentence, Tree sentimentTree, Element sentElem, String namespaceUri) {
    int sentiment = RNNCoreAnnotations.getPredictedClass(sentimentTree);
    sentElem.addAttribute(new Attribute("sentimentValue", Integer.toString(sentiment)));
    String sentimentClass = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
    sentElem.addAttribute(new Attribute("sentiment", sentimentClass.replaceAll(" ", "")));

    Element sentimentElem = new Element("sentiment", namespaceUri);

    setSingleElement(sentimentElem, "sentiment", namespaceUri, sentimentClass);
    setSingleElement(sentimentElem, "sentimentValue", namespaceUri, Integer.toString(sentiment));
    List<Double> sentimentPredictions = RNNCoreAnnotations.getPredictionsAsStringList(sentimentTree);
    Element predictionsElem = new Element("predictions", namespaceUri);
    for (int i = 0; i < sentimentPredictions.size(); ++i) {
      double score = sentimentPredictions.get(i);
      Element predElem = new Element("prediction", namespaceUri);
      setSingleElement(predElem, "classIndex", namespaceUri, Integer.toString(i));
      setSingleElement(predElem, "score", namespaceUri, Double.toString(score));
      predictionsElem.appendChild(predElem);
    }
    sentimentElem.appendChild(predictionsElem);

    sentElem.appendChild(sentimentElem);
  }

  /**
   * Generates the XML content for a list of OpenIE triples.
   */
  private static void addTriples(Collection<RelationTriple> openieTriples, Element top, String namespaceUri) {
    for (RelationTriple triple : openieTriples) {
      top.appendChild(toXML(triple, namespaceUri));
    }
  }

  /**
   * Generates the XML content for a constituent tree
   */
  private static void addConstituentTreeInfo(Element treeInfo, Tree tree, TreePrint constituentTreePrinter) {
    StringWriter treeStrWriter = new StringWriter();
    constituentTreePrinter.printTree(tree, new PrintWriter(treeStrWriter, true));
    String temp = treeStrWriter.toString();
    //log.info(temp);
    treeInfo.appendChild(temp);
  }

  private static Element buildDependencyTreeInfo(String dependencyType, SemanticGraph graph, List<CoreLabel> tokens, String curNS) {
    if(graph != null) {
      Element depInfo = new Element("dependencies", curNS);
      depInfo.addAttribute(new Attribute("type", dependencyType));
      // The SemanticGraph doesn't explicitly encode the ROOT node,
      // so we print that out ourselves
      for (IndexedWord root : graph.getRoots()) {
        String rel = GrammaticalRelation.ROOT.getLongName();
        rel = rel.replaceAll("\\s+", ""); // future proofing
        int source = 0;
        int target = root.index();
        String sourceWord = "ROOT";
        String targetWord = tokens.get(target - 1).word();
        final boolean isExtra = false;

        addDependencyInfo(depInfo, rel, isExtra, source, sourceWord, null, target, targetWord, null, curNS);
      }
      for (SemanticGraphEdge edge : graph.edgeListSorted()) {
        String rel = edge.getRelation().toString();
        rel = rel.replaceAll("\\s+", "");
        int source = edge.getSource().index();
        int target = edge.getTarget().index();
        String sourceWord = tokens.get(source - 1).word();
        String targetWord = tokens.get(target - 1).word();
        Integer sourceCopy = edge.getSource().copyCount();
        Integer targetCopy = edge.getTarget().copyCount();
        boolean isExtra = edge.isExtra();

        addDependencyInfo(depInfo, rel, isExtra, source, sourceWord, sourceCopy, target, targetWord, targetCopy, curNS);
      }
      return depInfo;
    }
    return null;
  }

  private static void addDependencyInfo(Element depInfo, String rel, boolean isExtra, int source, String sourceWord, Integer sourceCopy, int target, String targetWord, Integer targetCopy, String curNS) {
    Element depElem = new Element("dep", curNS);
    depElem.addAttribute(new Attribute("type", rel));
    if (isExtra) {
      depElem.addAttribute(new Attribute("extra", "true"));
    }

    Element govElem = new Element("governor", curNS);
    govElem.addAttribute(new Attribute("idx", Integer.toString(source)));
    govElem.appendChild(sourceWord);
    if (sourceCopy != null && sourceCopy > 0) {
      govElem.addAttribute(new Attribute("copy", Integer.toString(sourceCopy)));
    }
    depElem.appendChild(govElem);

    Element dependElem = new Element("dependent", curNS);
    dependElem.addAttribute(new Attribute("idx", Integer.toString(target)));
    dependElem.appendChild(targetWord);
    if (targetCopy != null && targetCopy > 0) {
      dependElem.addAttribute(new Attribute("copy", Integer.toString(targetCopy)));
    }
    depElem.appendChild(dependElem);

    depInfo.appendChild(depElem);
  }

  /**
   * Generates the XML content for MachineReading entities.
   */
  private static void addEntities(List<EntityMention> entities, Element top, String curNS) {
    for (EntityMention e: entities) {
      Element ee = toXML(e, curNS);
      top.appendChild(ee);
    }
  }

  /**
   * Generates the XML content for MachineReading relations.
   */
  private static void addRelations(List<RelationMention> relations, Element top, String curNS, double beam) {
    for (RelationMention r: relations){
      if (r.printableObject(beam)) {
        Element re = toXML(r, curNS);
        top.appendChild(re);
      }
    }
  }

  /**
   * Generates the XML content for the coreference chain object.
   */
  private static boolean addCorefGraphInfo
    (Options options, Element corefInfo, List<CoreMap> sentences, Map<Integer, CorefChain> corefChains, String curNS) {
    boolean foundCoref = false;
    for (CorefChain chain : corefChains.values()) {
      if (!options.printSingletons && chain.getMentionsInTextualOrder().size() <= 1)
        continue;
      foundCoref = true;
      Element chainElem = new Element("coreference", curNS);
      CorefChain.CorefMention source = chain.getRepresentativeMention();
      addCorefMention(options, chainElem, curNS, sentences, source, true);
      for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
        if (mention == source)
          continue;
        addCorefMention(options, chainElem, curNS, sentences, mention, false);
      }
      corefInfo.appendChild(chainElem);
    }
    return foundCoref;
  }

  private static void addCorefMention(Options options,
                                      Element chainElem, String curNS,
                                      List<CoreMap> sentences,
                                      CorefChain.CorefMention mention,
                                      boolean representative) {
    Element mentionElem = new Element("mention", curNS);
    if (representative) {
      mentionElem.addAttribute(new Attribute("representative", "true"));
    }

    setSingleElement(mentionElem, "sentence", curNS,
                     Integer.toString(mention.sentNum));
    setSingleElement(mentionElem, "start", curNS,
                     Integer.toString(mention.startIndex));
    setSingleElement(mentionElem, "end", curNS,
                     Integer.toString(mention.endIndex));
    setSingleElement(mentionElem, "head", curNS,
                     Integer.toString(mention.headIndex));

    String text = mention.mentionSpan;
    setSingleElement(mentionElem, "text", curNS, text);
    // Do you want context with your coreference?
    if (sentences != null && options.coreferenceContextSize > 0) {
      // If so use sentences to get so context from sentences

      List<CoreLabel> tokens = sentences.get(mention.sentNum - 1).get(CoreAnnotations.TokensAnnotation.class);
      int contextStart = Math.max(mention.startIndex - 1 - 5, 0);
      int contextEnd = Math.min(mention.endIndex - 1 + 5, tokens.size());
      String leftContext = StringUtils.joinWords(tokens, " ", contextStart, mention.startIndex - 1);
      String rightContext = StringUtils.joinWords(tokens, " ", mention.endIndex - 1, contextEnd);
      setSingleElement(mentionElem, "leftContext", curNS, leftContext);
      setSingleElement(mentionElem, "rightContext", curNS, rightContext);
    }

    chainElem.appendChild(mentionElem);
  }

  private static void addWordInfo(Element wordInfo, CoreMap token, int id, String curNS, Options options) {
    // store the position of this word in the sentence
    wordInfo.addAttribute(new Attribute("id", Integer.toString(id)));

    setSingleElement(wordInfo, "word", curNS, token.get(CoreAnnotations.TextAnnotation.class));
    setSingleElement(wordInfo, "lemma", curNS, token.get(CoreAnnotations.LemmaAnnotation.class));

    if (options.includeText) {
      setSingleElement(wordInfo, "before", curNS, token.get(CoreAnnotations.BeforeAnnotation.class));
      setSingleElement(wordInfo, "after", curNS, token.get(CoreAnnotations.AfterAnnotation.class));
      setSingleElement(wordInfo, "originalText", curNS, token.get(CoreAnnotations.OriginalTextAnnotation.class));
    }

    if (token.containsKey(CoreAnnotations.CharacterOffsetBeginAnnotation.class) && token.containsKey(CoreAnnotations.CharacterOffsetEndAnnotation.class)) {
      setSingleElement(wordInfo, "CharacterOffsetBegin", curNS, Integer.toString(token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)));
      setSingleElement(wordInfo, "CharacterOffsetEnd", curNS, Integer.toString(token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)));
    }

    if (token.containsKey(CoreAnnotations.CodepointOffsetBeginAnnotation.class) && token.containsKey(CoreAnnotations.CodepointOffsetEndAnnotation.class)) {
      setSingleElement(wordInfo, "CodepointOffsetBegin", curNS, Integer.toString(token.get(CoreAnnotations.CodepointOffsetBeginAnnotation.class)));
      setSingleElement(wordInfo, "CodepointOffsetEnd", curNS, Integer.toString(token.get(CoreAnnotations.CodepointOffsetEndAnnotation.class)));
    }

    if (token.containsKey(CoreAnnotations.PartOfSpeechAnnotation.class)) {
      setSingleElement(wordInfo, "POS", curNS, token.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    }

    if (token.containsKey(CoreAnnotations.NamedEntityTagAnnotation.class)) {
      setSingleElement(wordInfo, "NER", curNS, token.get(CoreAnnotations.NamedEntityTagAnnotation.class));
    }

    if (token.containsKey(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)) {
      setSingleElement(wordInfo, "NormalizedNER", curNS, token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
    }

    if (token.containsKey(CoreAnnotations.SpeakerAnnotation.class)) {
      setSingleElement(wordInfo, "Speaker", curNS, token.get(CoreAnnotations.SpeakerAnnotation.class));
    }

    if (token.containsKey(TimeAnnotations.TimexAnnotation.class)) {
      Timex timex = token.get(TimeAnnotations.TimexAnnotation.class);
      Element timexElem = new Element("Timex", curNS);
      timexElem.addAttribute(new Attribute("tid", timex.tid()));
      timexElem.addAttribute(new Attribute("type", timex.timexType()));
      timexElem.appendChild(timex.value());
      wordInfo.appendChild(timexElem);
    }

    if (token.containsKey(CoreAnnotations.TrueCaseAnnotation.class)) {
      Element cur = new Element("TrueCase", curNS);
      cur.appendChild(token.get(CoreAnnotations.TrueCaseAnnotation.class));
      wordInfo.appendChild(cur);
    }
    if (token.containsKey(CoreAnnotations.TrueCaseTextAnnotation.class)) {
      Element cur = new Element("TrueCaseText", curNS);
      cur.appendChild(token.get(CoreAnnotations.TrueCaseTextAnnotation.class));
      wordInfo.appendChild(cur);
    }

    if (token.containsKey(SentimentCoreAnnotations.SentimentClass.class)) {
      Element cur = new Element("sentiment", curNS);
      cur.appendChild(token.get(SentimentCoreAnnotations.SentimentClass.class));
      wordInfo.appendChild(cur);
    }

    if (token.containsKey(CoreAnnotations.WikipediaEntityAnnotation.class)) {
      Element cur = new Element("entitylink", curNS);
      cur.appendChild(token.get(CoreAnnotations.WikipediaEntityAnnotation.class));
      wordInfo.appendChild(cur);
    }

//    IntTuple corefDest;
//    if((corefDest = label.get(CorefDestAnnotation.class)) != null){
//      Element cur = new Element("coref", curNS);
//      String value = Integer.toString(corefDest.get(0)) + "." + Integer.toString(corefDest.get(1));
//      cur.setText(value);
//      wordInfo.addContent(cur);
//    }
  }

  /**
   * Helper method for addWordInfo().  If the value is not null,
   * creates an element of the given name and namespace and adds it to the
   * tokenElement.
   *
   * @param tokenElement This is the element to which the newly created element will be added
   * @param elemName This is the name for the new XML element
   * @param curNS    The current namespace
   * @param value    This is its value
   */
  private static void setSingleElement(Element tokenElement, String elemName, String curNS, String value) {
    if (value != null) {
      Element cur = new Element(elemName, curNS);
      cur.appendChild(value);
      tokenElement.appendChild(cur);
    }
  }

  private static Element toXML(RelationTriple triple, String curNS) {
    Element top = new Element("triple", curNS);
    top.addAttribute(new Attribute("confidence", triple.confidenceGloss()));

    // Create the subject
    Element subject = new Element("subject", curNS);
    subject.addAttribute(new Attribute("begin", Integer.toString(triple.subjectTokenSpan().first)));
    subject.addAttribute(new Attribute("end", Integer.toString(triple.subjectTokenSpan().second)));
    Element text = new Element("text", curNS);
    text.appendChild(triple.subjectGloss());
    Element lemma = new Element("lemma", curNS);
    lemma.appendChild(triple.subjectLemmaGloss());
    subject.appendChild(text);
    subject.appendChild(lemma);
    top.appendChild(subject);

    // Create the relation
    Element relation = new Element("relation", curNS);
    relation.addAttribute(new Attribute("begin", Integer.toString(triple.relationTokenSpan().first)));
    relation.addAttribute(new Attribute("end", Integer.toString(triple.relationTokenSpan().second)));
    text = new Element("text", curNS);
    text.appendChild(triple.relationGloss());
    lemma = new Element("lemma", curNS);
    lemma.appendChild(triple.relationLemmaGloss());
    relation.appendChild(text);
    relation.appendChild(lemma);
    top.appendChild(relation);

    // Create the object
    Element object = new Element("object", curNS);
    object.addAttribute(new Attribute("begin", Integer.toString(triple.objectTokenSpan().first)));
    object.addAttribute(new Attribute("end", Integer.toString(triple.objectTokenSpan().second)));
    text = new Element("text", curNS);
    text.appendChild(triple.objectGloss());
    lemma = new Element("lemma", curNS);
    lemma.appendChild(triple.objectLemmaGloss());
    object.appendChild(text);
    object.appendChild(lemma);
    top.appendChild(object);

    return top;
  }

  private static Element toXML(EntityMention entity, String curNS) {
    Element top = new Element("entity", curNS);
    top.addAttribute(new Attribute("id", entity.getObjectId()));
    Element type = new Element("type", curNS);
    type.appendChild(entity.getType());
    top.appendChild(entity.getType());
    if (entity.getNormalizedName() != null){
      Element nm = new Element("normalized", curNS);
      nm.appendChild(entity.getNormalizedName());
      top.appendChild(nm);
    }

    if (entity.getSubType() != null){
      Element subtype = new Element("subtype", curNS);
      subtype.appendChild(entity.getSubType());
      top.appendChild(subtype);
    }
    Element span = new Element("span", curNS);
    span.addAttribute(new Attribute("start", Integer.toString(entity.getHeadTokenStart())));
    span.addAttribute(new Attribute("end", Integer.toString(entity.getHeadTokenEnd())));
    top.appendChild(span);

    top.appendChild(makeProbabilitiesElement(entity, curNS));
    return top;
  }


  private static Element toXML(RelationMention relation, String curNS) {
    Element top = new Element("relation", curNS);
    top.addAttribute(new Attribute("id", relation.getObjectId()));
    Element type = new Element("type", curNS);
    type.appendChild(relation.getType());
    top.appendChild(relation.getType());
    if (relation.getSubType() != null){
      Element subtype = new Element("subtype", curNS);
      subtype.appendChild(relation.getSubType());
      top.appendChild(relation.getSubType());
    }

    List<EntityMention> mentions = relation.getEntityMentionArgs();
    Element args = new Element("arguments", curNS);
    for (EntityMention e : mentions) {
      args.appendChild(toXML(e, curNS));
    }
    top.appendChild(args);

    top.appendChild(makeProbabilitiesElement(relation, curNS));
    return top;
  }

  private static Element makeProbabilitiesElement(ExtractionObject object, String curNS) {
    Element probs = new Element("probabilities", curNS);
    if (object.getTypeProbabilities() != null){
      List<Pair<String, Double>> sorted = Counters.toDescendingMagnitudeSortedListWithCounts(object.getTypeProbabilities());
      for(Pair<String, Double> lv: sorted) {
        Element prob = new Element("probability", curNS);
        Element label = new Element("label", curNS);
        label.appendChild(lv.first);
        Element value = new Element("value", curNS);
        value.appendChild(lv.second.toString());
        prob.appendChild(label);
        prob.appendChild(value);
        probs.appendChild(prob);
      }
    }
    return probs;
  }

}

