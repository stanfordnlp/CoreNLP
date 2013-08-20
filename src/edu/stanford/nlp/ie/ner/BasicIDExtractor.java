package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

import edu.stanford.nlp.ie.ner.BioCreativeGeneNormalizer.GeneEvidencePair;
import edu.stanford.nlp.ie.ner.BioCreativeGeneNormalizer.GeneIDPair;

/**
 * <p>A simple gene ID extractor for BioCreative task 1B, which matches the text against regular expressions
 * built from the synonym dictionary, and prints the gene IDs associated with the matched synonym.
 * This is meant to be a baseline model, which tests the recall of the regular expression matching system.</p>
 * <p/>
 * <p>Usage: BasicIDExtractor <synonym file> <test file> <tagged file> [-all] [-disambiguate <train file>] [-acceptNN] [-acceptCD] [-acceptJJ]</p>
 * <ul>
 * <li> synonym file - a file containing the synonym dictionary. This file should contain one gene id per line, followed by a tab delimited list of synonyms</li>
 * <li> test file - the input file.  This file should have one document per line, with the first word being the document id (@@12345), followed by plain text.</li>
 * <li> tagged file - a POS tagged version of test file.  Right now, these are two separate files, which have to be aligned properly, which is really lame.</li>
 * <li> -all - if specified prints both ambiguous and unambiguous ids.  otherwise, prints only the unambiguous ones.
 * <li> -disambiguate - whether to perform disambiguation using co-occurrence statistics.</li>
 * <li> train file - if disambiguate is specified, the file used to obtain the co-occurrence statistics. {@link CooccurrenceModel#initialize}</li>
 * <li> -acceptNN/CD/JJ - accepts matches with the given POS tags, rejects all other matches </li>
 * </ul>
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class BasicIDExtractor {
  /**
   * Runs the BasicIDExtractor.
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("Usage: BasicIDExtractor <synonym file> <test file> <tagged file> [-all] [-disambiguate <train file>] [-acceptNN] [-acceptCD] [-acceptJJ]");
      System.exit(1);
    }

    boolean printAll = false;

    // filters list what kind of tags are acceptable
    boolean acceptNN = false;
    boolean acceptJJ = false;
    boolean acceptCD = false;
    boolean disambiguate = false;
    String trainFile = null;

    for (int arg = 3; arg < args.length; arg++) {
      if ("-all".equals(args[arg])) {
        printAll = true;
        continue;
      } else if ("-acceptNN".equals(args[arg])) {
        acceptNN = true;
        continue;
      } else if ("-acceptJJ".equals(args[arg])) {
        acceptJJ = true;
        continue;
      } else if ("-acceptCD".equals(args[arg])) {
        acceptCD = true;
        continue;
      } else if ("-disambiguate".equals(args[arg])) {
        disambiguate = true;
        arg++;
        trainFile = args[arg];
        continue;
      } else {
        System.err.println("Unrecognized command-line argument: " + args[arg]);
      }
    }

    if (disambiguate) {
      BioCreativeGeneNormalizer.cm = new CooccurrenceModel();
      BioCreativeGeneNormalizer.cm.initialize(trainFile);
    }

    boolean filter = acceptNN || acceptJJ || acceptCD;
    BioRegexpDictionary dictionary = new BioRegexpDictionary(false);
    try {
      BufferedReader br = new BufferedReader(new FileReader(args[0]));
      String line;
      // build the synonym dictionary
      while ((line = br.readLine()) != null) {
        String[] columns = line.split("\t");
        if (columns.length == 0) {
          break;
        }
        String id = columns[0];
        for (int i = 1; i < columns.length; i++) {
          String synonym = columns[i];
          dictionary.add(synonym, id);
        }
      }
      BufferedReader tr = new BufferedReader(new FileReader(args[2])); // tagged file reader
      br = new BufferedReader(new FileReader(args[1]));
      while ((line = br.readLine()) != null) {
        String tl = tr.readLine(); // tagged line
        String[] tw = tl.split(" "); // an array of the words with their POS tags
        String fileName = tw[0].substring(2); // first word is the file name
        int index = 0; // a character-based index into the line
        int wordIndex = 0; // a word (space-delimited) index into the current line
        Set<GeneEvidencePair> ids = new HashSet<GeneEvidencePair>();
        Set<GeneIDPair> ambiguities = new HashSet<GeneIDPair>();
        while (index != -1) {
          int nextIndex = line.indexOf(' ', index);
          if (nextIndex == -1) {
            break;
          }
          index = nextIndex + 1; // move past the space
          wordIndex++;
          if (dictionary.lookingAt(line, index)) {
            String evidence = line.substring(index, dictionary.end());
            String[] ew = evidence.split(" ");
            // only accept sequences either longer than one word, or that are tagged as some kind of noun
            //System.err.println(evidence+":");
            //System.err.println(tw[wordIndex]+" "+wordIndex);

            String tag = tw[wordIndex].substring(tw[wordIndex].lastIndexOf('/') + 1);
            if (!filter || ew.length > 1 || (acceptNN && tag.startsWith("NN")) || (acceptCD && tag.equals("CD")) || (acceptJJ && tag.startsWith("JJ"))) {
              if (printAll || dictionary.data().size() == 1) // only print unambiguous
              {
                for (Iterator iter = dictionary.data().iterator(); iter.hasNext();) {
                  ids.add(new BioCreativeGeneNormalizer.GeneEvidencePair((String) iter.next(), evidence));
                }
              } else if (disambiguate) {
                ambiguities.add(new BioCreativeGeneNormalizer.GeneIDPair(evidence, dictionary.data()));
              }
            }
            wordIndex += ew.length - 1;
            index = dictionary.end();
          }
        }
        if (disambiguate) {
          BioCreativeGeneNormalizer.disambiguate(ids, ambiguities);
        }
        List<GeneEvidencePair> sorted = new ArrayList<GeneEvidencePair>(ids);
        Collections.sort(sorted);
        for (Iterator<GeneEvidencePair> iter = sorted.iterator(); iter.hasNext();) {
          System.out.println(fileName + iter.next().toString());
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
