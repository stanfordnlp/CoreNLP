//
// StanfordCoreNLP -- a suite of NLP tools
// Copyright (c) 2009-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.dcoref; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

/**
 * Extracts {@literal <COREF>} mentions from a file annotated in MUC format.
 *
 * @author Jenny Finkel
 * @author Mihai Surdeanu
 * @author Karthik Raghunathan
 */
public class MUCMentionExtractor extends MentionExtractor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(MUCMentionExtractor.class);

  private final TokenizerFactory<CoreLabel> tokenizerFactory;
  private final String fileContents;
  private int currentOffset;

  public MUCMentionExtractor(Dictionaries dict, Properties props, Semantics semantics) throws Exception {
    super(dict, semantics);
    String fileName = props.getProperty(Constants.MUC_PROP);
    fileContents = IOUtils.slurpFile(fileName);
    currentOffset = 0;
    tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(false), "");
    stanfordProcessor = loadStanfordProcessor(props);
  }

  public MUCMentionExtractor(Dictionaries dict, Properties props, Semantics semantics,
      LogisticClassifier<String, String> singletonModel) throws Exception {
    this(dict, props, semantics);
    singletonPredictor = singletonModel;
  }

  @Override
  public void resetDocs() {
    super.resetDocs();
    currentOffset = 0;
  }

  @Override
  public Document nextDoc() throws Exception {
    List<List<CoreLabel>> allWords = new ArrayList<>();
    List<Tree> allTrees = new ArrayList<>();
    List<List<Mention>> allGoldMentions = new ArrayList<>();
    List<List<Mention>> allPredictedMentions;
    List<CoreMap> allSentences = new ArrayList<>();
    Annotation docAnno = new Annotation("");

    Pattern docPattern = Pattern.compile("<DOC>(.*?)</DOC>", Pattern.DOTALL+Pattern.CASE_INSENSITIVE);
    Pattern sentencePattern = Pattern.compile("(<s>|<hl>|<dd>|<DATELINE>)(.*?)(</s>|</hl>|</dd>|</DATELINE>)", Pattern.DOTALL+Pattern.CASE_INSENSITIVE);
    Matcher docMatcher = docPattern.matcher(fileContents);
    if (! docMatcher.find(currentOffset)) return null;

    currentOffset = docMatcher.end();
    String doc = docMatcher.group(1);
    Matcher sentenceMatcher = sentencePattern.matcher(doc);
    String ner = null;

    //Maintain current document ID.
    Pattern docIDPattern = Pattern.compile("<DOCNO>(.*?)</DOCNO>", Pattern.DOTALL+Pattern.CASE_INSENSITIVE);
    Matcher docIDMatcher = docIDPattern.matcher(doc);
    if(docIDMatcher.find()) currentDocumentID = docIDMatcher.group(1);
    else currentDocumentID = "documentAfter " + currentDocumentID;

    while (sentenceMatcher.find()) {
      String sentenceString = sentenceMatcher.group(2);
      List<CoreLabel> words = tokenizerFactory.getTokenizer(new StringReader(sentenceString)).tokenize();

      // FIXING TOKENIZATION PROBLEMS
      for (int i = 0; i < words.size(); i++) {
        CoreLabel w = words.get(i);
        if (i > 0 && w.word().equals("$")) {
          if(!words.get(i-1).word().endsWith("PRP") && !words.get(i-1).word().endsWith("WP"))
            continue;
          words.get(i-1).set(CoreAnnotations.TextAnnotation.class, words.get(i-1).word()+"$");
          words.remove(i);
          i--;
        } else if (w.word().equals("\\/")) {
          if(words.get(i-1).word().equals("</COREF>"))
            continue;
          w.set(CoreAnnotations.TextAnnotation.class, words.get(i-1).word()+"\\/"+words.get(i+1).word());
          words.remove(i+1);
          words.remove(i-1);
        }
      }
      // END FIXING TOKENIZATION PROBLEMS

      List<CoreLabel> sentence = new ArrayList<>();
      // MUC accepts embedded coref mentions, so we need to keep a stack for the mentions currently open
      Stack<Mention> stack = new Stack<>();
      List<Mention> mentions = new ArrayList<>();

      allWords.add(sentence);
      allGoldMentions.add(mentions);

      for (CoreLabel word : words) {
        String w = word.get(CoreAnnotations.TextAnnotation.class);
        // found regular token: WORD/POS
        if (!w.startsWith("<") && w.contains("\\/") && w.lastIndexOf("\\/") != w.length()-2) {
          int i = w.lastIndexOf("\\/");
          String w1 = w.substring(0, i);
          // we do NOT set POS info here. We take the POS tags from the parser!
          word.set(CoreAnnotations.TextAnnotation.class, w1);
          word.remove(CoreAnnotations.OriginalTextAnnotation.class);
          if(Constants.USE_GOLD_NE) {
            if (ner != null) {
              word.set(CoreAnnotations.NamedEntityTagAnnotation.class, ner);
            } else {
              word.set(CoreAnnotations.NamedEntityTagAnnotation.class, "O");
            }
          }
          sentence.add(word);
        }
        // found the start SGML tag for a NE, e.g., "<ORGANIZATION>"
        else if (w.startsWith("<") && !w.startsWith("<COREF") && !w.startsWith("</")) {
          Pattern nerPattern = Pattern.compile("<(.*?)>");
          Matcher m = nerPattern.matcher(w);
          m.find();
          ner = m.group(1);
        }
        // found the end SGML tag for a NE, e.g., "</ORGANIZATION>"
        else if (w.startsWith("</") && !w.startsWith("</COREF")) {
          Pattern nerPattern = Pattern.compile("</(.*?)>");
          Matcher m = nerPattern.matcher(w);
          m.find();
          String ner1 = m.group(1);
          if (ner != null && !ner.equals(ner1)) throw new RuntimeException("Unmatched NE labels in MUC file: " + ner + " v. " + ner1);
          ner = null;
        }
        // found the start SGML tag for a coref mention
        else if (w.startsWith("<COREF")) {
          Mention mention = new Mention();
          // position of this mention in the sentence
          mention.startIndex = sentence.size();

          // extract GOLD info about this coref chain. needed for eval
          Pattern idPattern = Pattern.compile("ID=\"(.*?)\"");
          Pattern refPattern = Pattern.compile("REF=\"(.*?)\"");

          Matcher m = idPattern.matcher(w);
          m.find();
          mention.mentionID = Integer.parseInt(m.group(1));

          m = refPattern.matcher(w);
          if (m.find()) {
            mention.originalRef = Integer.parseInt(m.group(1));
          }

          // open mention. keep track of all open mentions using the stack
          stack.push(mention);
        }
        // found the end SGML tag for a coref mention
        else if (w.equals("</COREF>")) {
          Mention mention = stack.pop();
          mention.endIndex = sentence.size();

          // this is a closed mention. add it to the final list of mentions
          // System.err.printf("Found MENTION: ID=%d, REF=%d\n", mention.mentionID, mention.originalRef);
          mentions.add(mention);
        } else {
          word.remove(CoreAnnotations.OriginalTextAnnotation.class);
          if(Constants.USE_GOLD_NE){
            if (ner != null) {
              word.set(CoreAnnotations.NamedEntityTagAnnotation.class, ner);
            } else {
              word.set(CoreAnnotations.NamedEntityTagAnnotation.class, "O");
            }
          }
          sentence.add(word);
        }
      }
      StringBuilder textContent = new StringBuilder();
      for (int i=0 ; i<sentence.size(); i++){
        CoreLabel w = sentence.get(i);
        w.set(CoreAnnotations.IndexAnnotation.class, i+1);
        w.set(CoreAnnotations.UtteranceAnnotation.class, 0);
        if(i>0) textContent.append(" ");
        textContent.append(w.getString(CoreAnnotations.TextAnnotation.class));
      }
      CoreMap sentCoreMap = new Annotation(textContent.toString());
      allSentences.add(sentCoreMap);
      sentCoreMap.set(CoreAnnotations.TokensAnnotation.class, sentence);
    }

    // assign goldCorefClusterID
    Map<Integer, Mention> idMention = Generics.newHashMap();    // temporary use
    for (List<Mention> goldMentions : allGoldMentions) {
      for (Mention m : goldMentions) {
        idMention.put(m.mentionID, m);
      }
    }
    for (List<Mention> goldMentions : allGoldMentions) {
      for (Mention m : goldMentions) {
        if (m.goldCorefClusterID == -1) {
          if (m.originalRef == -1) m.goldCorefClusterID = m.mentionID;
          else {
            int ref = m.originalRef;
            while (true) {
              Mention m2 = idMention.get(ref);
              if (m2.goldCorefClusterID != -1) {
                m.goldCorefClusterID = m2.goldCorefClusterID;
                break;
              } else if (m2.originalRef == -1) {
                m2.goldCorefClusterID = m2.mentionID;
                m.goldCorefClusterID = m2.goldCorefClusterID;
                break;
              } else {
                ref = m2.originalRef;
              }
            }
          }
        }
      }
    }

    docAnno.set(CoreAnnotations.SentencesAnnotation.class, allSentences);
    stanfordProcessor.annotate(docAnno);

    if(allSentences.size()!=allWords.size()) throw new IllegalStateException("allSentences != allWords");
    for(int i = 0 ; i< allSentences.size(); i++){
      List<CoreLabel> annotatedSent = allSentences.get(i).get(CoreAnnotations.TokensAnnotation.class);
      List<CoreLabel> unannotatedSent = allWords.get(i);
      List<Mention> mentionInSent = allGoldMentions.get(i);
      for (Mention m : mentionInSent){
        m.dependency = allSentences.get(i).get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
      }
      if(annotatedSent.size() != unannotatedSent.size()){
        throw new IllegalStateException("annotatedSent != unannotatedSent");
      }
      for (int j = 0, sz = annotatedSent.size(); j < sz; j++){
        CoreLabel annotatedWord = annotatedSent.get(j);
        CoreLabel unannotatedWord = unannotatedSent.get(j);
        if ( ! annotatedWord.get(CoreAnnotations.TextAnnotation.class).equals(unannotatedWord.get(CoreAnnotations.TextAnnotation.class))) {
          throw new IllegalStateException("annotatedWord != unannotatedWord");
        }
      }
      allWords.set(i, annotatedSent);
      allTrees.add(allSentences.get(i).get(TreeCoreAnnotations.TreeAnnotation.class));
    }

    // extract predicted mentions
    if(Constants.USE_GOLD_MENTIONS) allPredictedMentions = allGoldMentions;
    else allPredictedMentions = mentionFinder.extractPredictedMentions(docAnno, maxID, dictionaries);

    // add the relevant fields to mentions and order them for coref
    return arrange(docAnno, allWords, allTrees, allPredictedMentions, allGoldMentions, true);
  }
}
