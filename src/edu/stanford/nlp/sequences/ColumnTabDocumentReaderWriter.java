package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.CoreTokenFactory;
import edu.stanford.nlp.util.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Version of ColumnDocumentReaderAndWriter that doesn't read in entire file and
 * stores it in memory before parsing it.
 *
 * Reads in one line at a time. Assumes that sequences are broken up by empty
 * lines.
 *
 * Also differs from ColumnDocumentReaderAndWriter in following ways:
 * <ul>
 *   <li>Splits on tabs (delimiterPattern)</li>
 *   <li>Replaces within field whitespaces with "_" (replaceWhitespace)</li>
 *   <li>Assumes that a line with just one column and starts
 *        with "* xxxxx" indicates the document id (hasDocId)</li>
 * </ul>
 *
 * Accepts the following properties
 * <table>
 *   <tr><th>Field</th><th>Type</th><th>Default</th><th>Description</th></tr>
 *   <tr><td><code>columns</code></td><td>String</td><td><code></code></td><td>Comma separated list of mapping between annotation (see {@link edu.stanford.nlp.ling.AnnotationLookup.KeyLookup}) and column index (starting from 0).  Example: <code>word=0,tag=1</code></td></tr>
 *   <tr><td><code>delimiter</code></td><td>String</td><td><code>\t</code></td><td>Regular expression for delimiter</td></tr>
 *   <tr><td><code>replaceWhitespace</code></td><td>Boolean</td><td><code>true</code></td><td>Replace whitespaces with "_"</td></tr>
 *   <tr><td><code>tokens</code></td><td>Class</td>
 *       <td>{@link edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation}</td>
 *       <td>Annotation field for tokens</td></tr>
 *   <tr><td><code>tokenFactory</code></td><td>Class</td>
 *       <td>{@link CoreLabelTokenFactory edu.stanford.nlp.process.CoreLabelTokenFactory}</td>
 *       <td>Factory for creating tokens</td></tr>
 * </table>
 *
 * @author Angel Chang
 * @author Sonal Gupta (made the class generic)
 */
public class ColumnTabDocumentReaderWriter<IN extends CoreMap> implements DocumentReaderAndWriter<IN> {

  private static final long serialVersionUID = 1;

  private String[] map; // = null;
  private Pattern delimiterPattern = Pattern.compile("\t");
  private Pattern whitespacePattern = Pattern.compile("\\s");
  private boolean replaceWhitespace = true;
  private String tokensAnnotationClassName;
  private CoreTokenFactory<IN> tokenFactory;

