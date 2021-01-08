package edu.stanford.nlp.wordseg;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

public class NonDict2  {

  //public String sighanCorporaDict = "/u/nlp/data/chinese-segmenter/";
  public static final String DEFAULT_HOME = "/u/nlp/data/gale/segtool/stanford-seg/data/";
  public final String corporaDict;
  private final CorpusDictionary cd;

  private static Redwood.RedwoodChannels logger = Redwood.channels(NonDict2.class);

  public NonDict2(SeqClassifierFlags flags) {
    if (flags.sighanCorporaDict != null) {
      corporaDict = flags.sighanCorporaDict; // use the same flag for Sighan 2005,
      // but our list is extracted from ctb
    } else {
      corporaDict = DEFAULT_HOME;
    }

    String path;
    if (flags.dict2name != null && !flags.dict2name.equals("")) {
      path = corporaDict + "/dict/" + flags.dict2name;
      logger.info("INFO: dict2name specified | building NonDict2 from "+path);
    } else if (flags.useAs || flags.useHk || flags.useMsr) {
      throw new RuntimeException("only support settings for CTB and PKU now.");
    } else if ( flags.usePk ) {
      path = corporaDict+"/dict/pku.non";
      logger.info("INFO: flags.usePk=true | building NonDict2 from "+path);
    } else { // CTB
      path = corporaDict+"/dict/ctb.non";
      logger.info("INFO: flags.usePk=false | building NonDict2 from "+path);
    }

    cd = new CorpusDictionary(path);
  }

  public String checkDic(String c2, SeqClassifierFlags flags) {
    if (cd.getW(c2).equals("1")) {
      return "1";
    } 
    return "0";
  }

  /**
   * Rebuilds a non-dict.  Use -textFile and -outputFile as appropriate.
   * Uses SeqClassifierFlags so that specific flags for the reader can be honored.
   */
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args, SeqClassifierFlags.flagsToNumArgs());

    /*
    // TODO: refactor this into a util?
    // TODO: whitespace reader
    boolean foundReader = false;
    for (String propKey : props.stringPropertyNames()) {
      if (propKey.equalsIgnoreCase("plainTextDocumentReaderAndWriter")) {
        foundReader = true;
        break;
      }
    }
    if (!foundReader) {
      // this doesn't exist
      props.setProperty("plainTextDocumentReaderAndWriter", "edu.stanford.nlp.sequences.WhitespaceDocumentReaderAndWriter");
    }
    */

    SeqClassifierFlags flags = new SeqClassifierFlags(props);

    String inputFilename = flags.textFile;
    String outputFilename = flags.outputFile;

    DocumentReaderAndWriter<CoreLabel> readerAndWriter = AbstractSequenceClassifier.makePlainTextReaderAndWriter(flags);
    readerAndWriter.init(flags);

    Set<String> splitBigrams = new HashSet<>();

    FileReader fin = new FileReader(inputFilename);
    // for some weird syntax reason this can't take the place of ': iterable'
    Iterable<List<CoreLabel>> iterable = () -> readerAndWriter.getIterator(fin);
    List<CoreLabel> prevSentence = null;
    for (List<CoreLabel> sentence : iterable) {
      for (int i = 0; i < sentence.size() - 1; ++i) {
        String prevWord = sentence.get(i).value();
        String nextWord = sentence.get(i+1).value();
        String bigram = prevWord.substring(prevWord.length() - 1) + nextWord.substring(0, 1);
        splitBigrams.add(bigram);
      }
      if (prevSentence != null) {
        String prevWord = prevSentence.get(prevSentence.size() - 1).value();
        String nextWord = sentence.get(0).value();
        String bigram = prevWord.substring(prevWord.length() - 1) + nextWord.substring(0, 1);
        splitBigrams.add(bigram);
      }
      prevSentence = sentence;
    }
    fin.close();

    PrintWriter fout = IOUtils.getPrintWriter(outputFilename, "utf-8");
    for (String bigram : splitBigrams) {
      fout.print(bigram);
      fout.println();
    }
    fout.close();
  }
}
