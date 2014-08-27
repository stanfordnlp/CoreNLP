package edu.stanford.nlp.international.spanish.scripts;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.io.ReaderInputStream;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasCategory;
import edu.stanford.nlp.ling.HasContext;
import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.HasLemma;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.XMLUtils;import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.trees.international.spanish.SpanishTreeReaderFactory;

public class TreeToTSV {

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
