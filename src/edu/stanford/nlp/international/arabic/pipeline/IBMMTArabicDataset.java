package edu.stanford.nlp.international.arabic.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.international.arabic.IBMArabicEscaper;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.treebank.ConfigParser;
import edu.stanford.nlp.trees.treebank.Dataset;
import edu.stanford.nlp.trees.treebank.Mapper;
import edu.stanford.nlp.util.Generics;

/**
 * Applies the same orthographic transformations developed for ATB parse trees to flat
 * MT input. This data set escapes IBM Arabic (for example, it removes explicit clitic markings).
 * <p>
 * NOTE: This class expects UTF-8 input (not Buckwalter)
 *
 * @author Spence Green
 *
 */
public class IBMMTArabicDataset implements Dataset  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(IBMMTArabicDataset.class);

  protected Mapper lexMapper = null;
  protected final List<File> pathsToData;

  protected String outFileName;
  protected final Pattern fileNameNormalizer = Pattern.compile("\\s+");

  protected final IBMArabicEscaper escaper;
  private static final Pattern utf8ArabicChart = Pattern.compile("[\u0600-\u06FF]");

  protected final Set<String> configuredOptions;
  protected final Set<String> requiredOptions;
  protected final StringBuilder toStringBuilder;

  public IBMMTArabicDataset() {
    configuredOptions = Generics.newHashSet();
    toStringBuilder = new StringBuilder();
    pathsToData = new ArrayList<>();

    escaper = new IBMArabicEscaper(true);
    escaper.disableWarnings();

    requiredOptions = Generics.newHashSet();
    requiredOptions.add(ConfigParser.paramName);
    requiredOptions.add(ConfigParser.paramPath);
  }

  public void build() {
    LineNumberReader infile = null;
    PrintWriter outfile = null;
    String currentInfile = "";
    try {
      outfile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName),"UTF-8")));

      for(File path : pathsToData) {
        infile = new LineNumberReader(new BufferedReader(new InputStreamReader(new FileInputStream(path),"UTF-8")));
        currentInfile = path.getPath();

        while(infile.ready()) {
          ArrayList<Word> sent = SentenceUtils.toUntaggedList(infile.readLine().split("\\s+"));

          for(Word token : sent) {
            Matcher hasArabic = utf8ArabicChart.matcher(token.word());
            if(hasArabic.find()) {
              token.setWord(escaper.apply(token.word()));
              token.setWord(lexMapper.map(null, token.word()));
            }
          }

          outfile.println(SentenceUtils.listToString(sent));
        }

        toStringBuilder.append(String.format(" Read %d input lines from %s",infile.getLineNumber(),path.getPath()));
      }

      infile.close();

    } catch (UnsupportedEncodingException e) {
      System.err.printf("%s: Filesystem does not support UTF-8 output\n", this.getClass().getName());
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open %s for writing\n", this.getClass().getName(), outFileName);
    } catch(IOException e) {
      System.err.printf("%s: Error reading from %s (line %d)\n", this.getClass().getName(), currentInfile,infile.getLineNumber());
    } catch(RuntimeException e) {
      System.err.printf("%s: Input sentence from %s contains token mapped to null (line %d)\n", this.getClass().getName(),currentInfile,infile.getLineNumber());
      e.printStackTrace();
    } finally {
      if(outfile != null)
        outfile.close();
    }
  }

  public List<String> getFilenames() {
    List<String> l = new ArrayList<>();
    l.add(outFileName);
    return l;
  }

  @Override
  public String toString() {
    return toStringBuilder.toString();
  }

  public boolean setOptions(Properties opts) {
    for(String opt : opts.stringPropertyNames()) {
      String value = opts.getProperty(opt);

      if(value == null) {
        System.err.printf("%s: Read parameter with null value (%s)\n", this.getClass().getName(),opt);
        continue;
      }

      configuredOptions.add(opt);

      Matcher pathMatcher = ConfigParser.matchPath.matcher(opt);

      if(pathMatcher.lookingAt()) {
        pathsToData.add(new File(value));
        configuredOptions.add(ConfigParser.paramPath);
      } else if(opt.equals(ConfigParser.paramName)) {
        Matcher inThisFilename = fileNameNormalizer.matcher(value.trim());
        outFileName = inThisFilename.replaceAll("-");
        toStringBuilder.append(String.format("Dataset Name: %s\n",value.trim()));
      }
    }

    if(!configuredOptions.containsAll(requiredOptions))
      return false;

    //Finalize the output file names
    outFileName += ".txt";

    //Used for codifying lexical hacks
    lexMapper = new DefaultLexicalMapper();

    return true;
  }

}
