package edu.stanford.nlp.international.morph; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.international.arabic.ArabicTreeReaderFactory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Reads in the tree files without any kind of pre-processing. Assumes that the trees
 * have been processed separately.
 * <p>
 * TODO: wsg2011 Extend to other languages. Only supports Arabic right now.
 * 
 * @author Spence Green
 *
 */
public final class AddMorphoAnnotations  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AddMorphoAnnotations.class);
  
  private static final int minArgs = 2;
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Usage: java %s [OPTS] morph_file lemma_file < tree_file \n\n",AddMorphoAnnotations.class.getName()));
    sb.append("Options:\n");
    sb.append("  -e enc     : Encoding.\n");
    sb.append("  -g         : Morph file is gold tree file with morph analyses in the pre-terminals.");
    return sb.toString();
  }
  private static Map<String,Integer> argSpec() {
    Map<String,Integer> argSpec = Generics.newHashMap();
    argSpec.put("g", 0);
    argSpec.put("e", 1);
    return argSpec;
  }
  
  /**
   * Iterate over either strings or leaves.
   * 
   * @author Spence Green
   *
   */
  private static class YieldIterator implements Iterator<List<String>> {

    private List<String> nextYield = null;
    BufferedReader fileReader = null;
    TreeReader treeReader = null;
    
    public YieldIterator(String fileName, boolean isTree) {
      try {
        if (isTree) {
          TreeReaderFactory trf = new ArabicTreeReaderFactory.ArabicRawTreeReaderFactory(true);
          treeReader = trf.newTreeReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
        } else {
          fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
        }
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
      primeNext();
    }
    
    private void primeNext() {
      try {
        if (treeReader != null) {
            Tree tree = treeReader.readTree();
            if (tree == null) {
              nextYield = null;
            } else {
              List<CoreLabel> mLabeledLeaves = tree.taggedLabeledYield();
              nextYield = new ArrayList<>(mLabeledLeaves.size());
              for (CoreLabel label : mLabeledLeaves) {
                nextYield.add(label.tag());
              }
            }
        } else {
          String line = fileReader.readLine();
          if (line == null) {
            nextYield = null;
          } else {
            nextYield = Arrays.asList(line.split("\\s+"));
          }
        }
      } catch (IOException e) {
        nextYield = null;
        e.printStackTrace();
      }
    }
    
    @Override
    public boolean hasNext() {
      return nextYield != null;
    }

    @Override
    public List<String> next() {
      if (nextYield == null) {
        try {
          if (fileReader != null) {
            fileReader.close();
            fileReader = null;
          } else if (treeReader != null) {
            treeReader.close();
            treeReader = null;
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
      } else {
        List<String> next = nextYield;
        primeNext();
        return next;
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * 
   * @param args
   */
  public static void main(String[] args) {
    if(args.length < minArgs) {
      log.info(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, argSpec());
    String encoding = options.getProperty("e", "UTF-8");
    boolean isMorphTreeFile = PropertiesUtils.getBool(options, "g", false);
    String[] parsedArgs = options.getProperty("").split("\\s+");
    if (parsedArgs.length != 2) {
      log.info(usage());
      System.exit(-1);
    }
    
    YieldIterator morphIter = new YieldIterator(parsedArgs[0], isMorphTreeFile);
    YieldIterator lemmaIter = new YieldIterator(parsedArgs[1], false);
    
    final Pattern pParenStripper = Pattern.compile("[\\(\\)]");
        
    try {
      BufferedReader brIn = new BufferedReader(new InputStreamReader(System.in, encoding));
      TreeReaderFactory trf = new ArabicTreeReaderFactory.ArabicRawTreeReaderFactory(true);

      int nTrees = 0;
      for(String line; (line = brIn.readLine()) != null; ++nTrees) {
        Tree tree = trf.newTreeReader(new StringReader(line)).readTree();
        List<Tree> leaves = tree.getLeaves();
        if(!morphIter.hasNext()) {
          throw new RuntimeException("Mismatch between number of morpho analyses and number of input lines.");
        }
        List<String> morphTags = morphIter.next();
        if (!lemmaIter.hasNext()) {
          throw new RuntimeException("Mismatch between number of lemmas and number of input lines.");
        }
        List<String> lemmas = lemmaIter.next();
         
        // Sanity checks
        assert morphTags.size() == lemmas.size();
        assert lemmas.size() == leaves.size();
        
        for(int i = 0; i < leaves.size(); ++i) {
          String morphTag = morphTags.get(i);
          if (pParenStripper.matcher(morphTag).find()) {
            morphTag = pParenStripper.matcher(morphTag).replaceAll("");
          }
          String newLeaf = String.format("%s%s%s%s%s", leaves.get(i).value(),
              MorphoFeatureSpecification.MORPHO_MARK,
              lemmas.get(i),
              MorphoFeatureSpecification.LEMMA_MARK,
              morphTag);
          leaves.get(i).setValue(newLeaf);
        }
        System.out.println(tree.toString());
      }
      
      // Sanity checks
      assert !morphIter.hasNext();
      assert !lemmaIter.hasNext();
      
      System.err.printf("Processed %d trees%n",nTrees);
      
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
