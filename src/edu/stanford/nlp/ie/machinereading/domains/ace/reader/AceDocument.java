
package edu.stanford.nlp.ie.machinereading.domains.ace.reader; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Logger;

import edu.stanford.nlp.ie.machinereading.common.SimpleTokenize;
import edu.stanford.nlp.ie.machinereading.domains.ace.AceReader;
import edu.stanford.nlp.util.Generics;

/**
 * Stores the ACE elements annotated in this document
 */
public class AceDocument extends AceElement  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AceDocument.class);
  /** Prefix of the files from where this doc was created */
  private String mPrefix;

  /** Value of the SOURCE XML field */
  private String mSource;

  /** All entities */
  private Map<String, AceEntity> mEntities;
  /** All entity mentions */
  private Map<String, AceEntityMention> mEntityMentions;
  /** All entity mentions in a given sentence, sorted in textual order */
  private ArrayList<ArrayList<AceEntityMention>> mSentenceEntityMentions;

  /** All relations */
  private Map<String, AceRelation> mRelations;
  /** All relation mentions */
  private Map<String, AceRelationMention> mRelationMentions;
  /** All relation mentions in a given sentence, sorted in textual order */
  private ArrayList<ArrayList<AceRelationMention>> mSentenceRelationMentions;

  /** All events */
  private Map<String, AceEvent> mEvents;
  /** All event mentions */
  private Map<String, AceEventMention> mEventMentions;
  /** All event mentions in a given sentence, sorted in textual order */
  private ArrayList<ArrayList<AceEventMention>> mSentenceEventMentions;
  
  /** The list of all tokens in the document, sorted in textual order */
  private Vector<AceToken> mTokens;
  
  /** List of all sentences in the document */
  private List<List<AceToken>> mSentences;

  /** The raw byte document, no preprocessing */
  private String mRawBuffer;

  static Logger mLog = Logger.getLogger(AceReader.class.getName());

  public AceDocument(String id) {
    super(id);

    mEntities = Generics.newHashMap();
    mEntityMentions = Generics.newHashMap();
    mSentenceEntityMentions = new ArrayList<>();

    mRelations = Generics.newHashMap();
    mRelationMentions = Generics.newHashMap();
    mSentenceRelationMentions = new ArrayList<>();

    mEvents = Generics.newHashMap();
    mEventMentions = Generics.newHashMap();
    mSentenceEventMentions = new ArrayList<>();
    
    mTokens = new Vector<>();
  }

  public void setPrefix(String p) {
    mPrefix = p;
    setSource(mPrefix);
  }

  public String getPrefix() {
    return mPrefix;
  }

  public void setSource(String p) {
    if (p.indexOf("bc/") >= 0)
      mSource = "broadcast conversation";
    else if (p.indexOf("bn/") >= 0)
      mSource = "broadcast news";
    else if (p.indexOf("cts/") >= 0)
      mSource = "telephone";
    else if (p.indexOf("nw/") >= 0)
      mSource = "newswire";
    else if (p.indexOf("un/") >= 0)
      mSource = "usenet";
    else if (p.indexOf("wl/") >= 0)
      mSource = "weblog";
    else {
      log.info("WARNING: Unknown source for doc: " + p);
      mSource = "none";
    }
  }

  public int getSentenceCount() {
    return mSentenceEntityMentions.size();
  }

  public ArrayList<AceEntityMention> getEntityMentions(int sent) {
    return mSentenceEntityMentions.get(sent);
  }

  public ArrayList<ArrayList<AceEntityMention>> getAllEntityMentions() {
    return mSentenceEntityMentions;
  }

  public ArrayList<AceRelationMention> getRelationMentions(int sent) {
    return mSentenceRelationMentions.get(sent);
  }

  public ArrayList<ArrayList<AceRelationMention>> getAllRelationMentions() {
    return mSentenceRelationMentions;
  }
  
  public ArrayList<AceEventMention> getEventMentions(int sent) {
    return mSentenceEventMentions.get(sent);
  }

  public ArrayList<ArrayList<AceEventMention>> getAllEventMentions() {
    return mSentenceEventMentions;
  }

  public AceEntity getEntity(String id) {
    return mEntities.get(id);
  }

  public Set<String> getKeySetEntities() {
    return mEntities.keySet();
  }

  public void addEntity(AceEntity e) {
    mEntities.put(e.getId(), e);
  }

  public Map<String, AceEntityMention> getEntityMentions() {
    return mEntityMentions;
  }

  public AceEntityMention getEntityMention(String id) {
    return mEntityMentions.get(id);
  }

  public void addEntityMention(AceEntityMention em) {
    mEntityMentions.put(em.getId(), em);
  }

  public AceRelation getRelation(String id) {
    return mRelations.get(id);
  }

  public void addRelation(AceRelation r) {
    mRelations.put(r.getId(), r);
  }

  public Map<String, AceRelationMention> getRelationMentions() {
    return mRelationMentions;
  }

  public AceRelationMention getRelationMention(String id) {
    return mRelationMentions.get(id);
  }

  public void addRelationMention(AceRelationMention e) {
    mRelationMentions.put(e.getId(), e);
  }
  
  public AceEvent getEvent(String id) {
    return mEvents.get(id);
  }

  public void addEvent(AceEvent r) {
    mEvents.put(r.getId(), r);
  }

  public Map<String, AceEventMention> getEventMentions() {
    return mEventMentions;
  }

  public AceEventMention getEventMention(String id) {
    return mEventMentions.get(id);
  }

  public void addEventMention(AceEventMention e) {
    mEventMentions.put(e.getId(), e);
  }

  public void addToken(AceToken t) {
    mTokens.add(t);
  }

  public int getTokenCount() {
    return mTokens.size();
  }

  public AceToken getToken(int i) {
    return mTokens.get(i);
  }

  public List<AceToken> getSentence(int index) {
    return mSentences.get(index);
  }
  
  public List<List<AceToken>> getSentences() {
    return mSentences;
  }

  public void setSentences(List<List<AceToken>> sentences) {
    mSentences = sentences;
  }

  public String toString() {
    return toXml(0);
  }

  public String toXml(int offset) {
    StringBuilder builder = new StringBuilder();
    appendOffset(builder, offset);
    builder.append("<?xml version=\"1.0\"?>\n");
    appendOffset(builder, offset);
    builder.append("<!DOCTYPE source_file SYSTEM \"apf.v5.1.2.dtd\">\n");
    appendOffset(builder, offset);
    builder.append("<source_file URI=\"" + mId + ".sgm\" SOURCE=\"" + mSource
        + "\" TYPE=\"text\" AUTHOR=\"LDC\" ENCODING=\"UTF-8\">\n");
    appendOffset(builder, offset);
    builder.append("<document DOCID=\"" + getId() + "\">\n");

    // display all entities
    Set<String> entKeys = mEntities.keySet();
    for (String key : entKeys) {
      AceEntity e = mEntities.get(key);
      builder.append(e.toXml(offset));
      builder.append("\n");
    }

    // display all relations
    Set<String> relKeys = mRelations.keySet();
    for (String key : relKeys) {
      AceRelation r = mRelations.get(key);
      if (!r.getType().equals(AceRelation.NIL_LABEL)) {
        builder.append(r.toXml(offset));
        builder.append("\n");
      }
    }
    
    // TODO: display all events

    appendOffset(builder, offset);
    builder.append("</document>\n");
    appendOffset(builder, offset);
    builder.append("</source_file>\n");
    return builder.toString();
  }

  private String tokensWithByteSpan(int start, int end) {
    StringBuilder builder = new StringBuilder();
    boolean doPrint = false;
    builder.append("...");
    for (AceToken mToken : mTokens) {
      // start printing
      if (doPrint == false && mToken.getByteOffset().start() > start - 20
              && mToken.getByteOffset().end() < end) {
        doPrint = true;
      }

      // end printing
      else if (doPrint == true && mToken.getByteOffset().start() > end + 20) {
        doPrint = false;
      }

      if (doPrint) {
        builder.append(" " + mToken.display());
      }
    }
    builder.append("...");
    return builder.toString();
  }

  /**
   * Matches all relevant mentions, i.e. entities and anchors, to tokens Note:
   * entity mentions may match with multiple tokens!
   */
  public void matchCharSeqs(String filePrefix) {
    //
    // match the head and extent of entity mentions
    //
    Set<String> keys = mEntityMentions.keySet();
    for (String key : keys) {
      AceEntityMention m = mEntityMentions.get(key);

      //
      // match the head charseq to 1+ phrase(s)
      //
      try {
        m.getHead().match(mTokens);
      } catch (MatchException e) {
        mLog.severe("READER ERROR: Failed to match entity mention head: " + "[" + m.getHead().getText() + ", "
            + m.getHead().getByteStart() + ", " + m.getHead().getByteEnd() + "]");
        mLog.severe("Document tokens: " + tokensWithByteSpan(m.getHead().getByteStart(), m.getHead().getByteEnd()));
        mLog.severe("Document prefix: " + filePrefix);
        System.exit(1);
      }

      //
      // match the extent charseq to 1+ phrase(s)
      //
      try {
        m.getExtent().match(mTokens);
      } catch (MatchException e) {
        mLog.severe("READER ERROR: Failed to match entity mention extent: " + "[" + m.getExtent().getText() + ", "
            + m.getExtent().getByteStart() + ", " + m.getExtent().getByteEnd() + "]");
        mLog.severe("Document tokens: " + tokensWithByteSpan(m.getExtent().getByteStart(), m.getExtent().getByteEnd()));
        System.exit(1);
      }

      //
      // set the head word of the mention
      //
      m.detectHeadToken(this);      
    }
    
    // we need to do this for events as well since they may not have any AceEntityMentions associated with them (if they have no arguments)
    Set<String> eventKeys = mEventMentions.keySet();
    for (String key : eventKeys) {
      AceEventMention m = mEventMentions.get(key);
      
      //
      // match the extent charseq to 1+ phrase(s)
      //
      try {
        m.getExtent().match(mTokens);
      } catch (MatchException e) {
        mLog.severe("READER ERROR: Failed to match event mention extent: " + "[" + m.getExtent().getText() + ", "
            + m.getExtent().getByteStart() + ", " + m.getExtent().getByteEnd() + "]");
        mLog.severe("Document tokens: " + tokensWithByteSpan(m.getExtent().getByteStart(), m.getExtent().getByteEnd()));
        System.exit(1);
      }
    }
  }

  public static final String XML_EXT = ".apf.xml";
  public static final String ORIG_EXT = ".sgm";

  /**
   * Parses an ACE document. Works in the following steps: (a) reads both the
   * XML annotations; (b) reads the tokens; (c) matches the tokens against the
   * annotations (d) constructs mSentenceEntityMentions and
   * mRelationEntityMentions
   */
  public static AceDocument parseDocument(String prefix, boolean usePredictedBoundaries) throws java.io.IOException,
      org.xml.sax.SAXException, javax.xml.parsers.ParserConfigurationException {
    mLog.fine("Reading document " + prefix);
    AceDocument doc = null;

    //
    // read the ACE XML annotations
    //
    if (usePredictedBoundaries == false) {
      doc = AceDomReader.parseDocument(new File(prefix + XML_EXT));
      // log.info("Parsed " + doc.getEntityMentions().size() +
      // " entities in document " + prefix);
    }

    //
    // will use the predicted entity boundaries (see below)
    //
    else {
      int lastSlash = prefix.lastIndexOf(File.separator);
      assert (lastSlash > 0 && lastSlash < prefix.length() - 1);
      String id = prefix.substring(lastSlash + 1);
      // log.info(id + ": " + prefix);
      doc = new AceDocument(id);
    }
    doc.setPrefix(prefix);

    //
    // read the raw byte stream
    //
    String trueCasedFileName = prefix + ORIG_EXT + ".truecase";
    if((new File(trueCasedFileName).exists())){
    	mLog.severe("Using truecased file: " + trueCasedFileName);
    	doc.readRawBytes(trueCasedFileName);
    } else {
    	doc.readRawBytes(prefix + ORIG_EXT);
    }

    //
    // read the AceTokens
    //
    int offsetToSubtract = 0;
    List<List<AceToken>> sentences = AceSentenceSegmenter.tokenizeAndSegmentSentences(prefix);
    doc.setSentences(sentences);
    for (List<AceToken> sentence : sentences) {
      for (AceToken token : sentence) {
        offsetToSubtract = token.adjustPhrasePositions(offsetToSubtract, token.getLiteral());
        doc.addToken(token);
      }
    }
    
    //
    // match char sequences to phrases
    //
    doc.matchCharSeqs(prefix);

    //
    // construct the mEntityMentions matrix
    //
    Set<String> entityKeys = doc.mEntityMentions.keySet();
    int sentence;
    for (String key : entityKeys) {
      AceEntityMention em = doc.mEntityMentions.get(key);
      sentence = doc.mTokens.get(em.getHead().getTokenStart()).getSentence();

      // adjust the number of rows if necessary
      while (sentence >= doc.mSentenceEntityMentions.size()) {
        doc.mSentenceEntityMentions.add(new ArrayList<>());
        doc.mSentenceRelationMentions.add(new ArrayList<>());
        doc.mSentenceEventMentions.add(new ArrayList<>());
      }

      // store the entity mentions in increasing order:
      // (a) of the start position of their head
      // (b) if start is the same, in increasing order of the head end
      ArrayList<AceEntityMention> sentEnts = doc.mSentenceEntityMentions.get(sentence);
      boolean added = false;
      for (int i = 0; i < sentEnts.size(); i++) {
        AceEntityMention crt = sentEnts.get(i);
        if ((crt.getHead().getTokenStart() > em.getHead().getTokenStart())
            || (crt.getHead().getTokenStart() == em.getHead().getTokenStart() && crt.getHead().getTokenEnd() > em
                .getHead().getTokenEnd())) {
          sentEnts.add(i, em);
          added = true;
          break;
        }
      }
      if (!added) {
        sentEnts.add(em);
      }
    }

    // 
    // construct the mRelationMentions matrix
    //
    Set<String> relKeys = doc.mRelationMentions.keySet();
    for (String key : relKeys) {
      AceRelationMention rm = doc.mRelationMentions.get(key);
      sentence = doc.mTokens.get(rm.getArg(0).getHead().getTokenStart()).getSentence();

      //
      // no need to adjust the number of rows: was done above
      //

      // store the relation mentions in increasing order
      // (a) of the start position of their head, or
      // (b) if start is the same, in increasing order of ends
      ArrayList<AceRelationMention> sentRels = doc.mSentenceRelationMentions.get(sentence);
      boolean added = false;
      for (int i = 0; i < sentRels.size(); i++) {
        AceRelationMention crt = sentRels.get(i);
        if ((crt.getMinTokenStart() > rm.getMinTokenStart())
            || (crt.getMinTokenStart() == rm.getMinTokenStart() && crt.getMaxTokenEnd() > rm.getMaxTokenEnd())) {
          sentRels.add(i, rm);
          added = true;
          break;
        }
      }
      if (!added) {
        sentRels.add(rm);
      }
    }
    
    // 
    // construct the mEventMentions matrix
    //
    Set<String> eventKeys = doc.mEventMentions.keySet();
    for (String key : eventKeys) {
      AceEventMention em = doc.mEventMentions.get(key);
      sentence = doc.mTokens.get(em.getMinTokenStart()).getSentence();

      /*
       * adjust the number of rows if necessary -- if you're wondering why we do
       * this here again, (after we've done it for entities) it's because we can
       * have an event with no entities near the end of the document and thus
       * won't have created rows in mSentence*Mentions
       */
      while (sentence >= doc.mSentenceEntityMentions.size()) {
        doc.mSentenceEntityMentions.add(new ArrayList<>());
        doc.mSentenceRelationMentions.add(new ArrayList<>());
        doc.mSentenceEventMentions.add(new ArrayList<>());
      }

      // store the event mentions in increasing order
      // (a) first, event mentions with no arguments
      // (b) then by the start position of their head, or
      // (c) if start is the same, in increasing order of ends
      ArrayList<AceEventMention> sentEvents = doc.mSentenceEventMentions.get(sentence);
      boolean added = false;
      for (int i = 0; i < sentEvents.size(); i++) {
        AceEventMention crt = sentEvents.get(i);
        if ((crt.getMinTokenStart() > em.getMinTokenStart())
            || (crt.getMinTokenStart() == em.getMinTokenStart() && crt.getMaxTokenEnd() > em.getMaxTokenEnd())) {
          sentEvents.add(i, em);
          added = true;
          break;
        }
      }
      if (!added) {
        sentEvents.add(em);
      }
    }
    
    return doc;
  }

  //
  // heeyoung : skip relation, event parsing part - for ACE2004 
  //
  public static AceDocument parseDocument(String prefix, boolean usePredictedBoundaries, String AceVersion) throws java.io.IOException,
      org.xml.sax.SAXException, javax.xml.parsers.ParserConfigurationException {
    mLog.fine("Reading document " + prefix);
    AceDocument doc = null;

    //
    // read the ACE XML annotations
    //
    if (usePredictedBoundaries == false) {
      doc = AceDomReader.parseDocument(new File(prefix + XML_EXT));
      // log.info("Parsed " + doc.getEntityMentions().size() +
      // " entities in document " + prefix);
    }

    //
    // will use the predicted entity boundaries (see below)
    //
    else {
      int lastSlash = prefix.lastIndexOf(File.separator);
      assert (lastSlash > 0 && lastSlash < prefix.length() - 1);
      String id = prefix.substring(lastSlash + 1);
      // log.info(id + ": " + prefix);
      doc = new AceDocument(id);
    }
    doc.setPrefix(prefix);

    //
    // read the raw byte stream
    //
    String trueCasedFileName = prefix + ORIG_EXT + ".truecase";
    if((new File(trueCasedFileName).exists())){
    	mLog.severe("Using truecased file: " + trueCasedFileName);
    	doc.readRawBytes(trueCasedFileName);
    } else {
    	doc.readRawBytes(prefix + ORIG_EXT);
    }

    //
    // read the AceTokens
    //
    int offsetToSubtract = 0;
    List<List<AceToken>> sentences = AceSentenceSegmenter.tokenizeAndSegmentSentences(prefix);
    doc.setSentences(sentences);
    for (List<AceToken> sentence : sentences) {
      for (AceToken token : sentence) {
        offsetToSubtract = token.adjustPhrasePositions(offsetToSubtract, token.getLiteral());
        doc.addToken(token);
      }
    }
    
    //
    // match char sequences to phrases
    //
    doc.matchCharSeqs(prefix);

    //
    // construct the mEntityMentions matrix
    //
    Set<String> entityKeys = doc.mEntityMentions.keySet();
    int sentence;
    for (String key : entityKeys) {
      AceEntityMention em = doc.mEntityMentions.get(key);
      sentence = doc.mTokens.get(em.getHead().getTokenStart()).getSentence();

      // adjust the number of rows if necessary
      while (sentence >= doc.mSentenceEntityMentions.size()) {
        doc.mSentenceEntityMentions.add(new ArrayList<>());
        doc.mSentenceRelationMentions.add(new ArrayList<>());
        doc.mSentenceEventMentions.add(new ArrayList<>());
      }

      // store the entity mentions in increasing order:
      // (a) of the start position of their head
      // (b) if start is the same, in increasing order of the head end
      ArrayList<AceEntityMention> sentEnts = doc.mSentenceEntityMentions.get(sentence);
      boolean added = false;
      for (int i = 0; i < sentEnts.size(); i++) {
        AceEntityMention crt = sentEnts.get(i);
        if ((crt.getHead().getTokenStart() > em.getHead().getTokenStart())
            || (crt.getHead().getTokenStart() == em.getHead().getTokenStart() && crt.getHead().getTokenEnd() > em
                .getHead().getTokenEnd())) {
          sentEnts.add(i, em);
          added = true;
          break;
        }
      }
      if (!added) {
        sentEnts.add(em);
      }
    }

    return doc;
  }


  // TODO: never used?
  public void constructSentenceRelationMentions() {
    // 
    // construct the mRelationEntityMentions matrix
    //
    Set<String> relKeys = mRelationMentions.keySet();
    for (String key : relKeys) {
      AceRelationMention rm = mRelationMentions.get(key);
      int sentence = mTokens.get(rm.getArg(0).getHead().getTokenStart()).getSentence();

      //
      // no need to adjust the number of rows: was done in parseDocument
      //

      // store the relation mentions in increasing order
      // (a) of the start position of their head, or
      // (b) if start is the same, in increasing order of ends
      ArrayList<AceRelationMention> sentRels = mSentenceRelationMentions.get(sentence);
      boolean added = false;
      for (int i = 0; i < sentRels.size(); i++) {
        AceRelationMention crt = sentRels.get(i);
        if ((crt.getMinTokenStart() > rm.getMinTokenStart())
            || (crt.getMinTokenStart() == rm.getMinTokenStart() && crt.getMaxTokenEnd() > rm.getMaxTokenEnd())) {
          sentRels.add(i, rm);
          added = true;
          break;
        }
      }
      if (!added) {
        sentRels.add(rm);
      }
    }
  }

  /**
   * Verifies if the two tokens are part of the same chunk
   */
  public boolean sameChunk(int left, int right) {
    for (int i = right; i > left; i--) {
      String chunk = AceToken.OTHERS.get(getToken(i).getChunk());
      if (!chunk.startsWith("I-"))
        return false;
      String word = AceToken.WORDS.get(getToken(i).getWord());
      if (word.equals(",") || word.equals("(") || word.equals("-"))
        return false;
    }
    String leftChunk = AceToken.OTHERS.get(getToken(left).getChunk());
    if (leftChunk.equals("O"))
      return false;
    return true;
  }

  public boolean isChunkHead(int pos) {
    String next = AceToken.OTHERS.get(getToken(pos + 1).getChunk());
    if (next.startsWith("I-"))
      return false;
    return true;
  }

  public int findChunkEnd(int pos) {
    String crt = AceToken.OTHERS.get(getToken(pos).getChunk());
    if (crt.equals("O"))
      return pos;

    for (pos = pos + 1; pos < getTokenCount(); pos++) {
      crt = AceToken.OTHERS.get(getToken(pos).getChunk());
      if (!crt.startsWith("I-"))
        break;
    }

    return pos - 1;
  }

  public int findChunkStart(int pos) {
    String crt = AceToken.OTHERS.get(getToken(pos).getChunk());
    if (crt.equals("O") || crt.startsWith("B-"))
      return pos;

    for (pos = pos - 1; pos >= 0; pos--) {
      crt = AceToken.OTHERS.get(getToken(pos).getChunk());
      if (crt.startsWith("B-"))
        break;
    }

    return pos;
  }

  public boolean isApposition(int left, int right) {
    int leftEnd = findChunkEnd(left);
    int rightStart = findChunkStart(right);

    if (rightStart == leftEnd + 1)
      return true;

    if (rightStart == leftEnd + 2) {
      String comma = AceToken.WORDS.get(getToken(leftEnd + 1).getWord());
      if (comma.equals(",") || comma.equals("-") || comma.equals("_")) {
        return true;
      }
    }

    return false;
  }

  public int countVerbs(int start, int end) {
    int count = 0;
    for (int i = start; i < end; i++) {
      String crt = AceToken.OTHERS.get(getToken(i).getPos());
      if (crt.startsWith("VB"))
        count++;
    }
    return count;
  }

  public int countCommas(int start, int end) {
    int count = 0;
    for (int i = start; i < end; i++) {
      String crt = AceToken.WORDS.get(getToken(i).getWord());
      if (crt.equals(","))
        count++;
    }
    return count;
  }

  private void readRawBytes(String fileName) throws IOException {
    BufferedReader in = new BufferedReader(new FileReader(fileName));
    StringBuilder builder = new StringBuilder();
    int c;
    while ((c = in.read()) >= 0)
      builder.append((char) c);
    mRawBuffer = builder.toString();
    // System.out.println(mRawBuffer);
    in.close();
  }

  @SuppressWarnings("unused")
  private void readPredictedEntityBoundaries(BufferedReader is) throws java.io.IOException {
    // System.out.println("Reading boundaries from file: " + mPrefix);

    //
    // read Massi's B-ENT, I-ENT, or O labels
    //
    ArrayList<String> labels = new ArrayList<>();
    String line;
    while ((line = is.readLine()) != null) {
      ArrayList<String> tokens = SimpleTokenize.tokenize(line);
      if (tokens.isEmpty() == false)
        labels.add(tokens.get(0));
    }
    assert (labels.size() == mTokens.size());

    int entityId = 1;

    //
    // traverse the label array and create entities as needed
    //
    for (int i = 0; i < labels.size(); i++) {
      // System.out.println(labels.get(i));
      if (labels.get(i).startsWith("B-") || labels.get(i).startsWith("I-")) { // Massi's
                                                                              // ents
                                                                              // may
                                                                              // start
                                                                              // with
                                                                              // I-ENT
        int startToken = i;
        int endToken = i + 1;
        while (endToken < labels.size() && labels.get(endToken).startsWith("I-"))
          endToken++;

        //
        // Set the type/subtype to whatever Massi predicted
        // This is not directly used in this system. It is needed only
        // to generate the APF files with Massi info, which are needed
        // by Edgar. Otherwise type/subtype could be safely set to "none".
        //
        String label = labels.get(startToken);
        int dash = label.indexOf("-", 2);
        if (dash <= 2 || dash >= label.length()) {
          throw new RuntimeException(label);
        }
        assert (dash > 2 && dash < label.length() - 1);
        String type = label.substring(2, dash);
        String subtype = label.substring(dash + 1);
        /*
         * String type = "none"; String subtype = "none";
         */

        // create a new entity between [startToken, endToken)
        makeEntity(startToken, endToken, entityId, type, subtype);

        // skip over this entity
        i = endToken - 1;
        entityId++;
      } else {
        assert (labels.get(i).equals("O"));
      }
    }
  }

  public AceCharSeq makeCharSeq(int startToken, int endToken) {
    /*
     * StringBuilder buf = new StringBuilder(); for(int i = startToken; i <
     * endToken; i ++){ if(i > startToken) buf.append(" ");
     * buf.append(mTokens.get(i).getLiteral()); }
     */
    startToken = Math.max(0, startToken);
    while (mTokens.get(startToken).getByteStart() < 0)
      // SGML token
      startToken++;
    endToken = Math.min(endToken, mTokens.size());
    while (mTokens.get(endToken - 1).getByteStart() < 0)
      // SGML token
      endToken--;
    assert (endToken > startToken);

    String text = mRawBuffer.substring(mTokens.get(startToken).getRawByteStart(), mTokens.get(endToken - 1)
        .getRawByteEnd());

    /*
     * if(mTokens.get(startToken).getByteStart() > mTokens.get(endToken -
     * 1).getByteEnd() - 1){ for(int i = startToken; i < endToken; i ++){
     * System.out.println("Token: " + mTokens.get(i).display()); } }
     */
    return new AceCharSeq(text, // buf.toString(),
        mTokens.get(startToken).getByteStart(), mTokens.get(endToken - 1).getByteEnd() - 1);
  }

  /** Makes an ACE entity from the span [startToken, endToken) */
  private void makeEntity(int startToken, int endToken, int id, String type, String subtype) {
    String eid = mId + "-E" + id;
    AceEntity ent = new AceEntity(eid, type, subtype, "SPC");
    addEntity(ent);

    AceCharSeq cseq = makeCharSeq(startToken, endToken);
    String emid = mId + "-E" + id + "-1";
    AceEntityMention entm = new AceEntityMention(emid, "NOM", "NOM", cseq, cseq);
    addEntityMention(entm);
    ent.addMention(entm);
  }
}
