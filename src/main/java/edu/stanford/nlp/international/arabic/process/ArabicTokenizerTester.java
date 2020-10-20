package edu.stanford.nlp.international.arabic.process; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.international.arabic.pipeline.DefaultLexicalMapper;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.treebank.Mapper;

/**
 * Compares the output of the JFlex-based ArabicTokenizer to DefaultLexicalMapper, which
 * is used in the parser and elsewhere.
 *
 * @author Spence Green
 *
 */
public class ArabicTokenizerTester  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ArabicTokenizerTester.class);

  /**
   * arg[0] := tokenizer options
   * args[1] := file to tokenize
   *
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      System.out.printf("Usage: java %s OPTS filename%n", ArabicTokenizerTester.class.getName());
      System.exit(-1);
    }
    String tokOptions = args[0];
    File path = new File(args[1]);
    log.info("Reading from: " + path.getPath());
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
      TokenizerFactory<CoreLabel> tf = ArabicTokenizer.factory();
      tf.setOptions(tokOptions);
      Mapper lexMapper = new DefaultLexicalMapper();
      lexMapper.setup(null, "StripSegMarkersInUTF8", "StripMorphMarkersInUTF8");

      int lineId = 0;
      for(String line; (line = br.readLine()) != null; lineId++) {
        line = line.trim();

        // Tokenize with the tokenizer
        List<CoreLabel> tokenizedLine = tf.getTokenizer(new StringReader(line)).tokenize();
        System.out.println(SentenceUtils.listToString(tokenizedLine));

        // Tokenize with the mapper
        StringBuilder sb = new StringBuilder();
        String[] toks = line.split("\\s+");
        for (String tok : toks) {
          String mappedTok = lexMapper.map(null, tok);
          sb.append(mappedTok).append(" ");
        }
        List<String> mappedToks = Arrays.asList(sb.toString().trim().split("\\s+"));

        // Evaluate the output
        if (mappedToks.size() != tokenizedLine.size()) {
          System.err.printf("Line length mismatch:%norig: %s%ntok: %s%nmap: %s%n%n",
              line,
              SentenceUtils.listToString(tokenizedLine),
              SentenceUtils.listToString(mappedToks));
        } else {
          boolean printLines = false;
          for (int i = 0; i < mappedToks.size(); ++i) {
            String mappedTok = mappedToks.get(i);
            String tokenizedTok = tokenizedLine.get(i).word();
            if ( ! mappedTok.equals(tokenizedTok)) {
              System.err.printf("Token mismatch:%nmap: %s%ntok: %s%n", mappedTok, tokenizedTok);
              printLines = true;
            }
          }
          if (printLines) {
            System.err.printf("orig: %s%ntok: %s%nmap: %s%n%n",
                line,
                SentenceUtils.listToString(tokenizedLine),
                SentenceUtils.listToString(mappedToks));
          }
        }
      }

      System.err.printf("Read %d lines.%n", lineId);

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
