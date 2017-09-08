package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.io.InDataStreamFile;
import old.edu.stanford.nlp.io.OutDataStreamFile;
import old.edu.stanford.nlp.io.RuntimeIOException;
import old.edu.stanford.nlp.util.Index;
import old.edu.stanford.nlp.util.HashIndex;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.*;

/**
 * This class holds the POS tags, assigns them unique ids, and knows which tags
 * are open versus closed class.
 * <p/>
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Company:      Stanford University<p>
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class TTags {

  private Index<String> index = new HashIndex<String>();
  private final HashSet<String> closed = new HashSet<String>();
  private HashSet<String> openTags = null; /* cache */
  private boolean isEnglish = false; // for speed

  /** If true, then the open tags are fixed and we set closed tags based on
   *  index-openTags; otherwise, we set open tags based on index-closedTags.
   */
  private boolean openFixed = false;

  static final int CLOSED_TAG_THRESHOLD = 40;

  /** When making a decision based on the training data as to whether a
   *  tag is closed, this is the threshold for how many tokens can be in
   *  a closed class - purposely conservative.
   */
  private final int closedTagThreshold = CLOSED_TAG_THRESHOLD;

  /** If true, when a model is trained, all tags that had fewer tokens than
   *  closedTagThreshold will be considered closed.
   */
  private boolean learnClosedTags = false;

  /** Caches values returned by deterministicallyExpandTags.
   *  This assumes that each word comes always with the same tag (wasn't proven false so far).
   */
  private final Map<String,String[]> deterministicExpansionMemoizer = new HashMap<String,String[]>();

  public TTags() {
  }

  /*
  public TTags(TaggerConfig config) {
    String[] closedArray = config.getClosedClassTags();
    String[] openArray = config.getOpenClassTags();
    if(closedArray.length > 0) {
      closed = new HashSet<String>(Arrays.asList(closedArray));
    } else if(openArray.length > 0) {
      openTags = new HashSet<String>(Arrays.asList(openArray));
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
      closed.add("EOS");
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
      closed.add("EOS");
      closed.add("ppron12");
      closed.add("ppron3");
      closed.add("siebie");
      closed.add("qub");
      closed.add("conj");
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
    } else if (language.equalsIgnoreCase("arabic")) {
      closed.add("PUNC");
      closed.add("CONJ");
      // maybe more should still be added ... cdm jun 2006
    } else if(language.equalsIgnoreCase("german")) {
      closed.add("$,");
      closed.add("$.");
      closed.add("$");
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
      closed.add("EOS");
      closed.add("-LRB-");
      closed.add("-RRB-");
    } else if (language.equalsIgnoreCase("vietnamese")) {
      this.closed.add(".");
      this.closed.add(",");
      this.closed.add(":");
      this.closed.add("(");
      this.closed.add(")");
      this.closed.add("LBKT");
      this.closed.add("RBKT");
      this.closed.add("#");
      this.closed.add("/");
      this.closed.add("-");
      this.closed.add("&");
      this.closed.add("...");
      this.closed.add(";");
      this.closed.add("?");
      this.closed.add("'");
      this.closed.add("I");
      this.closed.add("Q");
      this.closed.add("Vb");
      this.closed.add("Vy");
      this.closed.add("CC");
      this.closed.add("Y");
      this.closed.add("Z");
      this.closed.add("SLASH");
    } else if (language.equalsIgnoreCase("")) {
    }
    /* add closed-class lists for other languages here */
    else {
      throw new RuntimeException("unknown language: " + language);
    }
  }


  /**
   * Returns a list of all open class tags
   * @return set of open tags
   */
  public Set<String> getOpenTags() {
    if (openTags == null) { /* cache check */
      HashSet<String> open = new HashSet<String>();

      for (String tag : index) {
        if ( ! closed.contains(tag)) {
          open.add(tag);
        }
      }

      openTags = open;
    } // if
    return openTags;
  }

  protected int add(String tag) {
    index.add(tag);
    return index.indexOf(tag);
  }

  public String getTag(int i) {
    return index.get(i);
  }

  protected void save(String filename) {
    try {
      DataOutputStream out = new OutDataStreamFile(filename);

      out.writeInt(index.size());
      for (String item : index) {
        out.writeUTF(item);
        if (learnClosedTags) {
          if(GlobalHolder.tagTokens.get(item).size() < closedTagThreshold) {
            markClosed(item);
          }
        }
        out.writeBoolean(isClosed(item));
      }

      out.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  protected void save(DataOutputStream file) {
    try {
      file.writeInt(index.size());
      for (String item : index) {
        file.writeUTF(item);
        if (learnClosedTags) {
          if(GlobalHolder.tagTokens.get(item).size() < closedTagThreshold) {
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
      InDataStreamFile in = new InDataStreamFile(filename);
      read(in);
      in.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void read(DataInputStream file) {
    try {
      int size = file.readInt();
      index = new HashIndex<String>();
      for (int i = 0; i < size; i++) {
        String tag = file.readUTF();
        boolean inClosed = file.readBoolean();
        index.add(tag);

        if (inClosed) closed.add(tag);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  protected boolean isClosed(String tag) {
    if (openFixed) {
      return !openTags.contains(tag);
    } else {
      return closed.contains(tag);
    }
  }

  void markClosed(String tag) {
    closed.add(tag);
  }

  public void setLearnClosedTags(boolean learn) {
    learnClosedTags = learn;
  }

  public void setOpenClassTags(String[] openClassTags) {
    openTags = new HashSet<String>();
    openTags.addAll(Arrays.asList(openClassTags));
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
   * (CDM May 2007: This was putting repeated values into the set of possible
   * tags, which was bad.  Now it doesn't, but the resulting code is a funny
   * mixture of trying to micro-optimize, and just using equals() inside a
   * List linear scan....
   *
   * @param tags Known possible tags for the word
   * @param word The word (currently not a used parameter)
   * @return A superset of tags
   */
  @SuppressWarnings({"UnusedDeclaration"})
  String[] deterministicallyExpandTags(String[] tags, String word) {
    String[] cached;
    synchronized(deterministicExpansionMemoizer) { cached = deterministicExpansionMemoizer.get(word); }
    if(cached != null)
      return cached;
    if (isEnglish) {
      ArrayList<String> tl = new ArrayList<String>(tags.length + 2);
      int yVBD = GlobalHolder.tags.getIndex("VBD");
      int yVBN = GlobalHolder.tags.getIndex("VBN");
      int yVBP = GlobalHolder.tags.getIndex("VBP");
      int yVB = GlobalHolder.tags.getIndex("VB");
      if (yVBD < 0 || yVBN < 0 || yVBP < 0 || yVB < 0) {
        System.err.println("Language erroneously set to 'english' when it isn't UPenn English tag set!!");
        return tags;
      }

      for (String tag : tags) {
        int y = GlobalHolder.tags.getIndex(tag);
        assert (y >= 0);
        addIfAbsent(tl, tag);
        if (y == yVBD) {
          addIfAbsent(tl, "VBN");
        } else if (y == yVBN) {
          addIfAbsent(tl, "VBD");
        } else if (y == yVB) {
          addIfAbsent(tl, "VBP");
        } else if (y == yVBP) {
          addIfAbsent(tl, "VB");
        }
      } // end for i
      String[] newtags = tl.toArray(new String[tl.size()]);
      synchronized(deterministicExpansionMemoizer) { deterministicExpansionMemoizer.put(word,newtags); }
      return newtags;
    } else {
      // no tag expansion for other languages currently
      return tags;
    }
  }

  private static void addIfAbsent(List<String> list, String item) {
    if ( ! list.contains(item)) {
      list.add(item);
    }
  }

}
