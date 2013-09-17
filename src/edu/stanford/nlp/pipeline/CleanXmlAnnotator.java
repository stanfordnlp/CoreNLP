package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.XMLUtils;


/**
 * An annotator which removes all xml tags (as identified by the
 * tokenizer) and possibly selectively keeps the text between them.
 * Can also add sentence ending markers depending on the xml tag.
 *
 * @author John Bauer
 */
public class CleanXmlAnnotator implements Annotator{
  /**
   * A regular expression telling us where to look for tokens
   * we care about
   */
  private final Pattern xmlTagMatcher;

  public static final String DEFAULT_XML_TAGS = ".*";

  /**
   * This regular expression tells us which tags end a sentence...
   * for example, &lt;p&gt; would be a great candidate
   */
  private final Pattern sentenceEndingTagMatcher;

  public static final String DEFAULT_SENTENCE_ENDERS = "";

  /**
   * This tells us which XML tags wrap document date
   */
  private final Pattern dateTagMatcher;

  public static final String DEFAULT_DATE_TAGS = "datetime|date";

  /**
   * This setting allows handling of flawed XML.  For example,
   * a lot of the news articles we parse go: <br>
   *  &lt;text&gt; <br>
   *  &lt;turn&gt; <br>
   *  &lt;turn&gt; <br>
   *  &lt;turn&gt; <br>
   *  &lt;/text&gt; <br>
   * ... eg, no closing &lt;/turn&gt; tags.
   */
  private final boolean allowFlawedXml;

  public static final boolean DEFAULT_ALLOW_FLAWS = true;

  public CleanXmlAnnotator() {
    this(DEFAULT_XML_TAGS, DEFAULT_SENTENCE_ENDERS, DEFAULT_DATE_TAGS, DEFAULT_ALLOW_FLAWS);
  }

  public CleanXmlAnnotator(String xmlTagsToRemove,
                           String sentenceEndingTags,
                           String dateTags,
                           boolean allowFlawedXml) {
    this.allowFlawedXml = allowFlawedXml;
    if (xmlTagsToRemove != null) {
      xmlTagMatcher = Pattern.compile(xmlTagsToRemove);
      if (sentenceEndingTags != null &&
          sentenceEndingTags.length() > 0) {
        sentenceEndingTagMatcher = Pattern.compile(sentenceEndingTags);
      } else {
        sentenceEndingTagMatcher = null;
      }
    } else {
      xmlTagMatcher = null;
      sentenceEndingTagMatcher = null;
    }

    if(dateTags != null){
      dateTagMatcher = Pattern.compile(dateTags, Pattern.CASE_INSENSITIVE);
    } else {
      dateTagMatcher = null;
    }
  }

  public void annotate(Annotation annotation) {
    if (annotation.has(CoreAnnotations.TokensAnnotation.class)) {
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      List<CoreLabel> dateTokens = new ArrayList<CoreLabel>();
      List<CoreLabel> newTokens = process(tokens, dateTokens);
      // We assume that if someone is using this annotator, they don't
      // want the old tokens any more and get rid of them
      annotation.set(CoreAnnotations.TokensAnnotation.class, newTokens);

      // if the doc date was found, save it. it is used by SUTime (inside the "ner" annotator)
      if(dateTokens.size() > 0){
        StringBuffer os = new StringBuffer();
        boolean first = true;
        for (CoreLabel t : dateTokens) {
          if (!first) os.append(" ");
          os.append(t.word());
          first = false;
        }
        //System.err.println("DOC DATE IS: " + os.toString());
        annotation.set(CoreAnnotations.DocDateAnnotation.class, os.toString());
      }
    }
  }

  public List<CoreLabel> process(List<CoreLabel> tokens) {
    return process(tokens, null);
  }

