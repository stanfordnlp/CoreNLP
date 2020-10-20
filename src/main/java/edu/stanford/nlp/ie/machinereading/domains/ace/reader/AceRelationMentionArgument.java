
package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

public class AceRelationMentionArgument extends AceMentionArgument {
  public AceRelationMentionArgument(String role,
				    AceEntityMention content) {
    super(role, content, "relation");
  }
}