  /**
   * reads the tokenFactory and tokensAnnotationClassName from
   * {@link SeqClassifierFlags}
   */
  public void init(SeqClassifierFlags flags) {
    if (flags.tokensAnnotationClassName != null) {
      this.tokensAnnotationClassName = flags.tokensAnnotationClassName;
    } else {
      this.tokensAnnotationClassName = "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation";
    }

    if (flags.tokenFactory != null) {
      try {
        this.tokenFactory = (CoreTokenFactory<IN>) Class.forName(flags.tokenFactory).newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      this.tokenFactory = (CoreTokenFactory<IN>) new CoreLabelTokenFactory();
    }

    init(flags, this.tokenFactory, this.tokensAnnotationClassName);
  }

  public void init(Properties props) {
    init("", props);
  }

  public void init(String name, Properties props) {
    String prefix = (name == null)? "":name + ".";
    String delimiterRegex = props.getProperty(prefix + "delimiter");
    if (delimiterRegex != null) {
      delimiterPattern = Pattern.compile(delimiterRegex);
    }
    replaceWhitespace = PropertiesUtils.getBool(props, prefix + "replaceWhitespace", replaceWhitespace);
    String mapString = props.getProperty(prefix + "columns");
    tokensAnnotationClassName = props.getProperty(prefix + "tokens",
            "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation");
    String tokenFactoryClassName =  props.getProperty(prefix + "tokenFactory");
    if (tokenFactoryClassName != null) {
      try {
        this.tokenFactory = (CoreTokenFactory<IN>) Class.forName(tokenFactoryClassName).newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      this.tokenFactory = (CoreTokenFactory<IN>) new CoreLabelTokenFactory();
    }
    init(mapString, this.tokenFactory, this.tokensAnnotationClassName);
  }

  public void init(String map) {
    init(map, (CoreTokenFactory<IN>) new CoreLabelTokenFactory(),
        "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation");
  }

  public void init(SeqClassifierFlags flags, CoreTokenFactory<IN> tokenFactory, String tokensAnnotationClassName) {
    this.map = StringUtils.mapStringToArray(flags.map);
    this.tokenFactory = tokenFactory;
    this.tokensAnnotationClassName = tokensAnnotationClassName;
  }

  public void init(String map, CoreTokenFactory<IN> tokenFactory, String tokensAnnotationClassName) {
    this.map = StringUtils.mapStringToArray(map);
    this.tokenFactory = tokenFactory;
    this.tokensAnnotationClassName = tokensAnnotationClassName;
  }

  public Iterator<List<IN>> getIterator(Reader r) {
    BufferedReader br;
    if (r instanceof BufferedReader) {
      br = (BufferedReader) r;
    } else {
      br = new BufferedReader(r);
    }
    return new BufferedReaderIterator<List<IN>>(new ColumnDocBufferedGetNextTokens(br));
  }

  public Iterator<Annotation> getDocIterator(Reader r) {
    BufferedReader br;
    if (r instanceof BufferedReader) {
      br = (BufferedReader) r;
    } else {
      br = new BufferedReader(r);
    }
    return new BufferedReaderIterator<Annotation>(new ColumnDocBufferedGetNext(br, false));
  }

  public Iterator<Annotation> getDocIterator(Reader r, boolean includeText) {
    BufferedReader br;
    if (r instanceof BufferedReader) {
      br = (BufferedReader) r;
    } else {
      br = new BufferedReader(r);
    }
    return new BufferedReaderIterator<Annotation>(new ColumnDocBufferedGetNext(br, false, includeText));
  }

  private static interface GetNextFunction<E> {
    public E getNext();
  }

  private static class BufferedReaderIterator<E> extends AbstractIterator<E> {
    E nextItem;
    GetNextFunction<E> getNextFunc;

    public BufferedReaderIterator(GetNextFunction<E> getNextFunc) {
      this.getNextFunc = getNextFunc;
      this.nextItem = getNextFunc.getNext();
    }

    public boolean hasNext() {
      return nextItem != null;
    };

    public E next() {
      if (nextItem == null) {
        throw new NoSuchElementException();
      }
      E item = nextItem;
      nextItem = getNextFunc.getNext();
      return item;
    }
  }

  private class ColumnDocBufferedGetNextTokens<IN extends CoreMap> implements GetNextFunction<List<IN>> {
    ColumnDocBufferedGetNext docGetNext;

    public ColumnDocBufferedGetNextTokens(BufferedReader br) {
      docGetNext = new ColumnDocBufferedGetNext(br, true);
    }

    public List<IN> getNext() {
      try {
        CoreMap m = docGetNext.getNext();
        Class tokensAnnotationClass = Class.forName(tokensAnnotationClassName);
        return (List<IN>) ((m != null) ? m.get(tokensAnnotationClass) : null);
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }

      return null;
    }
  }

  private static <IN extends CoreMap> String join(Iterable<IN> l, Class textKey, String glue) {
    StringBuilder sb = new StringBuilder();
    for (IN o : l) {
      if (sb.length() > 0) {
        sb.append(glue);
      }
      sb.append(o.get(textKey));
    }
    return sb.toString();
  }

  private class ColumnDocBufferedGetNext implements GetNextFunction<Annotation> {
    private BufferedReader br;
    boolean includeText = false;
    boolean keepBoundaries = false;
    boolean returnTokensOnEmptyLine = true;
    boolean hasDocId = true;
    boolean hasDocStart = false;
    String docId;
    String newDocId;
    int itemCnt = 0;
    int lineCnt = 0;

    public ColumnDocBufferedGetNext(BufferedReader br) {
      this(br, true, false);
    }

    public ColumnDocBufferedGetNext(BufferedReader br, boolean returnSegmentsAsDocs) {
      this(br, returnSegmentsAsDocs, false);
    }

    public ColumnDocBufferedGetNext(BufferedReader br, boolean returnSegmentsAsDocs, boolean includeText) {
      this.br = br;
      this.includeText = includeText;
      if (returnSegmentsAsDocs) {
        keepBoundaries = false;
        returnTokensOnEmptyLine = true;
        hasDocStart = false;
      } else {
        keepBoundaries = true;
        returnTokensOnEmptyLine = false;
        hasDocStart = true;
      }
    }

    private Annotation createDoc(String docId, List<IN> tokens, List<IntPair> sentenceBoundaries, boolean includeText) {
      try {
        String docText = includeText ? join(tokens, CoreAnnotations.TextAnnotation.class, " ") : null;
        Annotation doc = new Annotation(docText);
        doc.set(CoreAnnotations.DocIDAnnotation.class, docId);
        Class tokensClass = Class.forName(tokensAnnotationClassName);
        doc.set(tokensClass, tokens);
        boolean setTokenCharOffsets = includeText;
        if (setTokenCharOffsets) {
          int i = 0;
          for (IN token : tokens) {
            String tokenText = token.get(CoreAnnotations.TextAnnotation.class);
            token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, i);
            i += tokenText.length();
            token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, i);
            /*
             * if (i > docText.length()) { System.err.println("index " + i +
             * " larger than docText length " + docText.length());
             * System.err.println("Token: " + tokenText);
             * System.err.println("DocText: " + docText); }
             */
            assert (i <= docText.length());
            i++; // Skip space
          }
        }
        if (sentenceBoundaries != null) {
          List<CoreMap> sentences = new ArrayList<CoreMap>(sentenceBoundaries.size());
          for (IntPair p : sentenceBoundaries) {
            // get the sentence text from the first and last character offsets
            List<IN> sentenceTokens = new ArrayList<IN>(tokens.subList(p.getSource(), p.getTarget() + 1));
            Integer begin = sentenceTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
            int last = sentenceTokens.size() - 1;
            Integer end = sentenceTokens.get(last).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
            String sentenceText = includeText ? join(sentenceTokens, CoreAnnotations.TextAnnotation.class, " ") : null;

            // create a sentence annotation with text and token offsets
            Annotation sentence = new Annotation(sentenceText);
            sentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
            sentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
            sentence.set(tokensClass, sentenceTokens);
            sentence.set(CoreAnnotations.TokenBeginAnnotation.class, p.getSource());
            sentence.set(CoreAnnotations.TokenEndAnnotation.class, p.getTarget() + 1);
            int sentenceIndex = sentences.size();
            sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex);

            // add the sentence to the list
            sentences.add(sentence);
          }
          // add the sentences annotations to the document
          doc.set(CoreAnnotations.SentencesAnnotation.class, sentences);
        }
        return doc;
      } catch (ClassNotFoundException e) {
        e.printStackTrace(System.err);
      }
      return null;
    }

