package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

public class AceMentionArgument {

  final protected String mRole;
  final protected AceEntityMention mContent;
  final private String mentionType; // in practice, event or relation

  public AceMentionArgument(String role,
      AceEntityMention content, String mentionType) {
    mRole = role;
    mContent = content;
    this.mentionType = mentionType;
  }

  public AceEntityMention getContent() { return mContent; }

  public String getRole() { return mRole; }

  public String toXml(int offset) {
    StringBuffer buffer = new StringBuffer();
    AceElement.appendOffset(buffer, offset);
    buffer.append("<" + mentionType + "_mention_argument REFID=\"" + mContent.getId() + 
  	  "\" ROLE=\"" + mRole + "\">\n");
  
    
    //buffer.append(getContent().toXml(offset + 2));
    AceCharSeq ext = getContent().getExtent();
    buffer.append(ext.toXml("extent", offset + 2));
    buffer.append("\n");
  
    AceElement.appendOffset(buffer, offset);
    buffer.append("</" + mentionType + "_mention_argument>");
    return buffer.toString();
  }

  public String toXmlShort(int offset) {
    StringBuffer buffer = new StringBuffer();
    AceElement.appendOffset(buffer, offset);
    buffer.append("<" + mentionType + "_argument REFID=\"" + 
  	  mContent.getParent().getId() + 
  	  "\" ROLE=\"" + mRole + "\"/>");
    return buffer.toString();
  }

}