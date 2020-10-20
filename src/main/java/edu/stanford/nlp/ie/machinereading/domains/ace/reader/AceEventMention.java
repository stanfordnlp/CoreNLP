
package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.Generics;

/**
 * Stores one ACE event mention
 */
public class AceEventMention extends AceMention {

  /** Maps argument roles to argument mentions */
  private Map<String, AceEventMentionArgument> mRolesToArguments;

  /** the parent event */
  private AceEvent mParent;

  /** anchor text for this event */
  private AceCharSeq mAnchor;

  public AceEventMention(String id, AceCharSeq extent, AceCharSeq anchor) {
    super(id, extent);
    mRolesToArguments = Generics.newHashMap();
    this.mAnchor = anchor;
  }

  @Override
  public String toString() {
    return "AceEventMention [mAnchor=" + mAnchor + ", mParent=" + mParent
        + ", mRolesToArguments=" + mRolesToArguments + ", mExtent=" + mExtent
        + ", mId=" + mId + "]";
  }

  public Collection<AceEventMentionArgument> getArgs() {
    return mRolesToArguments.values();
  }

  public Set<String> getRoles() {
    return mRolesToArguments.keySet();
  }

  public AceEntityMention getArg(String role) {
    return mRolesToArguments.get(role).getContent();
  }

  public void addArg(AceEntityMention em, String role) {
    mRolesToArguments.put(role, new AceEventMentionArgument(role, em));
  }

  public void setParent(AceEvent e) {
    mParent = e;
  }

  public AceEvent getParent() {
    return mParent;
  }

  public void setAnchor(AceCharSeq anchor) {
    mAnchor = anchor;
  }

  public AceCharSeq getAnchor() {
    return mAnchor;
  }

  /** Fetches the id of the sentence that contains this mention */
  // TODO disabled until we tie in sentence boundaries
  // public int getSentence(AceDocument doc) {
  // return doc.getToken(getArg(0).getHead().getTokenStart()).getSentence();
  // }

  /**
   * Returns the smallest start of all argument heads (or the beginning of the
   * mention's extent if there are no arguments)
   */
  public int getMinTokenStart() {
    Collection<AceEventMentionArgument> args = getArgs();
    int earliestTokenStart = -1;
    for (AceEventMentionArgument arg : args) {
      int tokenStart = arg.getContent().getHead().getTokenStart();
      if (earliestTokenStart == -1)
        earliestTokenStart = tokenStart;
      else
        earliestTokenStart = Math.min(earliestTokenStart, tokenStart);
    }

    // this will happen when we have no arguments
    if (earliestTokenStart == -1)
      return mExtent.getTokenStart();

    return earliestTokenStart;
  }

  /**
   * Returns the largest start of all argument heads (or the beginning of the
   * mention's extent if there are no arguments)
   */
  public int getMaxTokenEnd() {
    Collection<AceEventMentionArgument> args = getArgs();
    int latestTokenStart = -1;
    for (AceEventMentionArgument arg : args) {
      int tokenStart = arg.getContent().getHead().getTokenStart();
      if (latestTokenStart == -1)
        latestTokenStart = tokenStart;
      else
        latestTokenStart = Math.max(latestTokenStart, tokenStart);
    }

    // this will happen when we have no arguments
    if (latestTokenStart == -1)
      return mExtent.getTokenStart();

    return latestTokenStart;
  }

  // TODO: toXml method
}
