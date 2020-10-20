package edu.stanford.nlp.trees;

import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;

/**
 * This utility looks for a given sentence in a file or directory of
 * tree files.  Options that can be specified are a tag separator used
 * on the sentence, the encoding of the file, and a regex to limit the
 * files looked for in subdirectorys.  For example, if you specify
 * -fileRegex ".*parse", then only filenames that end in "parse" will
 * be considered.  
 * <br>
 * The first non-option argument given will be the sentence searched
 * for.  The other arguments are paths in which to look for the
 * sentence.
 *
 * @author John Bauer
 */
public class FindTreebankTree {
  public static void main(String[] args) {
    // Args specified with -tagSeparator, -encoding, etc are assigned
    // to the appropriate option.  Otherwise, the first arg found is
    // the sentence to look for, and all other args are paths in which
    // to look for that sentence.
    String needle = "";
    String tagSeparator = "_";
    String encoding = "utf-8";
    String fileRegex = "";
    List<String> paths = new ArrayList<>();
    for (int i = 0; i < args.length; ++i) {
      if ((args[i].equalsIgnoreCase("-tagSeparator") ||
           args[i].equalsIgnoreCase("--tagSeparator")) &&
          i + 1 < args.length) {
        tagSeparator = args[i + 1];
        ++i;
      } else if ((args[i].equalsIgnoreCase("-encoding") ||
                  args[i].equalsIgnoreCase("--encoding")) &&
                 i + 1 < args.length) {
        encoding = args[i + 1];
        ++i;
      } else if ((args[i].equalsIgnoreCase("-fileRegex") ||
                  args[i].equalsIgnoreCase("--fileRegex")) &&
                 i + 1 < args.length) {
        fileRegex = args[i + 1];
        ++i;
      } else if (needle.equals("")) {
        needle = args[i].trim();
      } else {
        paths.add(args[i]);
      }
    }
    
    TreeReaderFactory trf = new LabeledScoredTreeReaderFactory();

    // If the user specified a regex, here we make a filter using that
    // regex.  We just use an anonymous class for the filter
    FileFilter filter = null;
    if (!fileRegex.equals("")) {
      final Pattern filePattern = Pattern.compile(fileRegex);
      filter = pathname -> (pathname.isDirectory() ||
              filePattern.matcher(pathname.getName()).matches());
    }

    for (String path : paths) {
      // Start a new treebank with the given path, encoding, filter, etc
      DiskTreebank treebank = new DiskTreebank(trf, encoding);
      treebank.loadPath(path, filter);

      Iterator<Tree> treeIterator = treebank.iterator();
      int treeCount = 0;
      String currentFile = "";
      while (treeIterator.hasNext()) {
        // the treebank might be a directory, not a single file, so
        // keep track of which file we are currently looking at
        if (!currentFile.equals(treebank.getCurrentFilename())) {
          currentFile = treebank.getCurrentFilename();
          treeCount = 0;
        }
        ++treeCount;
        Tree tree = treeIterator.next();
        List<TaggedWord> sentence = tree.taggedYield();
        boolean found = false;
        // The tree can match in one of three ways: tagged, untagged,
        // or untagged and unsegmented (which is useful for Chinese,
        // for example)
        String haystack = SentenceUtils.listToString(sentence, true);
        found = needle.equals(haystack);
        haystack = haystack.replaceAll(" ", "");
        found = found || needle.equals(haystack);
        haystack = SentenceUtils.listToString(sentence, false, tagSeparator);
        found = found || needle.equals(haystack);
        if (found) {
          System.out.println("needle found in " +  currentFile + 
                             " tree " + treeCount);
        }
      }
    }
  }
}
