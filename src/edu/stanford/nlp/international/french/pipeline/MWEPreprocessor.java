package edu.stanford.nlp.international.french.pipeline;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.international.french.FrenchTreeReader;
import edu.stanford.nlp.trees.international.french.FrenchTreeReaderFactory;
import edu.stanford.nlp.trees.tregex.ParseException;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

/**
 * Various modifications to the MWEs in the treebank.
 * 
 * @author Spence Green
 *
 */
public final class MWEPreprocessor {

  private static final boolean RESOLVE_DUMMY_TAGS = true;
  
  private static int nMissingPOS = 0;
  private static int nMissingPhrasal = 0;

  private MWEPreprocessor() {}
  
  //UW words extracted from June2010 revision of FTB
  private static class ManualUWModel {
    
    private static final Set<String> nouns = new HashSet<String>();
    private static final String nStr = 
      "A. Alezais alfa Annick Appliances Ardenne Artois baptiste Bargue Bellanger Bregenz clefs Coeurs ...conomie consumer " +
      "contrôleur Coopérative Coppée cuisson dédoublement demandeuse défraie Domestic dépistage Elektra Elettrodomestici " +
      "Essonnes Fair Finparcom Gelisim gorge Happy Indesit Italia jockey Lawrence leone Levi machinisme Mc.Donnel MD Merloni " +
      "Meydan ménagers Muenchener Parcel Prost R. sam Sara Siège silos SPA Stateman Valley Vanity VF Vidal Vives Yorker Young Zemment";
    //TODO wsg2011: défraie is a verb
    
    private static final Set<String> adjectives = new HashSet<String>();
    private static final String aStr = "astral bis bovin gracieux intégrante italiano sanguin sèche";
    
    private static final Set<String> preps = new HashSet<String>();
    private static final String pStr = "c o t";
    
    private static int nUnknownWordTypes;
    
    static {
      nouns.addAll(Arrays.asList(nStr.split("\\s+")));
      adjectives.addAll(Arrays.asList(aStr.split("\\s+")));
      preps.addAll(Arrays.asList(pStr.split("\\s+")));
      nUnknownWordTypes = nouns.size() + adjectives.size() + preps.size();
    }
    
    private static final Pattern digit = Pattern.compile("\\d+");
    
    public static String getTag(String word) {
      if(digit.matcher(word).find())
        return "N"; //This isn't right, but its close enough....
      else if(nouns.contains(word))
        return "N";
      else if(adjectives.contains(word))
        return "A";
      else if(preps.contains(word))
        return "P";
      
      System.err.println("No POS tag for " + word);
      return "N";
    }
  }
    
