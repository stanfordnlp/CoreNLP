
package edu.stanford.nlp.ie.machinereading.domains.ace.reader;


/**
 * Stores one ACE relation mention
 */
public class AceRelationMention extends AceMention {
  private String mLexicalCondition;

  /** The two argument mentions */
  private AceRelationMentionArgument [] mArguments;

  /** the parent event */
  private AceRelation mParent;

  public AceRelationMention(String id,
			    AceCharSeq extent,
			    String lc) {
    super(id, extent);
    mLexicalCondition = lc;
    mArguments = new AceRelationMentionArgument[2];
  }

  public AceRelationMentionArgument [] getArgs() { return mArguments; }
  public AceEntityMention getArg(int which) {
    return mArguments[which].getContent();
  }
  public void setArg(int which, 
		     AceEntityMention em, 
		     String role) {
    mArguments[which] = new AceRelationMentionArgument(role, em);
  }

  /** Retrieves the argument that appears *first* in the sentence */
  public AceEntityMention getFirstArg() {
    if(getArg(0).getHead().getTokenStart() <= 
       getArg(1).getHead().getTokenStart()){
      return getArg(0);
    }
    return getArg(1);
  }
  /** Retrieves the argument that appears *last* in the sentence */
  public AceEntityMention getLastArg() {
    if(getArg(0).getHead().getTokenStart() >
       getArg(1).getHead().getTokenStart()){
      return getArg(0);
    }
    return getArg(1);
  }

  public void setParent(AceRelation e) { mParent = e; }
  public AceRelation getParent() { return mParent; }

  public String getLexicalCondition() { return mLexicalCondition; }

  /** Fetches the id of the sentence that contains this mention */
  public int getSentence(AceDocument doc) {
    return doc.getToken(getArg(0).getHead().getTokenStart()).getSentence();
  }

  /** Returns the smallest start of the two args heads */
  public int getMinTokenStart() {
    int s1 = getArg(0).getHead().getTokenStart();
    int s2 = getArg(1).getHead().getTokenStart();
    return Math.min(s1, s2);
  }

  /** Returns the largest end of the two args heads */
  public int getMaxTokenEnd() {
    int s1 = getArg(0).getHead().getTokenEnd();
    int s2 = getArg(1).getHead().getTokenEnd();
    return Math.max(s1, s2);
  }

  public String toXml(int offset) {
    StringBuffer buffer = new StringBuffer();
    appendOffset(buffer, offset);
    buffer.append("<relation_mention ID=\"" + getId() + "\"");
    if(mLexicalCondition != null)
      buffer.append(" LEXICALCONDITION=\"" + mLexicalCondition + "\"");
    buffer.append(">\n");

    buffer.append(mExtent.toXml("extent", offset + 2));
    buffer.append("\n");

    AceRelationMentionArgument arg1 = getArgs()[0];
    AceRelationMentionArgument arg2 = getArgs()[1];
    if(arg1.getRole().equals("Arg-1")){ // left to right
      buffer.append(arg1.toXml(offset + 2) + "\n");
      buffer.append(arg2.toXml(offset + 2) + "\n");  
    } else { // right to left
      buffer.append(arg2.toXml(offset + 2) + "\n");
      buffer.append(arg1.toXml(offset + 2) + "\n");  
    }  

    appendOffset(buffer, offset);
    buffer.append("</relation_mention>");
    return buffer.toString();
  }
}
