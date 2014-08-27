package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.spanish.SpanishHeadFinder;
import edu.stanford.nlp.trees.international.spanish.SpanishTreeReaderFactory;
import edu.stanford.nlp.trees.international.spanish.SpanishTreebankLanguagePack;

import java.util.List;

/**
 * TreebankLangParserParams for the AnCora corpus. This package assumes
 * that the provided trees are in PTB format, read from the initial
 * AnCora XML with
 * {@link edu.stanford.nlp.trees.international.spanish.SpanishXMLTreeReader}
 * and preprocessed with
 * {@link edu.stanford.nlp.international.spanish.pipeline.MultiWordPreprocessor}.
 *
 * @author Jon Gauthier
 *
 */
public class SpanishTreebankParserParams extends AbstractTreebankParserParams {

  private static final long serialVersionUID = -8734165273482119424L;

  public SpanishTreebankParserParams() {
    super(new SpanishTreebankLanguagePack());

    setInputEncoding("UTF-8");
  }

  @Override
  public Tree transformTree(Tree t, Tree root) {
    // TODO
    return t;
  }

  @Override
  public HeadFinder headFinder() {
    return new SpanishHeadFinder(tlp);
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    // Not supported
    return null;
  }

  @Override
  public String[] sisterSplitters() {
    return new String[0];
  }

  @Override
  public TreeTransformer collinizer() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public TreeTransformer collinizerEvalb() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public DiskTreebank diskTreebank() {
   return new DiskTreebank(treeReaderFactory(), inputEncoding);
  }

  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory(), inputEncoding);
  }

  public TreeReaderFactory treeReaderFactory() {
    return new SpanishTreeReaderFactory();
  }

  public List<HasWord> defaultTestSentence() {
    String[] sent = {"Ésto", "es", "sólo", "una", "prueba", "."};
    return Sentence.toWordList(sent);
  }

  @Override
  public void display() {
    System.err.println(getClass().getName());
  }

}
