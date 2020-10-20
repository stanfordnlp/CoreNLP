package edu.stanford.nlp.sequences;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.util.PropertiesUtils;


/**
 * @author Christopher Manning
 */
public class ObjectBankWrapperTest {

  @SuppressWarnings("ForLoopReplaceableByForEach")
  @Test
  public void testUsingIterator() {
    String s = "\n\n@@123\nthis\nis\na\nsentence\n\n@@12\nThis\nis another\n.\n\n";
    String[] output = { "@@", "123", "this", "is", "a", "sentence", "@@", "12", "This", "is", "another", "." };
    String[] outWSs = { "@@", "ddd", "xxxx", "xx", "x", "xxxxx", "@@", "dd", "Xxxx", "xx", "xxxxx", "." };
    Assert.assertEquals("Two output arrays should have same length", output.length, outWSs.length);

    Properties props = PropertiesUtils.asProperties("wordShape", "chris2");
    SeqClassifierFlags flags = new SeqClassifierFlags(props);
    PlainTextDocumentReaderAndWriter<CoreLabel> readerAndWriter = new PlainTextDocumentReaderAndWriter<>();
    readerAndWriter.init(flags);
    ReaderIteratorFactory rif = new ReaderIteratorFactory(new StringReader(s));
    ObjectBank<List<CoreLabel>> di = new ObjectBank<>(rif, readerAndWriter);
    Set<String> knownLCWords = new HashSet<>();
    ObjectBankWrapper<CoreLabel> obw = new ObjectBankWrapper<>(flags, di, knownLCWords);

    try {
      int outIdx = 0;
      for (Iterator<List<CoreLabel>> iter = obw.iterator(); iter.hasNext(); ) {
        List<CoreLabel> sent = iter.next();
        for (Iterator<CoreLabel> iter2 = sent.iterator(); iter2.hasNext(); ) {
          CoreLabel cl = iter2.next();
          String tok = cl.word();
          String shape = cl.get(CoreAnnotations.ShapeAnnotation.class);
          Assert.assertEquals(output[outIdx], tok);
          Assert.assertEquals(outWSs[outIdx], shape);
          outIdx++;
        }
      }

      if (outIdx < output.length) {
        Assert.fail("Too few things in iterator, lacking: " + output[outIdx]);
      }
    } catch (Exception e) {
      Assert.fail("Probably too many things in iterator: " + e);
    }
  }

  @Test
  public void testUsingEnhancedFor() {
    String s = "\n\n@@123\nthis\nis\na\nsentence\n\n@@12\nThis\nis another\n.\n\n";
    String[] output = { "@@", "123", "this", "is", "a", "sentence", "@@", "12", "This", "is", "another", "." };
    String[] outWSs = { "@@", "ddd", "xxxx", "xx", "x", "xxxxx", "@@", "dd", "Xxxx", "xx", "xxxxx", "." };
    Assert.assertEquals("Two output arrays should have same length", output.length, outWSs.length);

    Properties props = PropertiesUtils.asProperties("wordShape", "chris2");
    SeqClassifierFlags flags = new SeqClassifierFlags(props);
    PlainTextDocumentReaderAndWriter<CoreLabel> readerAndWriter = new PlainTextDocumentReaderAndWriter<>();
    readerAndWriter.init(flags);
    ReaderIteratorFactory rif = new ReaderIteratorFactory(new StringReader(s));
    ObjectBank<List<CoreLabel>> di = new ObjectBank<>(rif, readerAndWriter);
    Set<String> knownLCWords = new HashSet<>();
    ObjectBankWrapper<CoreLabel> obw = new ObjectBankWrapper<>(flags, di, knownLCWords);

    try {
      int outIdx = 0;
      for (List<CoreLabel> sent : obw) {
        for (CoreLabel cl : sent) {
          String tok = cl.word();
          String shape = cl.get(CoreAnnotations.ShapeAnnotation.class);
          Assert.assertEquals(output[outIdx], tok);
          Assert.assertEquals(outWSs[outIdx], shape);
          outIdx++;
        }
      }

      if (outIdx < output.length) {
        Assert.fail("Too few things in iterator, lacking: " + output[outIdx]);
      }
    } catch (Exception e) {
      Assert.fail("Probably too many things in iterator." + e);
    }
  }

  @SuppressWarnings({"unchecked", "ZeroLengthArrayAllocation", "ToArrayCallWithZeroLengthArrayArgument"})
  @Test
  public void testUsingToArray() {
    String s = "\n\n@@123\nthis\nis\na\nsentence\n\n@@12\nThis\nis another\n.\n\n";
    String[] output = { "@@", "123", "this", "is", "a", "sentence", "@@", "12", "This", "is", "another", "." };
    String[] outWSs = { "@@", "ddd", "xxxx", "xx", "x", "xxxxx", "@@", "dd", "Xxxx", "xx", "xxxxx", "." };
    Assert.assertEquals("Two output arrays should have same length", output.length, outWSs.length);

    Properties props = PropertiesUtils.asProperties("wordShape", "chris2");
    SeqClassifierFlags flags = new SeqClassifierFlags(props);
    PlainTextDocumentReaderAndWriter<CoreLabel> readerAndWriter = new PlainTextDocumentReaderAndWriter<>();
    readerAndWriter.init(flags);
    ReaderIteratorFactory rif = new ReaderIteratorFactory(new StringReader(s));
    ObjectBank<List<CoreLabel>> di = new ObjectBank<>(rif, readerAndWriter);
    Set<String> knownLCWords = new HashSet<>();
    ObjectBankWrapper<CoreLabel> obw = new ObjectBankWrapper<>(flags, di, knownLCWords);

    try {
      List<CoreLabel>[] sents = obw.toArray(new List[0]);
      int outIdx = 0;
      for (List<CoreLabel> sent : sents) {
        for (CoreLabel cl : sent) {
          String tok = cl.word();
          String shape = cl.get(CoreAnnotations.ShapeAnnotation.class);
          Assert.assertEquals(output[outIdx], tok);
          Assert.assertEquals(outWSs[outIdx], shape);
          outIdx++;
        }
      }

      if (outIdx < output.length) {
        Assert.fail("Too few things in iterator, lacking: " + output[outIdx]);
      }
    } catch (Exception e) {
      Assert.fail("Probably too many things in iterator: " + e);
    }
  }

}