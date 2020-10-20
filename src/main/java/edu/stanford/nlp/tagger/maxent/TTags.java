package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.tagger.common.Tagger;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.*;

/**
 * This class holds the POS tags, assigns them unique ids, and knows which tags
 * are open versus closed class.
 * <p>
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Company:      Stanford University<p>
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class TTags {

  private Index<String> index = new HashIndex<>();
  private final Set<String> closed = Generics.newHashSet();
  private Set<String> openTags; // = null; /* cache */
  private transient String[] openTagsArr; // = null; used for cache
  private final boolean isEnglish; // for speed
  private static final boolean doDeterministicTagExpansion = true;


  /** If true, then the open tags are fixed and we set closed tags based on
   *  index-openTags; otherwise, we set open tags based on index-closedTags.
   */
  private boolean openFixed = false;

  /** When making a decision based on the training data as to whether a
   *  tag is closed, this is the threshold for how many tokens can be in
   *  a closed class - purposely conservative.
   * TODO: make this an option you can set; need to pass in TaggerConfig object and then can say = config.getClosedTagThreshold());
   */
  private final int closedTagThreshold = Integer.parseInt(TaggerConfig.CLOSED_CLASS_THRESHOLD);

  /** If true, when a model is trained, all tags that had fewer tokens than
   *  closedTagThreshold will be considered closed.
   */
  private boolean learnClosedTags = false;


  public TTags() {
    isEnglish = false;
  }

  /*
  public TTags(TaggerConfig config) {
    String[] closedArray = config.getClosedClassTags();
    String[] openArray = config.getOpenClassTags();
    if(closedArray.length > 0) {
      closed = Generics.newHashSet(Arrays.asList(closedArray));
    } else if(openArray.length > 0) {
      openTags = Generics.newHashSet(Arrays.asList(openArray));
    } else {
      learnClosedTags = config.getLearnClosedClassTags();
      closedTagThreshold = config.getClosedTagThreshold();
    }
  }
  */

  TTags(String language) {
    if (language.equalsIgnoreCase("english")) {
      closed.add(".");
      closed.add(",");
      closed.add("``");
      closed.add("''");
      closed.add(":");
      closed.add("$");
      closed.add("EX");
      closed.add("(");
      closed.add(")");
      closed.add("#");
      closed.add("MD");
      closed.add("CC");
      closed.add("DT");
      closed.add("LS");
      closed.add("PDT");
      closed.add("POS");
      closed.add("PRP");
      closed.add("PRP$");
      closed.add("RP");
      closed.add("TO");
      closed.add(Tagger.EOS_TAG);
      closed.add("UH");
      closed.add("WDT");
      closed.add("WP");
      closed.add("WP$");
      closed.add("WRB");
      closed.add("-LRB-");
      closed.add("-RRB-");
      //  closed.add("IN");
      isEnglish = true;
    } else if(language.equalsIgnoreCase("polish")) {
      closed.add(".");
      closed.add(",");
      closed.add("``");
      closed.add("''");
      closed.add(":");
      closed.add("$");
      closed.add("(");
      closed.add(")");
      closed.add("#");
      closed.add("POS");
      closed.add(Tagger.EOS_TAG);
      closed.add("ppron12");
      closed.add("ppron3");
      closed.add("siebie");
      closed.add("qub");
      closed.add("conj");
      isEnglish = false;
    } else if(language.equalsIgnoreCase("chinese")) {
      /* chinese treebank 5 tags */
      closed.add("AS");
      closed.add("BA");
      closed.add("CC");
      closed.add("CS");
      closed.add("DEC");
      closed.add("DEG");
      closed.add("DER");
      closed.add("DEV");
      closed.add("DT");
      closed.add("ETC");
      closed.add("IJ");
      closed.add("LB");
      closed.add("LC");
      closed.add("P");
      closed.add("PN");
      closed.add("PU");
      closed.add("SB");
      closed.add("SP");
      closed.add("VC");
      closed.add("VE");
      isEnglish = false;
    } else if (language.equalsIgnoreCase("arabic")) {
      // kulick tag set
      // the following tags seem to be complete sets in the training
      // data (see the comments for "german" for more info)
      closed.add("PUNC");
      closed.add("CC");
      closed.add("CPRP$");
      closed.add(Tagger.EOS_TAG);
      // maybe more should still be added ... cdm jun 2006
      isEnglish = false;
    } else if(language.equalsIgnoreCase("german")) {
      // The current version of the German tagger is built with the
      // negra-tiger data set.  We use the STTS tag set.  In
      // particular, we use the version with the changes described in
      // appendix A-2 of
      // http://www.uni-potsdam.de/u/germanistik/ls_dgs/tiger1-intro.pdf
      // eg the STTS tag set with PROAV instead of PAV
      // To find the closed tags, we use lists of standard closed German
      // tags, eg
      // http://www.sfs.uni-tuebingen.de/Elwis/stts/Wortlisten/WortFormen.html
      // In other words:
      //
      // APPO APPR APPRART APZR ART KOKOM KON KOUI KOUS PDAT PDS PIAT
      // PIDAT PIS PPER PPOSAT PPOSS PRELAT PRELS PRF PROAV PTKA
      // PTKANT PTKNEG PTKVZ PTKZU PWAT PWAV PWS VAFIN VAIMP VAINF
      // VAPP VMFIN VMINF VMPP
      //
      // One issue with this is that our training data does not have
      // the complete collection of many of these closed tags.  For
      // example, words with the tag APPR show up in the test or dev
      // sets without ever showing up in the training.  Tags that
      // don't have this property:
      //
      // KOKOM PPOSS PTKA PTKNEG PWAT VAINF VAPP VMINF VMPP
      closed.add("$,");
      closed.add("$.");
      closed.add("$(");
      closed.add("--"); // this shouldn't be a tag of the dataset, but was a conversion bug!
      closed.add(Tagger.EOS_TAG);
      closed.add("KOKOM");
      closed.add("PPOSS");
      closed.add("PTKA");
      closed.add("PTKNEG");
      closed.add("PWAT");
      closed.add("VAINF");
      closed.add("VAPP");
      closed.add("VMINF");
      closed.add("VMPP");
      isEnglish = false;
    } else if (language.equalsIgnoreCase("french")) {
      // Using the french treebank, with Spence's adaptations of
      // Candito's treebank modifications, we get that only the
      // punctuation tags are reliably closed:
      // !, ", *, ,, -, -LRB-, -RRB-, ., ..., /, :, ;, =, ?, [, ]
      closed.add("!");
      closed.add("\"");
      closed.add("*");
      closed.add(",");
      closed.add("-");
      closed.add("-LRB-");
      closed.add("-RRB-");
      closed.add(".");
      closed.add("...");
      closed.add("/");
      closed.add(":");
      closed.add(";");
      closed.add("=");
      closed.add("?");
      closed.add("[");
      closed.add("]");
      isEnglish = false;
    } else if (language.equalsIgnoreCase("spanish")) {
      closed.add(Tagger.EOS_TAG);

      // conjunctions
      closed.add("cc");
      closed.add("cs");

      // punctuation
      closed.add("faa");
      closed.add("fat");
      closed.add("fc");
      closed.add("fca");
      closed.add("fct");
      closed.add("fd");
      closed.add("fe");
      closed.add("fg");
      closed.add("fh");
      closed.add("fia");
      closed.add("fit");
      closed.add("fla");
      closed.add("flt");
      closed.add("fp");
      closed.add("fpa");
      closed.add("fpt");
      closed.add("fra");
      closed.add("frc");
      closed.add("fs");
      closed.add("ft");
      closed.add("fx");
      closed.add("fz");

      isEnglish = false;
    } else if (language.equalsIgnoreCase("medpost")) {
      closed.add(".");
      closed.add(",");
      closed.add("``");
      closed.add("''");
      closed.add(":");
      closed.add("$");
      closed.add("EX");
      closed.add("(");
      closed.add(")");
      closed.add("VM");
      closed.add("CC");
      closed.add("DD");
      closed.add("DB");
      closed.add("GE");
      closed.add("PND");
      closed.add("PNG");
      closed.add("TO");
      closed.add(Tagger.EOS_TAG);
      closed.add("-LRB-");
      closed.add("-RRB-");
      isEnglish = false;
    } else if (language.equalsIgnoreCase("testing")) {
      closed.add(".");
      closed.add(Tagger.EOS_TAG);
      isEnglish = false;
    } else if (language.equalsIgnoreCase("")) {
      isEnglish = false;
    }
    /* add closed-class lists for other languages here */
    else {
      throw new RuntimeException("unknown language: " + language);
    }
  }


  /** Return the Set of tags used by this tagger (available after training the tagger).
   *
   * @return The Set of tags used by this tagger
   */
  public Set<String> tagSet() {
    return new HashSet<>(index.objectsList());
  }


  /**
   * Returns a list of all open class tags
   * @return set of open tags
   */
  public synchronized Set<String> getOpenTags() {
    if (openTags == null) { /* cache check */
      Set<String> open = Generics.newHashSet();

      for (String tag : index) {
        if ( ! closed.contains(tag)) {
          open.add(tag);
        }
      }

      openTags = open;
      openTagsArr = null;
    } // if
    return openTags;
  }

  /**
   * Returns a list of all open class tags as an array.
   * This saves a little time in TestSentence.
   *
   * @return array of open tags
   */
  public synchronized String[] getOpenTagsArray() {
    if (openTagsArr == null) {
      Set<String> open = getOpenTags();
      openTagsArr = deterministicallyExpandTags(open.toArray(StringUtils.EMPTY_STRING_ARRAY));
    }
    return openTagsArr;
  }

  protected int add(String tag) {
    return index.addToIndex(tag);
  }

  public String getTag(int i) {
    return index.get(i);
  }

  protected void save(String filename,
                      Map<String, Set<String>> tagTokens) {
    try {
      DataOutputStream out = IOUtils.getDataOutputStream(filename);
      save(out, tagTokens);
      out.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  protected void save(DataOutputStream file,
                      Map<String, Set<String>> tagTokens) {
    try {
      file.writeInt(index.size());
      for (String item : index) {
        file.writeUTF(item);
        if (learnClosedTags) {
          if (tagTokens.get(item).size() < closedTagThreshold) {
            markClosed(item);
          }
        }
        file.writeBoolean(isClosed(item));
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }


  protected void read(String filename) {
    try {
      DataInputStream in = IOUtils.getDataInputStream(filename);
      read(in);
      in.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  protected void read(DataInputStream file) {
    try {
      int size = file.readInt();
      index = new HashIndex<>();
      for (int i = 0; i < size; i++) {
        String tag = file.readUTF();
        boolean inClosed = file.readBoolean();
        index.add(tag);

        if (inClosed) closed.add(tag);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }


  protected boolean isClosed(String tag) {
    return openFixed ? !openTags.contains(tag) : closed.contains(tag);
  }

  void markClosed(String tag) {
    add(tag);
    closed.add(tag);
    if (!openFixed) {
      openTagsArr = null;
    }
  }

  @SuppressWarnings("unused")
  public void setLearnClosedTags(boolean learn) {
    learnClosedTags = learn;
  }

  public synchronized void setOpenClassTags(String[] openClassTags) {
    openTags = Generics.newHashSet();
    openTags.addAll(Arrays.asList(openClassTags));
    for (String tag : openClassTags) {
      add(tag);
    }
    openTagsArr = openClassTags;
    openFixed = true;
  }

  public void setClosedClassTags(String[] closedClassTags) {
    for(String tag : closedClassTags) {
      markClosed(tag);
    }
  }


  int getIndex(String tag) {
    return index.indexOf(tag);
  }

  public int getSize() {
    return index.size();
  }

  /**
   * Deterministically adds other possible tags for words given observed tags.
   * For instance, for English with the Penn POS tag, a word with the VB
   * tag would also be expected to have the VBP tag.
   * <p>
   * The current implementation is a bit contorted, as it works to avoid
   * object allocations wherever possible for maximum runtime speed. But
   * intuitively it's just: For English (only),
   * if the VBD tag is present but not VBN, add it, and vice versa;
   * if the VB tag is present but not VBP, add it, and vice versa.
   *
   * @param tags Known possible tags for the word
   * @return A superset of tags
   */
  String[] deterministicallyExpandTags(String[] tags) {
    if (!isEnglish || !doDeterministicTagExpansion) {
      // no tag expansion for other languages currently
      return tags;
    }
    boolean seenVBN = false, seenVBD = false, seenVB  = false, seenVBP = false;
    for (String tag : tags) {
      char ch = tag.charAt(0);
      if (ch == 'V') {
        switch (tag) {
        case "VBD":
          seenVBD = true;
          break;
        case "VBN":
          seenVBN = true;
          break;
        case "VB":
          seenVB = true;
          break;
        case "VBP":
          seenVBP = true;
          break;
        }
      }
    }
    int toAdd = 0;
    if (seenVBN ^ seenVBD) { // ^ is xor
      toAdd++;
    }
    if (seenVB ^ seenVBP) {
      toAdd++;
    }
    if (toAdd == 0) {
      return tags;
    }
    int ind = tags.length;
    String[] newTags = new String[ind + toAdd];
    System.arraycopy(tags, 0, newTags, 0, tags.length);
    if (seenVBN && ! seenVBD) {
      newTags[ind++] = "VBD";
    } else if (seenVBD && ! seenVBN) {
      newTags[ind++] = "VBN";
    }
    if (seenVB && ! seenVBP) {
      newTags[ind] = "VBP";
    } else if (seenVBP && ! seenVB) {
      newTags[ind] = "VB";
    }
    return newTags;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder(200).append(index).append(' ');
    if (openFixed) {
      s.append(" OPEN:").append(getOpenTags());
    } else {
      s.append(" open:").append(getOpenTags()).append(" CLOSED:").append(closed);
    }
    return s.toString();
  }
}
