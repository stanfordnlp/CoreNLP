package edu.stanford.nlp.time; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.Builder;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import java.util.function.Function;
import edu.stanford.nlp.util.Iterables;


/**
 * @author Karthik Raghunathan
 */
public class ParsedGigawordReader implements Iterable<Annotation>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ParsedGigawordReader.class);

  private Iterable<File> files;

  public ParsedGigawordReader(File directory) {
    this.files = IOUtils.iterFilesRecursive(directory);
  }

  @Override
  public Iterator<Annotation> iterator() {
    return new Iterator<Annotation>() {
      private Iterator<BufferedReader> readers = Iterables.transform(files,
          file -> IOUtils.readerFromFile(file)).iterator();

      private BufferedReader reader = findReader();
      private Annotation annotation = findAnnotation();

      @Override
      public boolean hasNext() {
        return this.annotation != null;
      }

      @Override
      public Annotation next() {
        if (this.annotation == null) {
          throw new NoSuchElementException();
        }
        Annotation toReturn = this.annotation;
        this.annotation = this.findAnnotation();
        return toReturn;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

      private BufferedReader findReader() {
        return this.readers.hasNext() ? this.readers.next() : null;
      }

      private Annotation findAnnotation() {
        if (this.reader == null) {
          return null;
        }
        try {
          String line;
          StringBuilder doc = new StringBuilder();
          while ((line = this.reader.readLine()) != null) {
            doc.append(line);
            doc.append('\n');
//            if(line.contains("<DOC id")){
//              log.info(line);
//            }
            if (line.equals("</DOC>")) {
              break;
            }
            if (line.contains("</DOC>")) {
              throw new RuntimeException(String.format("invalid line '%s'", line));
            }
          }
          if (line == null) {
            this.reader.close();
            this.reader = findReader();
          }
          String xml = doc.toString().replaceAll("&", "&amp;");
          if(xml == null || xml.equals("")) {
            return findAnnotation();
          }

          xml = xml.replaceAll("num=([0-9]+) (.*)", "num=\"$1\" $2");
          xml = xml.replaceAll("sid=(.*)>", "sid=\"$1\">");
          xml = xml.replaceAll("</SENT>\n</DOC>", "</SENT>\n</TEXT>\n</DOC>");
          xml = new String(xml.getBytes(), "UTF8");
          //log.info("This is what goes in:\n" + xml);
          return toAnnotation(xml);
        } catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }
    };
  }

  private static final Pattern datePattern = Pattern.compile("^\\w+_\\w+_(\\d+)\\.");

  /*
   * Old implementation based on JDOM.
   * No longer maintained due to JDOM licensing issues.
  private static Annotation toAnnotation(String xml) throws IOException {
    Element docElem;
    try {
      docElem = new SAXBuilder().build(new StringReader(xml)).getRootElement();
    } catch (JDOMException e) {
      throw new RuntimeException(String.format("error:\n%s\ninput:\n%s", e, xml));
    }
    Element textElem = docElem.getChild("TEXT");
    StringBuilder text = new StringBuilder();
    int offset = 0;
    List<CoreMap> sentences = new ArrayList<CoreMap>();
    for (Object sentObj: textElem.getChildren("SENT")) {
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, offset);
      Element sentElem = (Element)sentObj;
      Tree tree = Tree.valueOf(sentElem.getText());
      List<CoreLabel> tokens = new ArrayList<CoreLabel>();
      List<Tree> preTerminals = preTerminals(tree);
      for (Tree preTerminal: preTerminals) {
        String posTag = preTerminal.value();
        for (Tree wordTree: preTerminal.children()) {
          String word = wordTree.value();
          CoreLabel token = new CoreLabel();
          token.set(CoreAnnotations.TextAnnotation.class, word);
          token.set(CoreAnnotations.TextAnnotation.class, word);
          token.set(CoreAnnotations.PartOfSpeechAnnotation.class, posTag);
          token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, offset);
          offset += word.length();
          token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, offset);
          text.append(word);
          text.append(' ');
          offset += 1;
          tokens.add(token);
        }
      }
      if (preTerminals.size() > 0) {
        text.setCharAt(text.length() - 1, '\n');
      }
      sentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, offset - 1);
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
      sentence.set(TreeCoreAnnotations.TreeAnnotation.class, tree);
      sentences.add(sentence);
    }

    String docID = docElem.getAttributeValue("id");
    Matcher matcher = datePattern.matcher(docID);
    matcher.find();
    Calendar docDate = new Timex(matcher.group(1)).getDate();

    Annotation document = new Annotation(text.toString());
    document.set(CoreAnnotations.DocIDAnnotation.class, docID);
    document.set(CoreAnnotations.CalendarAnnotation.class, docDate);
    document.set(CoreAnnotations.SentencesAnnotation.class, sentences);
    return document;
  }
  */

  private static Annotation toAnnotation(String xml) throws IOException {
    Element docElem;
    try {
      Builder parser = new Builder();
      StringReader in = new StringReader(xml);
      docElem = parser.build(in).getRootElement();
    } catch (ParsingException | IOException e) {
      throw new RuntimeException(String.format("error:\n%s\ninput:\n%s", e, xml));
    }

    Element textElem = docElem.getFirstChildElement("TEXT");
    StringBuilder text = new StringBuilder();
    int offset = 0;
    List<CoreMap> sentences = new ArrayList<>();
    Elements sentenceElements = textElem.getChildElements("SENT");
    for (int crtsent = 0; crtsent < sentenceElements.size(); crtsent ++){
      Element sentElem = sentenceElements.get(crtsent);
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, offset);
      Tree tree = Tree.valueOf(sentElem.getChild(0).getValue()); // XXX ms: is this the same as sentElem.getText() in JDOM?
      List<CoreLabel> tokens = new ArrayList<>();
      List<Tree> preTerminals = preTerminals(tree);
      for (Tree preTerminal: preTerminals) {
        String posTag = preTerminal.value();
        for (Tree wordTree: preTerminal.children()) {
          String word = wordTree.value();
          CoreLabel token = new CoreLabel();
          token.set(CoreAnnotations.TextAnnotation.class, word);
          token.set(CoreAnnotations.TextAnnotation.class, word);
          token.set(CoreAnnotations.PartOfSpeechAnnotation.class, posTag);
          token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, offset);
          offset += word.length();
          token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, offset);
          text.append(word);
          text.append(' ');
          offset += 1;
          tokens.add(token);
        }
      }
      if (preTerminals.size() > 0) {
        text.setCharAt(text.length() - 1, '\n');
      }
      sentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, offset - 1);
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
      sentence.set(TreeCoreAnnotations.TreeAnnotation.class, tree);
      sentences.add(sentence);
    }

    String docID = docElem.getAttributeValue("id");
    Matcher matcher = datePattern.matcher(docID);
    matcher.find();
    Calendar docDate = new Timex("DATE", matcher.group(1)).getDate();

    Annotation document = new Annotation(text.toString());
    document.set(CoreAnnotations.DocIDAnnotation.class, docID);
    document.set(CoreAnnotations.CalendarAnnotation.class, docDate);
    document.set(CoreAnnotations.SentencesAnnotation.class, sentences);
    return document;
  }

  // todo [cdm 2013]: replace the methods below with ones in Tree?
  // It depends on whether the code is somehow using preterminals with multiple children.

  private static List<Tree> preTerminals(Tree tree) {
    List<Tree> preTerminals = new ArrayList<>();
    for (Tree descendant: tree) {
      if (isPreterminal(descendant)) {
        preTerminals.add(descendant);
      }
    }
    return preTerminals;
  }

  private static boolean isPreterminal(Tree tree) {
    if (tree.isLeaf()) {
      return false;
    }
    for (Tree child: tree.children()) {
      if (!child.isLeaf()) {
        return false;
      }
    }
    return true;
  }

}