  public static void printCounter(TwoDimensionalCounter<String,String> cnt, 
                                  String fname) {
    try {
      PrintWriter pw = new PrintWriter(new PrintStream(new FileOutputStream(new File(fname)),false,"UTF-8"));
      for(String key : cnt.firstKeySet()) {
        for(String val : cnt.getCounter(key).keySet()) {
         pw.printf("%s\t%s\t%d%n", key, val, (int) cnt.getCount(key, val));
        }
      }
      pw.close();
      
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }
  
  public static void updateTagger(TwoDimensionalCounter<String,String> tagger, 
                                  Tree t) {
    List<CoreLabel> yield = t.taggedLabeledYield();
    for(CoreLabel cl : yield) {
      if(RESOLVE_DUMMY_TAGS && cl.tag().equals(FrenchTreeReader.MISSING_POS)) 
        continue;
      else
        tagger.incrementCount(cl.word(), cl.tag());
    }
  }
  
  
  public static void traverseAndFix(Tree t, 
      TwoDimensionalCounter<String, String> pretermLabel,
      TwoDimensionalCounter<String, String> unigramTagger) {
    if(t.isPreTerminal()) {
      if(t.value().equals(FrenchTreeReader.MISSING_POS)) {
        nMissingPOS++;
        String word = t.firstChild().value();
        String tag = (unigramTagger.firstKeySet().contains(word)) ? 
          Counters.argmax(unigramTagger.getCounter(word)) : ManualUWModel.getTag(word);
        t.setValue(tag);
      }
      
      return;
    }

    for(Tree kid : t.children())
      traverseAndFix(kid,pretermLabel,unigramTagger);
    
    //Post-order visit
    if(t.value().equals(FrenchTreeReader.MISSING_PHRASAL)) {
      nMissingPhrasal++;
      StringBuilder sb = new StringBuilder();
      for(Tree kid : t.children())
        sb.append(kid.value()).append(" ");
      
      String posSequence = sb.toString().trim();
      if(pretermLabel.firstKeySet().contains(posSequence)) {
        String phrasalCat = Counters.argmax(pretermLabel.getCounter(posSequence));
        t.setValue(phrasalCat);
      } else {
        System.out.println("No phrasal cat for: " + posSequence);
      }
    }
  }
  
  
  private static void resolveDummyTags(File treeFile,
      TwoDimensionalCounter<String, String> pretermLabel,
      TwoDimensionalCounter<String, String> unigramTagger) {
    
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new FrenchTreeReaderFactory(true);
      TreeReader tr = trf.newTreeReader(br);
      
      PrintWriter pw = new PrintWriter(new PrintStream(new FileOutputStream(new File(treeFile + ".fixed")),false,"UTF-8"));
   
      int nTrees = 0;
      for(Tree t; (t = tr.readTree()) != null;nTrees++) {
        traverseAndFix(t, pretermLabel, unigramTagger);
        pw.println(t.toString());
      }
      pw.close();
      tr.close();

      System.out.println("Processed " +nTrees+ " trees");

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  static final TregexPattern pMWE = TregexPattern.compile("/^MW/");

  static public void countMWEStatistics(Tree t,
      TwoDimensionalCounter<String, String> unigramTagger,
      TwoDimensionalCounter<String, String> labelPreterm,
      TwoDimensionalCounter<String, String> pretermLabel,
      TwoDimensionalCounter<String, String> labelTerm,
      TwoDimensionalCounter<String, String> termLabel) 
  {
    updateTagger(unigramTagger,t);

    //Count MWE statistics
    TregexMatcher m = pMWE.matcher(t);
    while (m.findNextMatchingNode()) {
      Tree match = m.getMatch();
      String label = match.value();
      if(RESOLVE_DUMMY_TAGS && label.equals(FrenchTreeReader.MISSING_PHRASAL))
        continue;
      
      String preterm = Sentence.listToString(match.preTerminalYield());
      String term = Sentence.listToString(match.yield());
      
      labelPreterm.incrementCount(label,preterm);
      pretermLabel.incrementCount(preterm,label);
      labelTerm.incrementCount(label,term);
      termLabel.incrementCount(term, label);
    }
  }
                                

  /**
   * 
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 1) {
      System.err.printf("Usage: java %s file%n", MWEPreprocessor.class.getName());
      System.exit(-1);
    }
    
    final File treeFile = new File(args[0]);
    TwoDimensionalCounter<String,String> labelTerm = 
      new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> termLabel = 
      new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> labelPreterm = 
      new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> pretermLabel = 
      new TwoDimensionalCounter<String,String>();
    
    TwoDimensionalCounter<String,String> unigramTagger = 
      new TwoDimensionalCounter<String,String>();
    
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new FrenchTreeReaderFactory(true);
      TreeReader tr = trf.newTreeReader(br);
      
      for(Tree t; (t = tr.readTree()) != null;) {
        countMWEStatistics(t, unigramTagger,
                           labelPreterm, pretermLabel, labelTerm, termLabel);
      }
      tr.close(); //Closes the underlying reader
      
      System.out.println("Generating {MWE Type -> Terminal}");
      printCounter(labelTerm, "label_term.csv");
      
      System.out.println("Generating {Terminal -> MWE Type}");
      printCounter(termLabel, "term_label.csv");
      
      System.out.println("Generating {MWE Type -> POS sequence}");
      printCounter(labelPreterm, "label_pos.csv");
      
      System.out.println("Generating {POS sequence -> MWE Type}");
      printCounter(pretermLabel, "pos_label.csv");
      
      if(RESOLVE_DUMMY_TAGS) {
        System.out.println("Resolving DUMMY tags");
        resolveDummyTags(treeFile, pretermLabel, unigramTagger);
      }
      
      System.out.println("#Unknown Word Types: " + ManualUWModel.nUnknownWordTypes);
      System.out.println("#Missing POS: " + nMissingPOS);
      System.out.println("#Missing Phrasal: " + nMissingPhrasal);
      
      System.out.println("Done!");
      
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      e.printStackTrace();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
