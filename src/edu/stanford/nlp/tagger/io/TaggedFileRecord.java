package edu.stanford.nlp.tagger.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import java.util.function.Predicate;
import edu.stanford.nlp.util.ReflectionLoading;

/** Parses and specifies all the details for how to read some POS tagging data.
 *  The options for this class are documented in MaxentTagger, under the trainFile property.
 *
 *  @author John Bauer
 */
public class TaggedFileRecord {

  public enum Format {
    TEXT,  // represents a tokenized file separated by text
    TSV,   // represents a tsv file such as a conll file
    TREES // represents a file in PTB format
  }

  private final String file;
  final Format format;
  final String encoding;
  final String tagSeparator;
  final TreeTransformer treeTransformer;
  final TreeNormalizer treeNormalizer;
  final NumberRangesFileFilter treeRange;
  final Predicate<Tree> treeFilter;
  final Integer wordColumn;
  final Integer tagColumn;
  final TreeReaderFactory trf;
  final boolean usesComments;
  final boolean skipMWT;

  private TaggedFileRecord(String file, Format format,
                           String encoding, String tagSeparator,
                           TreeTransformer treeTransformer,
                           TreeNormalizer treeNormalizer,
                           TreeReaderFactory trf,
                           NumberRangesFileFilter treeRange,
                           Predicate<Tree> treeFilter,
                           Integer wordColumn, Integer tagColumn,
                           boolean usesComments, boolean skipMWT) {
    this.file = file;
    this.format = format;
    this.encoding = encoding;
    this.tagSeparator = tagSeparator;
    this.treeTransformer = treeTransformer;
    this.treeNormalizer = treeNormalizer;
    this.treeRange = treeRange;
    this.treeFilter = treeFilter;
    this.wordColumn = wordColumn;
    this.tagColumn = tagColumn;
    this.trf = trf;
    this.usesComments = usesComments;
    this.skipMWT = skipMWT;
  }

  public static final String FORMAT = "format";
  public static final String ENCODING = "encoding";
  public static final String TAG_SEPARATOR = "tagSeparator";
  public static final String TREE_TRANSFORMER = "treeTransformer";
  public static final String TREE_NORMALIZER = "treeNormalizer";
  public static final String TREE_RANGE = "treeRange";
  public static final String TREE_FILTER = "treeFilter";
  public static final String WORD_COLUMN = "wordColumn";
  public static final String TAG_COLUMN = "tagColumn";
  public static final String TREE_READER = "trf";
  public static final String COMMENTS = "comments";
  public static final String SKIP_MWT = "skipMWT";

  public String toString() {
    StringBuilder s = new StringBuilder();
    s.append(FORMAT + "=" + format);
    s.append("," + ENCODING + "=" + encoding);
    s.append("," + TAG_SEPARATOR + "=" + tagSeparator);
    if (treeTransformer != null) {
      s.append("," + TREE_TRANSFORMER + "=" +
               treeTransformer.getClass().getName());
    }
    if (trf != null) {
      s.append("," + TREE_READER + "=" +
               trf.getClass().getName());
    }
    if (treeNormalizer != null) {
      s.append("," + TREE_NORMALIZER + "=" +
               treeNormalizer.getClass().getName());
    }
    if (treeRange != null) {
      s.append("," + TREE_RANGE + "=" +
               treeRange.toString().replaceAll(",", ":"));
    }
    if (treeRange != null) {
      s.append("," + TREE_FILTER + "=" + treeFilter.getClass().toString());
    }
    if (wordColumn != null) {
      s.append("," + WORD_COLUMN + "=" + wordColumn);
    }
    if (tagColumn != null) {
      s.append("," + TAG_COLUMN + "=" + tagColumn);
    }
    if (usesComments) {
      s.append("," + COMMENTS + "=true");
    }
    if (skipMWT) {
      s.append("," + SKIP_MWT + "=true");
    }
    return s.toString();
  }

  public String filename() { return file; }

  public TaggedFileReader reader() {
    switch(format) {
    case TEXT:
      return new TextTaggedFileReader(this);
    case TREES:
      return new TreeTaggedFileReader(this);
    case TSV:
      return new TSVTaggedFileReader(this);
    default:
      throw new IllegalArgumentException("Unknown format " + format);
    }
  }