  public List<CoreLabel> process(List<CoreLabel> tokens, List<CoreLabel> dateTokens) {
    // As we are processing, this stack keeps track of which tags we
    // are currently inside
    Stack<String> enclosingTags = new Stack<String>();
    // here we keep track of the current enclosingTags
    // this lets multiple tokens reuse the same tag stack
    List<String> currentTagSet = null;
    // How many matching tags we've seen
    int matchDepth = 0;
    // stores the filtered tags as we go
    List<CoreLabel> newTokens = new ArrayList<CoreLabel>();

    // we use this to store the before & after annotations if the
    // tokens were tokenized for "invertible"
    StringBuilder removedText = new StringBuilder();
    // we keep track of this so we can look at the last tag after
    // we're outside the loop

    for (CoreLabel token : tokens) {
      String word = token.word().trim();
      XMLUtils.XMLTag tag = XMLUtils.parseTag(word);
      // If it's not a tag, we do manipulations such as unescaping
      if (tag == null) {
        // TODO: put this into the lexer instead of here
        token.setWord(XMLUtils.unescapeStringForXML(token.word()));
        // TODO: was there another annotation that also represents the word?
        if (matchDepth > 0 ||
            xmlTagMatcher == null ||
            xmlTagMatcher.matcher("").matches()) {
          newTokens.add(token);
        }
        // if we removed any text, and the tokens are "invertible" and
        // therefore keep track of their before/after text, append
        // what we removed to the appropriate tokens
        if (removedText.length() > 0) {
          boolean added = false;
          String before = token.get(CoreAnnotations.BeforeAnnotation.class);
          if (before != null) {
            token.set(CoreAnnotations.BeforeAnnotation.class, removedText + before);
            added = true;
          }
          if (added && newTokens.size() > 1) {
            CoreLabel previous = newTokens.get(newTokens.size() - 2);
            String after = previous.get(CoreAnnotations.AfterAnnotation.class);
            if (after != null)
              previous.set(CoreAnnotations.AfterAnnotation.class, after + removedText);
            else
              previous.set(CoreAnnotations.AfterAnnotation.class, removedText.toString());
          }
          removedText = new StringBuilder();
        }
        if (currentTagSet == null) {
          // We wrap the list in an unmodifiable list because we reuse
          // the same list object many times.  We don't want to
          // let someone modify one list and screw up all the others.
          currentTagSet =
            Collections.unmodifiableList(new ArrayList<String>(enclosingTags));
        }
        token.set(CoreAnnotations.XmlContextAnnotation.class, currentTagSet);

        // is this token part of the doc date sequence?
        if (dateTagMatcher != null &&
            currentTagSet.size() > 0 &&
            dateTagMatcher.matcher(currentTagSet.get(currentTagSet.size() - 1)).matches()) {
          dateTokens.add(token);
        }

        continue;
      }

      // At this point, we know we have a tag

      // we are removing a token and its associated text...
      // keep track of that
      String currentRemoval = token.get(CoreAnnotations.BeforeAnnotation.class);
      if (currentRemoval != null)
        removedText.append(currentRemoval);
      currentRemoval = token.get(CoreAnnotations.OriginalTextAnnotation.class);
      if (currentRemoval != null)
        removedText.append(currentRemoval);
      if (token == tokens.get(tokens.size() - 1)) {
        currentRemoval = token.get(CoreAnnotations.AfterAnnotation.class);
        if (currentRemoval != null)
          removedText.append(currentRemoval);
      }

      // If the tag matches the sentence ending tags, and we have some
      // existing words, mark that word as being somewhere we want
      // to end the sentence.
      if (sentenceEndingTagMatcher != null &&
          sentenceEndingTagMatcher.matcher(tag.name).matches() &&
          newTokens.size() > 0) {
        CoreLabel previous = newTokens.get(newTokens.size() - 1);
        previous.set(CoreAnnotations.ForcedSentenceEndAnnotation.class, true);
      }

      if (xmlTagMatcher == null)
        continue;

      if (tag.isSingleTag) {
        continue;
      }
      // at this point, we can't reuse the "currentTagSet" vector
      // any more, since the current tag set has changed
      currentTagSet = null;
      if (tag.isEndTag) {
        while (true) {
          if (enclosingTags.size() == 0) {
            throw new IllegalArgumentException("Got a close tag " + tag.name +
                                               "which does not match " +
                                               "any open tag");
          }
          String lastTag = enclosingTags.pop();
          if (xmlTagMatcher.matcher(lastTag).matches()){
            --matchDepth;
          }
          if (lastTag.equals(tag.name))
            break;
          if (!allowFlawedXml)
            throw new IllegalArgumentException("Mismatched tags... " +
                                               tag.name + " closed a " +
                                               lastTag + " tag.");
        }
        if (matchDepth < 0) {
          // this should be impossible, since we already assert that
          // the tags match up correctly
          throw new AssertionError("Programming error?  We think there " +
                                   "have been more close tags than open tags");
        }
      } else {
        // open tag, since all other cases are exhausted
        enclosingTags.push(tag.name);
        if (xmlTagMatcher.matcher(tag.name).matches())
          matchDepth++;
      }
    }

    if (enclosingTags.size() > 0 && !allowFlawedXml) {
      throw new IllegalArgumentException("Unclosed tags, starting with " +
                                         enclosingTags.pop());
    }

    // If we ended with a string of xml tokens, that text needs to be
    // appended to the "AfterAnnotation" of one of the tokens...
    // Note that we clear removedText when we see a real token, so
    // if removedText is not empty, that must be because we just
    // dropped an xml tag.  Therefore we ignore that old After
    // annotation, since that text was already absorbed in the Before
    // annotation of the xml tag we threw away
    if (newTokens.size() > 0 && removedText.length() > 0) {
      CoreLabel lastToken = newTokens.get(newTokens.size() - 1);
      // sometimes AfterAnnotation seems to be null even when we are
      // collecting before & after annotations, but OriginalTextAnnotation
      // is only non-null if we are invertible.  Hopefully.
      if (lastToken.get(CoreAnnotations.OriginalTextAnnotation.class) != null) {
        lastToken.set(CoreAnnotations.AfterAnnotation.class, removedText.toString());
      }
    }

    return newTokens;
  }


  @Override
  public Set<Requirement> requires() {
    return Collections.singleton(TOKENIZE_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(CLEAN_XML_REQUIREMENT);
  }
}