    private void markBoundary(List<IN> words, List<IntPair> boundaries) {
      if (words != null && !words.isEmpty()) {
        int curWordIndex = words.size() - 1;
        if (boundaries.isEmpty()) {
          boundaries.add(new IntPair(0, curWordIndex));
        } else {
          int lastWordIndex = boundaries.get(boundaries.size() - 1).getTarget();
          if (lastWordIndex < curWordIndex) {
            boundaries.add(new IntPair(lastWordIndex + 1, curWordIndex));
          }
        }
      }
    }

    public Annotation getNext() {
      if (itemCnt > 0 && itemCnt % 1000 == 0) {
        System.err.print("[" + itemCnt + "," + lineCnt + "]");
        if (itemCnt % 10000 == 9000) {
          System.err.println();
        }
      }
      try {
        String line;
        List<IN> words = null;
        List<IntPair> boundaries = null;
        if (keepBoundaries) {
          boundaries = new ArrayList<IntPair>();
        }
        while ((line = br.readLine()) != null) {
          lineCnt++;
          line = line.trim();
          if (line.length() != 0) {
            String[] info = delimiterPattern.split(line);
            if (replaceWhitespace) {
              for (int i = 0; i < info.length; i++) {
                info[i] = whitespacePattern.matcher(info[i]).replaceAll("_");
              }
            }
            if (hasDocId && line.startsWith("* ") && info.length == 1) {
              newDocId = line.substring(2);
              if (words != null) {
                return createDoc(docId, words, boundaries, includeText);
              }
            } else if (hasDocStart && "-DOCSTART-".equals(info[0])) {
              newDocId = "doc" + itemCnt;
              if (words != null) {
                if (keepBoundaries) {
                  markBoundary(words, boundaries);
                }
                return createDoc(docId, words, boundaries, includeText);
              }
            } else {
              if (words == null) {
                words = new ArrayList<IN>();
                docId = newDocId;
                itemCnt++;
              }
              IN wi;
              if (info.length == map.length) {
                wi = tokenFactory.makeToken(map, info);
              } else {
                wi = tokenFactory.makeToken(map, Arrays.asList(info).subList(0, map.length).toArray(new String[map.length]));
              }
              words.add(wi);
            }
          } else {
            if (returnTokensOnEmptyLine && words != null) {
              if (keepBoundaries) {
                markBoundary(words, boundaries);
              }
              return createDoc(docId, words, boundaries, includeText);
            } else if (keepBoundaries) {
              markBoundary(words, boundaries);
            }
          }
        }
        if (words == null) {
          System.err.println("[" + itemCnt + "," + lineCnt + "]");
        }
        if (keepBoundaries) {
          markBoundary(words, boundaries);
        }
        return (words == null) ? null : createDoc(docId, words, boundaries, includeText);
      } catch (IOException ex) {
        System.err.println("IOException: " + ex);
        throw new RuntimeException(ex);
      }
    }

  } // end class ColumnDocParser

  public void printAnswers(List<IN> doc, PrintWriter out) {
    for (IN wi : doc) {
      String answer = wi.get(CoreAnnotations.AnswerAnnotation.class);
      String goldAnswer = wi.get(CoreAnnotations.GoldAnswerAnnotation.class);
      String tokenStr = StringUtils.getNotNullString(wi.get(CoreAnnotations.TextAnnotation.class));
      out.println(tokenStr + "\t" + goldAnswer + "\t" + answer);
    }
    out.println();
  }

}