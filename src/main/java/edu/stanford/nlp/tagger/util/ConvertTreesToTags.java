package edu.stanford.nlp.tagger.util; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;

/**
 * A short utility program that dumps out trees from multiple files
 * into one file of tagged text.  Useful for combining many parse tree
 * training files into one tagger training file, since the tagger
 * doesn't have convenient ways of reading in an entire directory.
 * <p>
 * There are a few command line arguments available:
 * <table>
 * <caption>Command line arguments</caption>
 * <tr>
 * <td> -output &lt;filename&gt; </td>
 * <td> File to output the data to </td>
 * </tr>
 * <tr>
 * <td> -tagSeparator &lt;separator&gt; </td>
 * <td> Separator to use between word and tag </td>
 * </tr>
 * <tr>
 * <td> -treeRange &lt;range&gt; </td>
 * <td> If tree files have numbers, they will be filtered out if not
 *      in this range.  Can be null. </td>
 * </tr>
 * <tr>
 * <td> -inputEncoding &lt;encoding&gt; </td>
 * <td> Encoding to use when reading tree files </td>
 * </tr>
 * <tr>
 * <td> -outputEncoding &lt;encoding&gt; </td>
 * <td> Encoding to use when writing tags </td>
 * </tr>
 * <tr>
 * <td> -treeFilter &lt;classname&gt; </td>
 * <td> A Filter&lt;Tree&gt; to load by reflection which eliminates
 *      trees from the data read </td>
 * </tr>
 * <tr>
 * <td> -noTags </td>
 * <td> If present, will only output the words, no tags at all
 * </tr>
 * <tr>
 * <td> -noSpaces </td>
 * <td> If present, words will be concatenated together </td>
 * </tr>
 * </table>
 *
 * All other arguments will be treated as filenames to read.
 *
 * @author John Bauer
 */
public class ConvertTreesToTags  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ConvertTreesToTags.class);

  private ConvertTreesToTags() {} // main method only

  public static void main(String[] args) throws IOException {
    String outputFilename = "";
    String tagSeparator = "";
    String treeRange = "";
    String inputEncoding = "UTF-8";
    String outputEncoding = "UTF-8";
    String treeFilter = "";
    boolean noTags = false;
    boolean noSpaces = false;
    List<String> inputFilenames = new ArrayList<>();
    for (int i = 0; i < args.length; ++i) {
      if ((args[i].equalsIgnoreCase("-output") ||
           args[i].equalsIgnoreCase("--output")) &&
          (i + 1 < args.length)) {
        outputFilename = args[i + 1];
        i++;
      } else if ((args[i].equalsIgnoreCase("-tagSeparator") ||
                  args[i].equalsIgnoreCase("--tagSeparator")) &&
                 (i + 1 < args.length)) {
        tagSeparator = args[i + 1];
        i++;
      } else if ((args[i].equalsIgnoreCase("-treeRange") ||
                  args[i].equalsIgnoreCase("--treeRange")) &&
                 (i + 1 < args.length)) {
        treeRange = args[i + 1];
        i++;
      } else if ((args[i].equalsIgnoreCase("-inputEncoding") ||
                  args[i].equalsIgnoreCase("--inputEncoding")) &&
                 (i + 1 < args.length)) {
        inputEncoding = args[i + 1];
        i++;
      } else if ((args[i].equalsIgnoreCase("-outputEncoding") ||
                  args[i].equalsIgnoreCase("--outputEncoding")) &&
                 (i + 1 < args.length)) {
        outputEncoding = args[i + 1];
        i++;
      } else if ((args[i].equalsIgnoreCase("-treeFilter") ||
                  args[i].equalsIgnoreCase("--treeFilter")) &&
                 (i + 1< args.length)) {
        treeFilter = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-noTags") ||
                 args[i].equalsIgnoreCase("--noTags")) {
        noTags = true;
      } else if (args[i].equalsIgnoreCase("-noSpaces") ||
                 args[i].equalsIgnoreCase("--noSpaces")) {
        noSpaces = true;
      } else {
        inputFilenames.add(args[i]);
      }
    }
    if (outputFilename.isEmpty()) {
      log.info("Must specify an output filename, -output");
      System.exit(2);
    }
    if (inputFilenames.isEmpty()) {
      log.info("Must specify one or more input filenames");
      System.exit(2);
    }

    FileOutputStream fos = new FileOutputStream(outputFilename);
    OutputStreamWriter osw = new OutputStreamWriter(fos, outputEncoding);
    BufferedWriter bout = new BufferedWriter(osw);
    Properties props = new Properties();
    for (String filename : inputFilenames) {
      String description = TaggedFileRecord.FORMAT + "=" +
                            TaggedFileRecord.Format.TREES + "," + filename;
      if (!treeRange.isEmpty()) {
        description = TaggedFileRecord.TREE_RANGE + "=" + treeRange +
                       "," + description;
      }
      if (!treeFilter.isEmpty()) {
        description = TaggedFileRecord.TREE_FILTER + "=" + treeFilter +
                       "," + description;
      }
      description = TaggedFileRecord.ENCODING + "=" + inputEncoding +
                     "," + description;
      TaggedFileRecord record = TaggedFileRecord.createRecord(props, description);

      for (List<TaggedWord> sentence : record.reader()) {
        String output = SentenceUtils.listToString(sentence, noTags, tagSeparator);
        if (noSpaces) {
          output = output.replaceAll(" ", "");
        }
        bout.write(output);
        bout.newLine();
      }
    }
    bout.flush();
    bout.close();
    osw.close();
    fos.close();
  }

}
