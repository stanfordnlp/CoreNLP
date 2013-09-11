package edu.stanford.nlp.ie;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ChunkAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IDAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PositionAnnotation;
import edu.stanford.nlp.objectbank.DelimitIterator;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Function;

/**
 * DocumentReader for BioCreative format.
 *
 * @author Jenny Finkel
 * @author Huy Nguyen
 */
public class BioCreativeDocumentIteratorFactory extends DelimitIterator.DelimitIteratorFactory<List<CoreLabel>> 
   //extends ColumnDocumentIteratorFactory
{

  public BioCreativeDocumentIteratorFactory() {
    super("\n\\s*\n", new BioCreativeDocumentParser(), false);
  }

  public BioCreativeDocumentIteratorFactory(String[] map, boolean intern) {
    super("\n\\s*\n", new BioCreativeDocumentParser(), false);
    BioCreativeDocumentParser.map = map;
    BioCreativeDocumentParser.intern = intern;
  }

  public  void printAnswers(List<List<CoreLabel>> l) {
    boolean inGene = false;
    String start = "", end = "", gene = "";
    String lastID = "";

    Iterator<List<CoreLabel>> docIter = l.iterator();
    while (docIter.hasNext()) {
      List<CoreLabel> doc = docIter.next();
      Iterator<CoreLabel> wordIter = doc.iterator();
      while (wordIter.hasNext()) {
        CoreLabel lineInfo = wordIter.next();
        if (inGene) {
          if (lineInfo.get(AnswerAnnotation.class).equals("G")) {
            gene += " " + lineInfo.word();
            end = lineInfo.get(PositionAnnotation.class);
          } else {
            System.out.println(lineInfo.get(IDAnnotation.class) + "|" + start + " " + end + "|" + gene);
            inGene = false;
          }
        } else if (lineInfo.get(AnswerAnnotation.class).equals("G")) {
          gene = lineInfo.word();
          start = lineInfo.get(PositionAnnotation.class);
          end = lineInfo.get(PositionAnnotation.class);
          inGene = true;
        }
        lastID = lineInfo.get(IDAnnotation.class);
      }
      if (inGene) {
        if (gene.trim().length() > 0) {
          System.out.println(lastID + "|" + start + " " + end + "|" + gene);
        }
        inGene = false;
      }
    }
  }

  /**
   * Breaks a String into a Document containing BioCreativeWords.
   */
  private static class BioCreativeDocumentParser implements Function<String, List<CoreLabel>> {

    private static String defaultMap = "word=0,tag=1,answer=2";
    public static boolean intern = true;
    public static String[] map = StringUtils.mapStringToArray(defaultMap);
    private static final String GENE = ("G").intern();
    private static final String OTHER = ("O").intern();

    private static String intern(String s) {
      if (intern) {
        return s.trim().intern();
      } else {
        return s.trim();
      }
    }

    public List<CoreLabel> apply(String doc) {
      if (doc == null) {
        return null;
      }

      List<CoreLabel> words = new ArrayList<CoreLabel>();

      String[] lines = doc.split("\n");
      String id = "";
      int position = 0;
      for (int i = 0; i < lines.length; i++) {
        lines[i] = lines[i].trim();
        if (lines[i].length() == 0) {
          continue;
        }
        if (lines[i].startsWith("@@")) {
          id = intern(lines[i]);
          position = 0;
          continue;
        }
        String[] info = lines[i].split("\\s+");

        for (int j = 0; j < info.length; j++) {
          intern(info[j]);
        }

        CoreLabel wi = new CoreLabel(map, info);
        wi.set(IDAnnotation.class, id);
        wi.set(ChunkAnnotation.class, intern("X"));
        wi.set(PositionAnnotation.class, intern("" + position));
        if (wi.get(AnswerAnnotation.class).length() > 0) {
          if (wi.get(AnswerAnnotation.class).startsWith("NEWGENE")) {
            wi.set(AnswerAnnotation.class, GENE);
          } else {
            wi.set(AnswerAnnotation.class, OTHER);
          }
          wi.set(GoldAnswerAnnotation.class, wi.get(AnswerAnnotation.class));
        }
        words.add(wi);
        position++;
      }

      return words;
    }
  }
}
