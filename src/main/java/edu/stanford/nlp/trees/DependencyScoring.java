package edu.stanford.nlp.trees; 
import edu.stanford.nlp.util.logging.Redwood;

import static edu.stanford.nlp.trees.GrammaticalRelation.DEPENDENT;

import java.io.LineNumberReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;

/**
 * Scoring of typed dependencies
 *
 * @author danielcer
 *
 */
public class DependencyScoring  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DependencyScoring.class);
  public final static boolean VERBOSE = false;

  public final List<Set<TypedDependency>> goldDeps;
  public final List<Set<TypedDependency>> goldDepsUnlabeled;
  public final boolean ignorePunc;


  private static List<Set<TypedDependency>> toSets(Collection<TypedDependency> depCollection) {
    Set<TypedDependency> depSet = Generics.newHashSet();
    Set<TypedDependency> unlabeledDepSet = Generics.newHashSet();
    for (TypedDependency dep : depCollection) {
      unlabeledDepSet.add(new TypedDependencyStringEquality(null, dep.gov(), dep.dep()));
      depSet.add(new TypedDependencyStringEquality(dep.reln(), dep.gov(), dep.dep()));
    }

    List<Set<TypedDependency>> l = new ArrayList<>(2);
    l.add(depSet);
    l.add(unlabeledDepSet);
    return l;
  }

  public DependencyScoring(List<Collection<TypedDependency>> goldDeps, boolean ignorePunc) {
    this.goldDeps = new ArrayList<>(goldDeps.size());
    this.goldDepsUnlabeled = new ArrayList<>(goldDeps.size());
    this.ignorePunc = ignorePunc;

    for (Collection<TypedDependency> depCollection : goldDeps) {
      List<Set<TypedDependency>> sets = toSets(depCollection);
      this.goldDepsUnlabeled.add(sets.get(1));
      this.goldDeps.add(sets.get(0));
    }
    if (ignorePunc) {
      removeHeadsAssignedToPunc(this.goldDeps);
      removeHeadsAssignedToPunc(this.goldDepsUnlabeled);
    }
  }

  private static void removeHeadsAssignedToPunc(Set<TypedDependency> depSet) {
    List<TypedDependency> deps = new ArrayList<>(depSet);
    for (TypedDependency dep : deps) {
      if (langIndependentPuncCheck(dep.dep().word())) {
        if (VERBOSE) {
           System.err.printf("Dropping Punctuation Dependency: %s\n", dep);
        }
        depSet.remove(dep);
      }
    }
  }

  private static void removeHeadsAssignedToPunc(List<Set<TypedDependency>> depSets) {
    for (Set<TypedDependency> depSet : depSets) {
      removeHeadsAssignedToPunc(depSet);
    }
  }

  public static boolean langIndependentPuncCheck(String token) {
    boolean isNotWord = true;
    for (int offset = 0; offset < token.length(); ) {
       final int codepoint = token.codePointAt(offset);
       if (Character.isLetterOrDigit(codepoint)) {
         isNotWord = false;
       }
       offset += Character.charCount(codepoint);
    }
    return isNotWord;
  }

  public static DependencyScoring newInstanceStringEquality(List<Collection<TypedDependency>> goldDeps, boolean ignorePunc) {
    return new DependencyScoring(convertStringEquality(goldDeps), ignorePunc);
  }

  public DependencyScoring(String filename, boolean CoNLLX, boolean ignorePunc) throws IOException {
    this((CoNLLX ? readDepsCoNLLX(filename) : readDeps(filename)), ignorePunc);
  }

  public DependencyScoring(String filename) throws IOException {
    this(filename, false, false);
  }

  public static List<Collection<TypedDependency>> convertStringEquality(List<Collection<TypedDependency>> deps){
    List<Collection<TypedDependency>> convertedDeps = new ArrayList<>();
    for(Collection<TypedDependency> depSet : deps){
      Collection<TypedDependency> converted = Generics.newHashSet();
      for(TypedDependency dep : depSet){
        converted.add(new TypedDependencyStringEquality(dep.reln(), dep.gov(), dep.dep()));
      }
      convertedDeps.add(converted);
    }
    return convertedDeps;
  }

  static private class TypedDependencyStringEquality extends TypedDependency {
    private static final long serialVersionUID = 1L;

    public TypedDependencyStringEquality(GrammaticalRelation reln, IndexedWord gov, IndexedWord dep)  {
       super(reln, gov, dep);
    }

    public boolean equals(Object o) {
       // some parsers, like Relex, screw up the casing
       return o.toString().toLowerCase().equals(this.toString().toLowerCase());
    }

    @Override
    public int hashCode() {
       return toString().toLowerCase().hashCode();
    }
  }

  /** Normalize all number tokens to &lt;num&gt; in order to allow
   *  for proper scoring of MSTParser productions.
   */
  static protected String normalizeNumbers(String token) {
    String norm = token.replaceFirst("^([0-9]+)-([0-9]+)$", "<num>-$2");

    if (!norm.equals(token)) {
      System.err.printf("Normalized numbers in token: %s => %s\n", token, norm);
    }
    return token;
  }


  /**
   * Read in typed dependencies in CoNLLX format.
   *
   * @param filename
   * @throws IOException
   */
  static protected List<Collection<TypedDependency>> readDepsCoNLLX(String filename) throws IOException {
	  List<GrammaticalStructure> gss = GrammaticalStructure.readCoNLLXGrammaticalStructureCollection(filename,
                  new fakeShortNameToGRel(), new GraphLessGrammaticalStructureFactory());
	  List<Collection<TypedDependency>> readDeps = new ArrayList<>(gss.size());
	  for (GrammaticalStructure gs : gss) {
	    Collection<TypedDependency> deps = gs.typedDependencies();
	    readDeps.add(deps);
	  }
	  return readDeps;
  }

  /**
   * Read in typed dependencies. Warning created typed dependencies are not
   * backed by any sort of a tree structure.
   *
   * @param filename
   * @throws IOException
   */
  static protected List<Collection<TypedDependency>> readDeps(String filename) throws IOException {
    LineNumberReader breader = new LineNumberReader(new FileReader(filename));
    List<Collection<TypedDependency>> readDeps = new ArrayList<>();
    Collection<TypedDependency> deps = new ArrayList<>();
    for (String line = breader.readLine(); line != null; line = breader.readLine()) {
      if (line.equals("null(-0,-0)") || line.equals("null(-1,-1)")) {
         readDeps.add(deps);
         deps = new ArrayList<>();
         continue; // relex parse error
      }
      try {
      if (line.equals("")) {
         if (deps.size() != 0) {
          //System.out.println(deps);
          readDeps.add(deps);
          deps = new ArrayList<>();
        }
        continue;
      }
      int firstParen = line.indexOf("(");
      int commaSpace = line.indexOf(", ");
      String depName = line.substring(0, firstParen);
      String govName = line.substring(firstParen + 1, commaSpace);
      String childName = line.substring(commaSpace+2, line.length() - 1);
      GrammaticalRelation grel = GrammaticalRelation.valueOf(depName);
      if (depName.startsWith("prep_")) {
        String prep = depName.substring(5);
        grel = EnglishGrammaticalRelations.getPrep(prep);
      }
      if (depName.startsWith("prepc_")) {
        String prepc = depName.substring(6);
        grel = EnglishGrammaticalRelations.getPrepC(prepc);
      }
      if (depName.startsWith("conj_")) {
        String conj = depName.substring(5);
        grel = EnglishGrammaticalRelations.getConj(conj);
      }
      if (grel == null) {
        throw new RuntimeException("Unknown grammatical relation '" + depName+"'");
      }

      //Word govWord = new Word(govName.substring(0, govDash));
      IndexedWord govWord = new IndexedWord();
      govWord.setValue(normalizeNumbers(govName));
      govWord.setWord(govWord.value());
      //Word childWord = new Word(childName.substring(0, childDash));
      IndexedWord childWord = new IndexedWord();
      childWord.setValue(normalizeNumbers(childName));
      childWord.setWord(childWord.value());
      TypedDependency dep = new TypedDependencyStringEquality(grel, govWord, childWord);
      deps.add(dep);
      } catch (Exception e) {
        breader.close();
        throw new RuntimeException("Error on line "+breader.getLineNumber()+":\n\n"+e);
      }
    }
    if (deps.size() != 0) {
       readDeps.add(deps);
    }

    //log.info("last: "+readDeps.get(readDeps.size()-1));
    breader.close();
    return readDeps;
  }

  /**
   * Score system typed dependencies
   *
   * @param system
   * @return a triple consisting of (labeled attachment, unlabeled attachment,
   *         label accuracy)
   */
  public Score score(List<Collection<TypedDependency>> system) {
    int parserCnt = 0;
    int goldCnt = 0;
    int parserUnlabeledCnt = 0;
    int goldUnlabeledCnt = 0;
    int correctAttachment = 0;
    int correctUnlabeledAttachment = 0;
    int labelCnt = 0;
    int labelCorrect = 0;

    ClassicCounter<String> unlabeledErrorCounts = new ClassicCounter<>();
    ClassicCounter<String> labeledErrorCounts = new ClassicCounter<>();
    //System.out.println("Gold size: "+ goldDeps.size() + " System size: "+system.size());
    for (int i = 0; i < system.size(); i++) {
      List<Set<TypedDependency>> l = toSets(system.get(i));
      if (ignorePunc) {
        removeHeadsAssignedToPunc(l.get(0));
        removeHeadsAssignedToPunc(l.get(1));
      }

      parserCnt += l.get(0).size();
      goldCnt += goldDeps.get(i).size();

      parserUnlabeledCnt += l.get(1).size();
      goldUnlabeledCnt += goldDepsUnlabeled.get(i).size();

      l.get(0).retainAll(goldDeps.get(i));
      l.get(1).retainAll(goldDepsUnlabeled.get(i));
      correctAttachment += l.get(0).size();
      correctUnlabeledAttachment += l.get(1).size();
      labelCnt += l.get(1).size();
      labelCorrect += l.get(0).size();
      //System.out.println(""+i+" Acc: "+(l.get(0).size())/(double)localCnt+" "+l.get(0).size()+"/"+localCnt);


      // identify errors
      List<Set<TypedDependency>> errl = toSets(system.get(i));
      errl.get(0).removeAll(goldDeps.get(i));
      errl.get(1).removeAll(goldDepsUnlabeled.get(i));
      Map<String,String> childCorrectWithLabel = Generics.newHashMap();
      Map<String,String> childCorrectWithOutLabel = Generics.newHashMap();

      for (TypedDependency goldDep: goldDeps.get(i)) {
          //System.out.print(goldDep);
          String sChild = goldDep.dep().toString().replaceFirst("-[^-]*$", "");
          String prefixLabeled = "";
          String prefixUnlabeled = "";
          if (childCorrectWithLabel.containsKey(sChild)) {
            prefixLabeled = childCorrectWithLabel.get(sChild)+", ";
            prefixUnlabeled = childCorrectWithOutLabel.get(sChild)+", ";
          }
          childCorrectWithLabel.put(sChild, prefixLabeled + goldDep.reln()+"("+goldDep.gov().toString().replaceFirst("-[^-]*$", "")+", "+sChild+")");
          childCorrectWithOutLabel.put(sChild, prefixUnlabeled + "dep("+goldDep.gov().toString().replaceFirst("-[^-]*$", "")+", "+sChild+")");
      }

      for (TypedDependency labeledError: errl.get(0)) {
          String sChild = labeledError.dep().toString().replaceFirst("-[^-]*$", "");
          String sGov   = labeledError.gov().toString().replaceFirst("-[^-]*$", "");
          labeledErrorCounts.incrementCount(labeledError.reln().toString()+"("+sGov+", "+sChild+") <= "+childCorrectWithLabel.get(sChild));
      }
      for (TypedDependency unlabeledError: errl.get(1)) {
          String sChild = unlabeledError.dep().toString().replaceFirst("-[^-]*$", "");
          String sGov   = unlabeledError.gov().toString().replaceFirst("-[^-]*$", "");
          unlabeledErrorCounts.incrementCount("dep("+sGov+", "+sChild+") <= "+childCorrectWithOutLabel.get(sChild));
      }
    }
    return new Score(parserCnt, goldCnt, parserUnlabeledCnt, goldUnlabeledCnt, correctAttachment, correctUnlabeledAttachment, labelCnt, labelCorrect, labeledErrorCounts, unlabeledErrorCounts);
  }

  public static class Score {
    final int parserCnt;
    final int goldCnt;
    final int parserUnlabeledCnt;
    final int goldUnlabeledCnt;
    final int correctAttachment;
    final int correctUnlabeledAttachment;
    final int labelCnt;
    final int labelCorrect;
    final ClassicCounter<String> unlabeledErrorCounts;
    final ClassicCounter<String> labeledErrorCounts;

    public Score(int parserCnt, int goldCnt, int parserUnlabeledCnt, int goldUnlabeledCnt, int correctAttachment, int correctUnlabeledAttachment, int labelCnt, int labelCorrect, ClassicCounter<String> labeledErrorCounts, ClassicCounter<String> unlabeledErrorCounts) {
      this.parserCnt = parserCnt;
      this.goldCnt = goldCnt;
      this.parserUnlabeledCnt = parserUnlabeledCnt;
      this.goldUnlabeledCnt = goldUnlabeledCnt;
      this.correctAttachment = correctAttachment;
      this.correctUnlabeledAttachment = correctUnlabeledAttachment;
      this.labelCnt = labelCnt;
      this.labelCorrect = labelCorrect;
      this.unlabeledErrorCounts = new ClassicCounter<>(unlabeledErrorCounts);
      this.labeledErrorCounts = new ClassicCounter<>(labeledErrorCounts);
    }

    public String toString() {
      return toStringFScore(false, false);
    }

    public String toStringAttachmentScore(boolean json) {
       if (parserCnt != goldCnt) {
         throw new RuntimeException(
           String.format("AttachmentScore cannot be used when count(gold deps:%d) != count(system deps:%d)", parserCnt, goldCnt));
       }
       double las = correctAttachment/(double)goldCnt;
       double uas = correctUnlabeledAttachment/(double)goldCnt;
       StringBuilder sbuild = new StringBuilder();

       if (json) {
         sbuild.append("{");
         sbuild.append(String.format("'LAS' : %.3f, ", las));
         sbuild.append(String.format("'UAS' : %.3f, ", uas));
         sbuild.append("}");
       } else {
         sbuild.append(String.format("|| Labeled Attachment Score   ||"));
         sbuild.append(String.format(" %.3f (%d/%d) ||\n", las, correctAttachment, goldCnt));

         sbuild.append(String.format("|| Unlabeled Attachment Score ||"));
         sbuild.append(String.format(" %.3f (%d/%d) ||\n", uas, correctUnlabeledAttachment, goldCnt));
       }

       return sbuild.toString();
    }

    public String toStringFScore(boolean verbose, boolean json) {

      double lp = correctAttachment/(double)parserCnt;
      double lr = correctAttachment/(double)goldCnt;
      double lf = 2.0*(lp*lr)/(lp+lr);
      /*sbuild.append(String.format("Labeled Attachment P: %.3f (%d/%d)\n", correctAttachment/(double)parserCnt, correctAttachment, parserCnt));
      sbuild.append(String.format("Labeled Attachment R: %.3f (%d/%d)\n", correctAttachment/(double)goldCnt, correctAttachment, goldCnt));
      */
      double ulp = correctUnlabeledAttachment/(double)parserUnlabeledCnt;
      double ulr = correctUnlabeledAttachment/(double)goldUnlabeledCnt;
      double ulf = 2.0*(ulp*ulr)/(ulp+ulr);
      /*
      sbuild.append(String.format("Unlabeled Attachment P: %.3f (%d/%d)\n", correctUnlabeledAttachment/(double)parserCnt, correctUnlabeledAttachment, parserCnt));
      sbuild.append(String.format("Unlabeled Attachment R: %.3f (%d/%d)\n", correctUnlabeledAttachment/(double)goldCnt, correctUnlabeledAttachment, goldCnt));
      sbuild.append(String.format("LabelAccuracy: %.3f (%d/%d)\n", labelCorrect/(double)labelCnt, labelCorrect, labelCnt));
      */
      StringBuilder sbuild = new StringBuilder();

      if (json) {
         sbuild.append("{");
         sbuild.append(String.format("'LF1' : %.3f, ", lf));
         sbuild.append(String.format("'LP' : %.3f, ", lp));
         sbuild.append(String.format("'LR' : %.3f, ", lr));

         sbuild.append(String.format("'UF1' : %.3f, ", ulf));
         sbuild.append(String.format("'UP' : %.3f, ", ulp));
         sbuild.append(String.format("'UR' : %.3f, ", ulr));

         sbuild.append("}");
      } else {
         sbuild.append(String.format("|| Labeled Attachment   || F ||  P ||  R ||\n"));
         sbuild.append(String.format("||                      || %.3f || %.3f (%d/%d) || %.3f (%d/%d)||\n",
             lf, lp, correctAttachment, parserCnt, lr, correctAttachment, goldCnt));
         sbuild.append(String.format("|| Unlabeled Attachment || F ||  P ||  R ||\n"));
         sbuild.append(String.format("||                     || %.3f || %.3f (%d/%d) || %.3f (%d/%d)||\n",
             ulf, ulp, correctUnlabeledAttachment, parserCnt, ulr, correctUnlabeledAttachment, goldCnt));

         if (verbose) {
           sbuild.append("\nLabeled Attachment Error Counts\n");
           sbuild.append(Counters.toSortedString(labeledErrorCounts, Integer.MAX_VALUE, "\t%2$f\t%1$s", "\n"));
           sbuild.append("\n");
           sbuild.append("\nUnlabeled Attachment Error Counts\n");
           sbuild.append(Counters.toSortedString(unlabeledErrorCounts, Integer.MAX_VALUE, "\t%2$f\t%1$s", "\n"));
         }
      }
      return sbuild.toString();
    }

  } // end static class Score


  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);
    boolean verbose = Boolean.parseBoolean(props.getProperty("v", "False"));
    boolean conllx = Boolean.parseBoolean(props.getProperty("conllx", "False"));
    boolean jsonOutput = Boolean.parseBoolean(props.getProperty("jsonOutput", "False"));
    boolean ignorePunc = Boolean.parseBoolean(props.getProperty("nopunc", "False"));

    String goldFilename = props.getProperty("g");
    String systemFilename = props.getProperty("s");
    if (goldFilename == null || systemFilename == null) {
      log.info("Usage:\n\tjava ...DependencyScoring [-v True/False] [-conllx True/False] [-jsonOutput True/False] [-ignorePunc True/False] -g goldFile -s systemFile\n");
      log.info("\nOptions:\n\t-v verbose output");
      System.exit(-1);
    }

    DependencyScoring goldScorer = new DependencyScoring(goldFilename, conllx, ignorePunc);

    List<Collection<TypedDependency>> systemDeps;

    if (conllx) {
      systemDeps = DependencyScoring.readDepsCoNLLX(systemFilename);
    } else {
      systemDeps = DependencyScoring.readDeps(systemFilename);
    }

    Score score = goldScorer.score(systemDeps);
    if (conllx) {
      System.out.println(score.toStringAttachmentScore(jsonOutput));
    } else {
      System.out.println(score.toStringFScore(verbose,jsonOutput));
    }
  }
}

