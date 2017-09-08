package old.edu.stanford.nlp.objectbank;

import old.edu.stanford.nlp.util.Function;
import old.edu.stanford.nlp.process.Tokenizer;
import old.edu.stanford.nlp.util.AbstractIterator;
import old.edu.stanford.nlp.util.XMLUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * A class which iterates over Strings occuring between the begin and end of
 * a selected tag or tags. The element is specified by a regexp, matched
 * against the name of the element (i.e., excluding the angle bracket
 * characters) using <code>matches()</code>).
 * The class ignores all other characters in the input Reader.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 */
public class XMLBeginEndIterator<E> extends AbstractIterator<E> implements Tokenizer<E> {

  private final Pattern tagNamePattern;
  private final BufferedReader in;
  private final Function<String,E> op;
  private final boolean keepInternalTags;
  private final boolean keepDelimitingTags;
  private E nextToken; // stores the read-ahead next token to return

  @SuppressWarnings({"unchecked"}) // Can't seem to do IdentityFunction without warning!
  public XMLBeginEndIterator(Reader in, String tagNameRegexp) {
    this(in, tagNameRegexp, new IdentityFunction(), false);
  }

  @SuppressWarnings({"unchecked"})
  public XMLBeginEndIterator(Reader in, String tagNameRegexp, boolean keepInternalTags) {
    this(in, tagNameRegexp, new IdentityFunction(), keepInternalTags);
  }

  public XMLBeginEndIterator(Reader in, String tagNameRegexp, Function<String,E> op, boolean keepInternalTags) {
    this(in, tagNameRegexp, op, keepInternalTags, false);
  }

  @SuppressWarnings({"unchecked"})
  public XMLBeginEndIterator(Reader in, String tagNameRegexp, boolean keepInternalTags, boolean keepDelimitingTags) {
    this(in, tagNameRegexp, new IdentityFunction(), keepInternalTags, keepDelimitingTags);
  }

  public XMLBeginEndIterator(Reader in, String tagNameRegexp, Function<String,E> op, boolean keepInternalTags, boolean keepDelimitingTags) {
    this.tagNamePattern = Pattern.compile(tagNameRegexp);
    this.op = op;
    this.keepInternalTags = keepInternalTags;
    this.keepDelimitingTags = keepDelimitingTags;
    this.in = new BufferedReader(in);
    setNext();
  }

  private void setNext() {
    String s = getNext();
    nextToken = parseString(s);
  }

  // returns null if there is no next object
  private String getNext() {
    StringBuilder result = new StringBuilder();
    try {
      XMLUtils.XMLTag tag;
      do {
        // String text =
        XMLUtils.readUntilTag(in);
        // there may or may not be text before the next tag, but we discard it
        //        System.out.println("outside text: " + text );
        tag = XMLUtils.readAndParseTag(in);
        //        System.out.println("outside tag: " + tag);
        if (tag == null) {
          return null; // couldn't find any more tags, so no more elements
        }
      } while ( ! tagNamePattern.matcher(tag.name).matches() || tag.isEndTag);
      if (keepDelimitingTags) {
        result.append(tag.toString());
      }
      while (true) {
        String text = XMLUtils.readUntilTag(in);
        if (text != null) {
          // if the text isn't null, we append it
          //        System.out.println("inside text: " + text );
          result.append(text);
        }
        String tagString = XMLUtils.readTag(in);
        tag = XMLUtils.parseTag(tagString);
        if (tag == null) {
          return null; // unexpected end of this element, so no more elements
        }
        if (tagNamePattern.matcher(tag.name).matches() && tag.isEndTag) {
          if (keepDelimitingTags) {
            result.append(tagString);
          }
          // this is our end tag so we stop
          break;
        } else {
          // not our end tag, so we optionally append it and keep going
          if (keepInternalTags) {
            result.append(tagString);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result.toString();
  }

  protected E parseString(String s) {
    return op.apply(s);
  }

  @Override
  public boolean hasNext() {
    return nextToken != null;
  }

  @Override
  public E next() {
    if (nextToken == null) {
      throw new NoSuchElementException();
    }
    E token = nextToken;
    setNext();
    return token;
  }

  public E peek() {
    return nextToken;
  }

  /**
   * Returns pieces of text in element as a List of tokens.
   *
   * @return A list of all tokens remaining in the underlying Reader
   */
  public List<E> tokenize() {
    // System.out.println("tokenize called");
    List<E> result = new ArrayList<E>();
    while (hasNext()) {
      result.add(next());
    }
    return result;
  }


  /**
   * Returns a factory that vends BeginEndIterators that reads the contents of
   * the given Reader, extracts text between the specified Strings, then
   * returns the result.
   *
   * @param tag The tag the XMLBeginEndIterator will match on
   * @return The IteratorFromReaderFactory
   */
  public static IteratorFromReaderFactory<String> getFactory(String tag) {
    return new XMLBeginEndIteratorFactory<String>(tag, new IdentityFunction<String>(), false, false);
  }

  public static IteratorFromReaderFactory<String> getFactory(String tag, boolean keepInternalTags, boolean keepDelimitingTags) {
    return new XMLBeginEndIteratorFactory<String>(tag, new IdentityFunction<String>(), keepInternalTags, keepDelimitingTags);
  }

  public static <E> IteratorFromReaderFactory<E> getFactory(String tag, Function<String,E> op) {
    return new XMLBeginEndIteratorFactory<E>(tag, op, false, false);
  }

  public static <E> IteratorFromReaderFactory<E> getFactory(String tag, Function<String,E> op, boolean keepInternalTags, boolean keepDelimitingTags) {
    return new XMLBeginEndIteratorFactory<E>(tag, op, keepInternalTags, keepDelimitingTags);
  }

  static class XMLBeginEndIteratorFactory<E> implements IteratorFromReaderFactory<E> {

    private final String tag;
    private final Function<String,E> op;
    private final boolean keepInternalTags;
    private final boolean keepDelimitingTags;

    public XMLBeginEndIteratorFactory(String tag, Function<String,E> op, boolean keepInternalTags, boolean keepDelimitingTags) {
      this.tag = tag;
      this.op = op;
      this.keepInternalTags = keepInternalTags;
      this.keepDelimitingTags = keepDelimitingTags;
    }

    public Iterator<E> getIterator(Reader r) {
      return new XMLBeginEndIterator<E>(r, tag, op, keepInternalTags, keepDelimitingTags);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 3) {
      System.err.println("usage: XMLBeginEndIterator file element keepInternalBoolean");
      return;
    }
    Reader in = new FileReader(args[0]);
    Iterator<String> iter = new XMLBeginEndIterator<String>(in, args[1], args[2].equalsIgnoreCase("true"));
    while (iter.hasNext()) {
      String s = iter.next();
      System.out.println("*************************************************");
      System.out.println(s);
    }
    in.close();
  }

} // end class XMLBeginEndIterator

