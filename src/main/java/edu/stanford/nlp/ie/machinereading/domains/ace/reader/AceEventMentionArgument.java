
package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

public class AceEventMentionArgument extends AceMentionArgument {
  public AceEventMentionArgument(String role,
				    AceEntityMention content) {
    super(role, content, "event");
  }
}
