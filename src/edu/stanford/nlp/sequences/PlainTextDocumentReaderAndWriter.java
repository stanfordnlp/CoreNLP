package edu.stanford.nlp.sequences; 

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.XMLUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;

/**
 * This class provides methods for reading plain text documents and writing out
 * those documents once classified in several different formats.
 * The output formats are named: slashTags, xml, inlineXML, tsv, tabbedEntities.
 *
 * <i>Implementation note:</i> see
 * itest/src/edu/stanford/nlp/ie/crf/CRFClassifierITest.java for examples and
 * test cases for the output options.
 *
 * This class works over a list of anything that extends {@link CoreMap}.
 * The usual case is {@link CoreLabel}.
 *
 * @author Jenny Finkel
 * @author Christopher Manning (new output options organization)
 * @author Sonal Gupta (made the class generic)
 */
public class PlainTextDocumentReaderAndWriter<IN extends CoreMap> implements DocumentReaderAndWriter<IN>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(PlainTextDocumentReaderAndWriter.class);

  private static final long serialVersionUID = -2420535144980273136L;

  public enum OutputStyle {
    SLASH_TAGS    ("slashTags"),
    XML           ("xml"),
    INLINE_XML    ("inlineXML"),
    TSV           ("tsv"),
    TABBED        ("tabbedEntities");

    private final String shortName;
    OutputStyle(String shortName) {
      this.shortName = shortName;
    }

    private static final Map<String, OutputStyle> shortNames = Generics.newHashMap();

    static {
      for (OutputStyle style : OutputStyle.values())
        shortNames.put(style.shortName, style);
    }

    /** Convert a String expressing an output format to its internal
     *  coding as an OutputStyle.
     *
     *  @param name The String name
     *  @return OutputStyle The internal constant
     */
    public static OutputStyle fromShortName(String name) {
      OutputStyle result = shortNames.get(name);
      if (result == null)
        throw new IllegalArgumentException(name + " is not an OutputStyle");
      return result;
    }

    public static boolean defaultToPreserveSpacing(String str) {
      return str.equals(XML.shortName) || str.equals(INLINE_XML.shortName);
    }

  } // end enum Output style

  private static final Pattern sgml = Pattern.compile("<[^>]*>");
  // TODO: allow for turning off splitting on . if we are processing already tokenized text
  private final WordToSentenceProcessor<IN> wts =
          new WordToSentenceProcessor<>(WordToSentenceProcessor.NewlineIsSentenceBreak.ALWAYS);

  private SeqClassifierFlags flags; // = null;
  private TokenizerFactory<IN> tokenizerFactory;

  /**
   * Construct a PlainTextDocumentReaderAndWriter. You should call init() after
   * using the constructor.
   */
  public PlainTextDocumentReaderAndWriter() {
  }

  @Override
  public void init(SeqClassifierFlags flags) {
    String options = "tokenizeNLs=false,invertible=true";
    if (flags.tokenizerOptions != null) {
      options = options + ',' + flags.tokenizerOptions;
    }
    TokenizerFactory<IN> factory;
    if (flags.tokenizerFactory != null) {
      try {
        Class<TokenizerFactory<? extends HasWord>> clazz = ErasureUtils.uncheckedCast(Class.forName(flags.tokenizerFactory));
        Method factoryMethod = clazz.getMethod("newCoreLabelTokenizerFactory", String.class);
        factory = ErasureUtils.uncheckedCast(factoryMethod.invoke(null, options));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      factory = ErasureUtils.uncheckedCast(PTBTokenizer.PTBTokenizerFactory.newCoreLabelTokenizerFactory(options));
    }
    init(flags, factory);
  }

  public void init(SeqClassifierFlags flags, TokenizerFactory<IN> tokenizerFactory) {
    this.flags = flags;
    this.tokenizerFactory = tokenizerFactory;
  }

  // todo: give options for document splitting. A line or the whole file or sentence splitting as now
  @Override
  public Iterator<List<IN>> getIterator(Reader r) {
    Tokenizer<IN> tokenizer = tokenizerFactory.getTokenizer(r);
    // PTBTokenizer.newPTBTokenizer(r, false, true);
    List<IN> words = new ArrayList<>();
    IN previous = null;
    StringBuilder prepend = new StringBuilder();

    /*
     * This changes SGML tags into whitespace -- it should maybe be moved elsewhere
     */
    while (tokenizer.hasNext()) {
      IN w = tokenizer.next();
      String word = w.get(CoreAnnotations.TextAnnotation.class);
      Matcher m = sgml.matcher(word);
      if (m.matches()) {

        String before = StringUtils.getNotNullString(w.get(CoreAnnotations.BeforeAnnotation.class));
        String after = StringUtils.getNotNullString(w.get(CoreAnnotations.AfterAnnotation.class));
        prepend.append(before).append(word);
        if (previous != null) {
          String previousTokenAfter = StringUtils.getNotNullString(previous.get(CoreAnnotations.AfterAnnotation.class));
          previous.set(CoreAnnotations.AfterAnnotation.class, previousTokenAfter + word + after);
        }
        // previous.appendAfter(w.word() + w.after());
      } else {

        String before = StringUtils.getNotNullString(w.get(CoreAnnotations.BeforeAnnotation.class));
        if (prepend.length() > 0) {
          prepend.append(before);
          w.set(CoreAnnotations.BeforeAnnotation.class, prepend.toString());
          prepend = new StringBuilder();
        }
        words.add(w);
        previous = w;
      }
    }

    List<List<IN>> sentences = wts.process(words);
    String after = "";
    IN last = null;
    for (List<IN> sentence : sentences) {
      int pos = 0;
      for (IN w : sentence) {
        w.set(CoreAnnotations.PositionAnnotation.class, Integer.toString(pos));
        after = StringUtils.getNotNullString(w.get(CoreAnnotations.AfterAnnotation.class));
        w.remove(CoreAnnotations.AfterAnnotation.class);
        last = w;
      }
    }
    if (last != null) {
      last.set(CoreAnnotations.AfterAnnotation.class, after);
    }

    return sentences.iterator();
  }


  /**
   * Print the classifications for the document to the given Writer. This method
   * now checks the {@code outputFormat} property, and can print in
   * slashTags, inlineXML, xml (stand-Off XML), tsv, or a 3-column tabbed format
   * for easy entity retrieval. For both the XML output
   * formats, it preserves spacing, while for the other formats, it prints
   * tokenized (since preserveSpacing output is somewhat dysfunctional with these
   * formats, but you can control this by calling getAnswers()).
   *
   * @param list List of tokens with classifier answers
   * @param out Where to print the output to
   */
  @Override
  public void printAnswers(List<IN> list, PrintWriter out) {
    String style = null;
    if (flags != null) {
      style = flags.outputFormat;
    }
    if (style == null || style.isEmpty()) {
      style = "slashTags";
    }
    OutputStyle outputStyle = OutputStyle.fromShortName(style);
    printAnswers(list, out, outputStyle, OutputStyle.defaultToPreserveSpacing(style));
  }

  public String getAnswers(List<IN> l,
                           OutputStyle outputStyle, boolean preserveSpacing) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    printAnswers(l, pw, outputStyle, preserveSpacing);
    pw.flush();
    return sw.toString();
  }

  public void printAnswers(List<IN> l, PrintWriter out,
                           OutputStyle outputStyle, boolean preserveSpacing) {
    switch (outputStyle) {
    case SLASH_TAGS:
      if (preserveSpacing) {
        printAnswersAsIsText(l, out);
      } else {
        printAnswersTokenizedText(l, out);
      }
      break;
    case XML:
      if (preserveSpacing) {
        printAnswersXML(l, out);
      } else {
        printAnswersTokenizedXML(l, out);
      }
      break;
    case INLINE_XML:
      if (preserveSpacing) {
        printAnswersInlineXML(l, out);
      } else {
        printAnswersTokenizedInlineXML(l, out);
      }
      break;
      case TSV:
        if (preserveSpacing) {
          printAnswersAsIsTextTsv(l, out);
        } else {
          printAnswersTokenizedTextTsv(l, out);
        }
        break;
      case TABBED:
        if (preserveSpacing) {
          printAnswersAsIsTextTabbed(l, out);
        } else {
          printAnswersTokenizedTextTabbed(l, out);
        }
        break;
    default:
      throw new IllegalArgumentException(outputStyle +
                                         " is an unsupported OutputStyle");
    }
  }

  private static <IN extends CoreMap> void printAnswersTokenizedText(List<IN> l, PrintWriter out) {
    for (IN wi : l) {
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class)));
      out.print('/');
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.AnswerAnnotation.class)));
      out.print(' ');
    }
    out.println(); // put a single newline at the end [added 20091024].
  }

  private static <IN extends CoreMap> void printAnswersAsIsText(List<IN> l, PrintWriter out) {
    for (IN wi : l) {
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.BeforeAnnotation.class)));
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class)));
      out.print('/');
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.AnswerAnnotation.class)));
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.AfterAnnotation.class)));
    }
  }

  private static <IN extends CoreMap> void printAnswersTokenizedTextTsv(List<IN> l, PrintWriter out) {
    for (IN wi : l) {
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class)));
      out.print('\t');
      out.println(StringUtils.getNotNullString(wi.get(CoreAnnotations.AnswerAnnotation.class)));
    }
    out.println(); // put a single newline at the end [added 20091024].
  }

  private static <IN extends CoreMap> void printAnswersAsIsTextTsv(List<IN> l, PrintWriter out) {
    for (IN wi : l) {
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.BeforeAnnotation.class)));
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class)));
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.AfterAnnotation.class)));
      out.print('\t');
      out.println(StringUtils.getNotNullString(wi.get(CoreAnnotations.AnswerAnnotation.class)));
    }
  }

  private void printAnswersAsIsTextTabbed(List<IN> l, PrintWriter out) {
    final String background = flags.backgroundSymbol;
    String lastEntityType = null;
    for (IN wi : l) {
      String entityType = wi.get(CoreAnnotations.AnswerAnnotation.class);
      String token = StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class));
      String before = StringUtils.getNotNullString(wi.get(CoreAnnotations.BeforeAnnotation.class));
      String after = StringUtils.getNotNullString(wi.get(CoreAnnotations.AfterAnnotation.class));

      if (entityType.equals(lastEntityType)) {
        // continue the same entity in column 1 or 3
        out.print(before);
        out.print(token);
        out.print(after);
      } else {
        if (lastEntityType != null &&  ! background.equals(lastEntityType)) {
          // different entity type.  If previous not background/start, write in column 2
          out.print('\t');
          out.print(lastEntityType);
        }
        if (background.equals(entityType)) {
          // we'll print it in column 3. Normally, we're in column 2, unless we were at the start of doc
          if (lastEntityType == null) {
            out.print('\t');
          }
          out.print('\t');
        } else {
          // otherwise we're printing in column 1 again
          out.println();
        }
        out.print(before);
        out.print(token);
        out.print(after);
        lastEntityType = entityType;
      }
    }
    // if we're in the middle of printing an entity, then we should print its type
    if (lastEntityType != null && ! background.equals(lastEntityType)) {
      out.print('\t');
      out.print(lastEntityType);
    }
    // finish line then add blank line
    out.println();
    out.println();
  }

  private void printAnswersTokenizedTextTabbed(List<IN> l, PrintWriter out) {
    final String background = flags.backgroundSymbol;
    String lastEntityType = null;
    for (IN wi : l) {
      String entityType = wi.get(CoreAnnotations.AnswerAnnotation.class);
      String token = StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class));
      if (entityType.equals(lastEntityType)) {
        // continue the same entity in column 1 or 3
        out.print(' ');
        out.print(token);
      } else {
        if (lastEntityType != null && ! background.equals(lastEntityType)) {
          // different entity type.  If previous not background/start, write in column 2
          out.print('\t');
          out.print(lastEntityType);
        }
        if (background.equals(entityType)) {
          // we'll print it in column 3. Normally, we're in column 2, unless we were at the start of doc
          if (lastEntityType == null) {
            out.print('\t');
          }
          out.print('\t');
        } else {
          // otherwise we're printing in column 1 again
          out.println();
        }
        out.print(token);
        lastEntityType = entityType;
      }
    }
    // if we're in the middle of printing an entity, then we should print its type
    if (lastEntityType != null && ! background.equals(lastEntityType)) {
      out.print('\t');
      out.print(lastEntityType);
    }
    // finish line then add blank line
    out.println();
    out.println();
  }

  private static <IN extends CoreMap> void printAnswersXML(List<IN> doc, PrintWriter out) {
    int num = 0;
    for (IN wi : doc) {
      String prev = StringUtils.getNotNullString(wi.get(CoreAnnotations.BeforeAnnotation.class));
      out.print(prev);
      out.print("<wi num=\"");
      // tag.append(wi.get("position"));
      out.print(num++);
      out.print("\" entity=\"");
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.AnswerAnnotation.class)));
      out.print("\">");
      out.print(XMLUtils.escapeXML(StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class))));
      out.print("</wi>");
      String after = StringUtils.getNotNullString(wi.get(CoreAnnotations.AfterAnnotation.class));
      out.print(after);
    }
  }

  private static <IN extends CoreMap> void printAnswersTokenizedXML(List<IN> doc, PrintWriter out) {
    int num = 0;
    for (IN wi : doc) {
      out.print("<wi num=\"");
      // tag.append(wi.get("position"));
      out.print(num++);
      out.print("\" entity=\"");
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.AnswerAnnotation.class)));
      out.print("\">");
      out.print(XMLUtils.escapeXML(StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class))));
      out.println("</wi>");
    }
  }

  private void printAnswersInlineXML(List<IN> doc, PrintWriter out) {
    final String background = flags.backgroundSymbol;
    String prevTag = background;
    for (Iterator<IN> wordIter = doc.iterator(); wordIter.hasNext();) {
      IN wi = wordIter.next();
      String tag = StringUtils.getNotNullString(wi.get(CoreAnnotations.AnswerAnnotation.class));

      String before = StringUtils.getNotNullString(wi.get(CoreAnnotations.BeforeAnnotation.class));

      String current = StringUtils.getNotNullString(wi.get(CoreAnnotations.OriginalTextAnnotation.class));
      if (!tag.equals(prevTag)) {
        if (!prevTag.equals(background) && !tag.equals(background)) {
          out.print("</");
          out.print(prevTag);
          out.print('>');
          out.print(before);
          out.print('<');
          out.print(tag);
          out.print('>');
        } else if (!prevTag.equals(background)) {
          out.print("</");
          out.print(prevTag);
          out.print('>');
          out.print(before);
        } else if (!tag.equals(background)) {
          out.print(before);
          out.print('<');
          out.print(tag);
          out.print('>');
        }
      } else {
        out.print(before);
      }
      out.print(current);
      String afterWS = StringUtils.getNotNullString(wi.get(CoreAnnotations.AfterAnnotation.class));

      if (!tag.equals(background) && !wordIter.hasNext()) {
        out.print("</");
        out.print(tag);
        out.print('>');
        prevTag = background;
      } else {
        prevTag = tag;
      }
      out.print(afterWS);
    }
  }

  private void printAnswersTokenizedInlineXML(List<IN> doc, PrintWriter out) {
    final String background = flags.backgroundSymbol;
    String prevTag = background;
    boolean first = true;
    for (Iterator<IN> wordIter = doc.iterator(); wordIter.hasNext();) {
      IN wi = wordIter.next();
      String tag = StringUtils.getNotNullString(wi.get(CoreAnnotations.AnswerAnnotation.class));
      if (!tag.equals(prevTag)) {
        if (!prevTag.equals(background) && !tag.equals(background)) {
          out.print("</");
          out.print(prevTag);
          out.print("> <");
          out.print(tag);
          out.print('>');
        } else if (!prevTag.equals(background)) {
          out.print("</");
          out.print(prevTag);
          out.print("> ");
        } else if (!tag.equals(background)) {
          if (!first) {
            out.print(' ');
          }
          out.print('<');
          out.print(tag);
          out.print('>');
        }
      } else {
        if (!first) {
          out.print(' ');
        }
      }
      first = false;
      out.print(StringUtils.getNotNullString(wi.get(CoreAnnotations.OriginalTextAnnotation.class)));

      if (!wordIter.hasNext()) {
        if (!tag.equals(background)) {
          out.print("</");
          out.print(tag);
          out.print('>');
        }
        out.print(' ');
        prevTag = background;
      } else {
        prevTag = tag;
      }
    }
    out.println();
  }

}