class GraphLessGrammaticalStructureFactory implements GrammaticalStructureFromDependenciesFactory {

  public GrammaticalStructure build(
      List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
    return new GraphLessGrammaticalStructure(projectiveDependencies, root);
  }
}

class GraphLessGrammaticalStructure extends GrammaticalStructure {
  private static final long serialVersionUID = 1L;

  public GraphLessGrammaticalStructure(
      List<TypedDependency> projectiveDependencies, TreeGraphNode root) {
    super(projectiveDependencies, root);
   }
}

class fakeShortNameToGRel implements Map<String, GrammaticalRelation>{

	public void clear() {
	  throw new UnsupportedOperationException();
	}

	public boolean containsKey(Object o) {
	  // since we generate grammatical relations dynamically, this "map" technically contains any String key
	  if (o instanceof String) {
	    return true;
	  } else {
	    return false;
	  }
  }

	public boolean containsValue(Object o) {
		throw new UnsupportedOperationException();
	}

	public Set<java.util.Map.Entry<String, GrammaticalRelation>> entrySet() {
	  throw new UnsupportedOperationException();
	}

	public GrammaticalRelation get(Object key) {
	  if (!(key instanceof String)) {
	    throw new UnsupportedOperationException();
	  }
	  String strkey = (String)key;
	  return new GrammaticalRelation(Language.Any, strkey, null, DEPENDENT) {
      private static final long serialVersionUID = 1L;

      @Override
	    public boolean equals(Object o) {
	      if (o instanceof GrammaticalRelation) {
	         return this.getShortName().equals(((GrammaticalRelation)o).getShortName());
	      }
	      return false;
	    }

	    @Override
	    public int hashCode() {
	      return this.getShortName().hashCode();
	    }
	  };
	}

	public boolean isEmpty() {
		return false;
	}

	public Set<String> keySet() {
	  throw new UnsupportedOperationException();
	}

	public GrammaticalRelation put(String key, GrammaticalRelation value) {
	  throw new UnsupportedOperationException();
	}

	public void putAll(Map<? extends String, ? extends GrammaticalRelation> m) {
	  throw new UnsupportedOperationException();
	}

	public GrammaticalRelation remove(Object key) {
	  throw new UnsupportedOperationException();
	}

	public int size() {
	  throw new UnsupportedOperationException();
	}

	public Collection<GrammaticalRelation> values() {
	  throw new UnsupportedOperationException();
	}
}
