package edu.stanford.nlp.international.spanish.scripts; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.international.spanish.SpanishTreeReaderFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;

/**
 * This script converts a PTB tree into TSV suitable for NER classification. The
 * input is an AnCora treebank file with NER tags, and the output is a TSV file
 * with tab-seperated word-class pairs, one word per file. These can be used with
 * the CRFClassifier for training or testing. 
 */
public class TreeToTSV  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TreeToTSV.class);

  public static void main(String[] args) {
    if(args.length < 1) {
      System.err.printf("Usage: java %s tree_file%n", TreeToTSV.class.getName());
      System.exit(-1);
    }

    String treeFile = args[0];

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new SpanishTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      StringBuilder sb = new StringBuilder();
      String nl = System.getProperty("line.separator");

      Pattern nePattern = Pattern.compile("^grup\\.nom\\.");
      Pattern npPattern = Pattern.compile("^np0000.$");

      for (Tree tree; (tree = tr.readTree()) != null;) {
        for(Tree t : tree) {
          if(!t.isPreTerminal())
            continue;

          char type = 'O';
          Tree grandma = t.ancestor(1, tree);
          String grandmaValue = ((CoreLabel) grandma.label()).value();

          // grup.nom.x
          if(nePattern.matcher(grandmaValue).find())
            type = grandmaValue.charAt(9);

          // else check the pos for np0000x or not
          else {
            String pos = ((CoreLabel) t.label()).value();
            if(npPattern.matcher(pos).find())
              type = pos.charAt(6);
          }

          Tree wordNode = t.firstChild();
          String word = ((CoreLabel) wordNode.label()).value();
          sb.append(word).append("\t");
 
          switch(type) {
          case 'p':
            sb.append("PERS");
            break;
          case 'l':
            sb.append("LUG");
            break;
          case 'o':
            sb.append("ORG");
            break;
          case '0':
            sb.append("OTROS");
            break;
          default:
            sb.append("O");
          }
          sb.append(nl);
        }
        sb.append(nl);
      }
      System.out.print(sb.toString());

      tr.close();
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