/*
 * This is old stuff from a brief period when this DocumentReaderAndWriter tried
 * to handle treating SGML tags as part of white space, even though they were
 * returned as tokens by the tokenizer. If this is to be revived, it seems like
 * this handling should be moved down into the tokenizer.
 *
 * These first two class declarations used to be in CoreAnnotations. The rest
 * used to be in this class.
 *
 * public static class PrevSGMLAnnotation implements CoreAnnotation<String> {
 * public Class<String> getType() { return String.class; } }
 *
 * public static class AfterSGMLAnnotation implements CoreAnnotation<String> {
 * public Class<String> getType() { return String.class; } }
 *
 *
 *
 * public Iterator<List<IN>> getIterator(Reader r) { PTBTokenizer<IN> ptb =
 * PTBTokenizer.newPTBTokenizer(r, false, true); List firstSplit = new
 * ArrayList(); List<IN> d = new ArrayList<IN>();
 *
 * while (ptb.hasNext()) { IN w = ptb.next(); Matcher m =
 * sgml.matcher(w.word()); if (m.matches()) { if (d.size() > 0) {
 * firstSplit.add(d); d = new ArrayList<IN>(); } firstSplit.add(w); continue; }
 * d.add(w); } if (d.size() > 0) { firstSplit.add(d); }
 *
 * List secondSplit = new ArrayList(); for (Object o : firstSplit) { if (o
 * instanceof List) { secondSplit.addAll(wts.process((List) o)); } else {
 * secondSplit.add(o); } }
 *
 * String prevTags = ""; IN lastWord = null;
 *
 * List<List<IN>> documents = new ArrayList<List<IN>>();
 *
 * boolean first = true;
 *
 * for (Object o : secondSplit) { if (o instanceof List) { List doc = (List) o;
 * List<IN> document = new ArrayList<IN>(); int pos = 0; for (Iterator wordIter
 * = doc.iterator(); wordIter.hasNext(); pos++) { IN w = (IN) wordIter.next();
 * w.set(CoreAnnotations.PositionAnnotation.class, Integer.toString(pos)); if (first &&
 * prevTags.length() > 0) { w.set(PrevSGMLAnnotation.class, prevTags); } first =
 * false; lastWord = w; document.add(w); } documents.add(document); } else {
 * //String tag = ((Word) o).word(); IN word = (IN) o; String tag =
 * word.before() + word.current(); if (first) { log.info(word);
 * prevTags = tag; } else { String t =
 * lastWord.getString(AfterSGMLAnnotation.class); tag = t + tag;
 * lastWord.set(AfterSGMLAnnotation.class, tag); } } }
 *
 * // this is a hack to deal with the incorrect assumption in the above code
 * that // SGML only occurs between sentences and never inside of them. List<IN>
 * allWords = new ArrayList<IN>(); for (List<IN> doc : documents) {
 * allWords.addAll(doc); }
 *
 * List<List<IN>> documentsFinal = wts.process(allWords);
 * log.info(documentsFinal.get(0).get(0)); System.exit(0);
 *
 * return documentsFinal.iterator(); // return documents.iterator(); }
 *
 *
 * public void printAnswersInlineXML(List<IN> doc, PrintWriter out) { final
 * String background = flags.backgroundSymbol; String prevTag = background; for
 * (Iterator<IN> wordIter = doc.iterator(); wordIter.hasNext(); ) { IN wi =
 * wordIter.next(); String prev = wi.getString(PrevSGMLAnnotation.class);
 * out.print(prev); if (prev.length() > 0) { prevTag = background; } String tag
 * = wi.getString(CoreAnnotations.AnswerAnnotation.class); if ( ! tag.equals(prevTag)) { if ( !
 * prevTag.equals(background) && ! tag.equals(background)) { out.print("</");
 * out.print(prevTag); out.print('>');
 * out.print(wi.getString(CoreAnnotations.BeforeAnnotation.class)); out.print('<');
 * out.print(tag); out.print('>'); } else if ( ! prevTag.equals(background)) {
 * out.print("</"); out.print(prevTag); out.print('>');
 * out.print(wi.getString(CoreAnnotations.BeforeAnnotation.class)); } else if ( !
 * tag.equals(background)) { out.print(wi.getString(CoreAnnotations.BeforeAnnotation.class));
 * out.print('<'); out.print(tag); out.print('>'); } } else {
 * out.print(wi.getString(CoreAnnotations.BeforeAnnotation.class)); }
 * out.print(wi.getString(CoreAnnotations.OriginalTextAnnotation.class)); String after =
 * wi.getString(AfterSGMLAnnotation.class); String afterWS =
 * wi.getString(CoreAnnotations.AfterAnnotation.class);
 *
 * if ( ! tag.equals(background) && ( ! wordIter.hasNext() || after.length() >
 * 0)) { out.print("</"); out.print(tag); out.print('>'); prevTag = background;
 * } else { prevTag = tag; } out.print(afterWS); out.print(after); } }
 */