  public static List<TaggedFileRecord> createRecords(Properties config,
                                                     String description) {
    String[] pieces = description.split(";");
    List<TaggedFileRecord> records = new ArrayList<>();
    for (String piece : pieces) {
      records.add(createRecord(config, piece));
    }
    return records;
  }

  public static TaggedFileRecord createRecord(Properties config,
                                              String description) {
    String[] pieces = description.split(",");
    if (pieces.length == 1) {
      return new TaggedFileRecord(description, Format.TEXT,
                                  getEncoding(config),
                                  getTagSeparator(config),
                                  null, null, null, null, null, null, null, false, false);
    }

    String[] args = new String[pieces.length - 1];
    System.arraycopy(pieces, 0, args, 0, pieces.length - 1);
    String file = pieces[pieces.length - 1];
    Format format = Format.TEXT;
    String encoding = getEncoding(config);
    String tagSeparator = getTagSeparator(config);
    TreeTransformer treeTransformer = null;
    TreeNormalizer treeNormalizer = null;
    TreeReaderFactory trf = null;
    NumberRangesFileFilter treeRange = null;
    Predicate<Tree> treeFilter = null;
    Integer wordColumn = null, tagColumn = null;
    boolean comments = false;
    boolean skipMWT = false;

    for (String arg : args) {
      String[] argPieces = arg.split("=", 2);
      if (argPieces.length != 2) {
        throw new IllegalArgumentException("TaggedFileRecord argument " + arg +
                                           " has an unexpected number of =s");
      }
      if (argPieces[0].equalsIgnoreCase(FORMAT)) {
        format = Format.valueOf(argPieces[1]);
      } else if (argPieces[0].equalsIgnoreCase(ENCODING)) {
        encoding = argPieces[1];
      } else if (argPieces[0].equalsIgnoreCase(TAG_SEPARATOR)) {
        tagSeparator = argPieces[1];
      } else if (argPieces[0].equalsIgnoreCase(TREE_TRANSFORMER)) {
        treeTransformer = ReflectionLoading.loadByReflection(argPieces[1]);
      } else if (argPieces[0].equalsIgnoreCase(TREE_NORMALIZER)) {
        treeNormalizer = ReflectionLoading.loadByReflection(argPieces[1]);
      } else if (argPieces[0].equalsIgnoreCase(TREE_READER)) {
        trf = ReflectionLoading.loadByReflection(argPieces[1]);
      } else if (argPieces[0].equalsIgnoreCase(TREE_RANGE)) {
        String range = argPieces[1].replaceAll(":", ",");
        treeRange = new NumberRangesFileFilter(range, true);
      } else if (argPieces[0].equalsIgnoreCase(TREE_FILTER)) {
        treeFilter = ReflectionLoading.loadByReflection(argPieces[1]);
      } else if (argPieces[0].equalsIgnoreCase(WORD_COLUMN)) {
        wordColumn = Integer.valueOf(argPieces[1]);
      } else if (argPieces[0].equalsIgnoreCase(TAG_COLUMN)) {
        tagColumn = Integer.valueOf(argPieces[1]);
      } else if (argPieces[0].equalsIgnoreCase(COMMENTS)) {
        comments = Boolean.valueOf(argPieces[1]);
      } else if (argPieces[0].equalsIgnoreCase(SKIP_MWT)) {
        skipMWT = Boolean.valueOf(argPieces[1]);
      } else {
        throw new IllegalArgumentException("TaggedFileRecord argument " +
                                           argPieces[0] + " is unknown");
      }
    }
    return new TaggedFileRecord(file, format, encoding, tagSeparator,
                                treeTransformer, treeNormalizer, trf, treeRange,
                                treeFilter, wordColumn, tagColumn, comments, skipMWT);
  }

  public static String getEncoding(Properties config) {
    String encoding = config.getProperty(TaggerConfig.ENCODING_PROPERTY);
    if (encoding == null)
      return TaggerConfig.ENCODING;
    return encoding;
  }

  public static String getTagSeparator(Properties config) {
    String tagSeparator =
      config.getProperty(TaggerConfig.TAG_SEPARATOR_PROPERTY);
    if (tagSeparator == null)
      return TaggerConfig.TAG_SEPARATOR;
    return tagSeparator;
  }

}
