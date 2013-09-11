package edu.stanford.nlp.annotation;

import com.quiotix.html.parser.*;

import java.io.*;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;


/**
 * <code>HtmlCleaner</code> removes various code elements
 * (<code>style</code>, <code>script</code>, <code>applet</code>, and so on)
 * from an HTML document.
 * <code>HtmlCleaner</code> is built on top of the HtmlParser package
 * written by
 * Quiotix, which compresses vertical white space and outputs an html
 * document with consistent syntax (all
 * html is lower case, even spacing in tags, no quotes for attributes
 * except for file names and string literals).  HtmlCleaner adds filters
 * for comments, meta and area tags, and for script, style, server, and
 * applet tags along with the text that is contained between those tags, which
 * generally do not add to the "semantics" of a web page.  In addition, the
 * user may specify any additional tags to filter out.
 * This is
 * usually a necessary first step for processing html documents before
 * passing them into <code>TaggedStreamTokenizer</code>, as the TST makes
 * no attempt to fix spacing, etc, (though it can filter tags and comments).
 * <p/>
 * HtmlCleaner can be run in batch mode using default settings, with a shell
 * script similar to the
 * following; note that only single-word html file names can work in this
 * script -- I am not a script expert, and I never bothered finding a way
 * around this.
 * <p><code><pre>
 * #!/bin/ksh
 * #
 * <p/>
 * DIR=docs
 * OUTDIR=docs/cleaned
 * <p/>
 * # ------------
 * for FILE in $DIR/*.htm*
 * <p/>
 * do
 * echo $FILE
 * FILEROOT=${FILE%.*}
 * OUTFILE="$FILEROOT-c.html"
 * java HtmlCleaner $FILE > $OUTFILE
 * done
 * <p/>
 * mv $DIR/*-c.html $OUTDIR
 * <p/>
 * </pre></code>
 * <p/>
 * Some of the code has been customized for Annotator.  For example, unnecessary
 * quotes are no longer stripped from attributes, since they are required by the
 * Annotator.  Also, ignoreAttributes does not hold for "tag" tags.  The
 * attributes for those tags are always displayed, again since they are required by
 * the Annotator.
 *
 * @author Miler Lee, Stanford University
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 * @version 1.0 8/30/01
 */

public class HtmlCleaner {

  private Hashtable<String, Integer> ignores = new Hashtable<String, Integer>();
  private Hashtable<String, Integer> nospanIgnores = new Hashtable<String, Integer>();

  private boolean newLinePrinted = false;
  private final Integer inScope = Integer.valueOf(1);
  private final Integer notInScope = Integer.valueOf(0);

  private boolean ignoreComments = true;
  private boolean ignoreAttributes = false;
  private InputStream instream = null;
  private Reader reader = null;
  private PrintWriter out;

  // sets of tags that we wish to keep and output
  private Set<String> acceptableTags;
  private static final String[] defaultAcceptableTags = new String[]{"blockquote", "br", "center", "cite", "code", "em", "dd", "dl", "div", "dt", "font", "h1", "h2", "h3", "h4", "h5", "h6", "i", "li", "ol", "p", "strong", "table", "tbody", "td", "th", "thead", "title", "tr", "tt", "ul", "u", "tag"};

  /**
   * Creates a new <code>HtmlCleaner</code> with default settings.
   *
   * @param in the input stream of the html to be cleaned
   * @param os the output stream that the cleaned html will be written to
   */
  public HtmlCleaner(InputStream in, OutputStream os) {
    instream = in;
    out = new PrintWriter(os);
    setDefaultIgnores(true);
  }


  /**
   * Creates a new <code>HtmlCleaner</code> with default settings.
   *
   * @param r  The reader for the html to be cleaned
   * @param os The output stream that the cleaned html will be written to
   */
  public HtmlCleaner(Reader r, OutputStream os) {
    reader = r;
    out = new PrintWriter(os);
    setDefaultIgnores(true);
  }

  /**
   * Creates a new <code>HtmlCleaner</code> with default settings.
   *
   * @param r The reader for the html to be cleaned
   * @param output the output StringWriter for cleaned HTML.
   */
  public HtmlCleaner(Reader r, StringWriter output){
    reader=r;
    out = new PrintWriter(output);
    setDefaultIgnores(true);

  }

  /**
   * Adds the given tag to the acceptable tag set.  If the acceptable set is
   * null, creates a HashSet to hold the acceptable tags.
   * @param tag Tag to add
   */
  public void addAcceptableTag(String tag) {
    if (acceptableTags == null) {
      acceptableTags = new HashSet<String>();
    }
    acceptableTags.add(tag);
  }

  /**
   * Removes the given tag from the aceptable tag set, if the set is not null
   */
  public void removeAcceptableTag(String tag) {
    if (acceptableTags == null) {
      return;
    }
    acceptableTags.remove(tag);
  }

  /**
   * Set the set of acceptable tags, null clears.  Only tags in the
   * acceptable tag set will be printed.  If the set is null, then this
   * condition is ignored.
   */
  public void setAcceptableTags(Set<String> acceptableTags) {
    this.acceptableTags = acceptableTags;
  }

  /**
   * Returns the default acceptable tags as a Set of Strings.
   */
  public static Set<String> getDefaultAcceptableTags() {
    HashSet<String> tags = new HashSet<String>();
    for (int i = 0; i < defaultAcceptableTags.length; i++) {
      tags.add(defaultAcceptableTags[i]);
    }
    return (tags);
  }

  /**
   * Sets whether tag attributes will be omitted from output or not.  If
   * <tt>ignore</tt> is true, attributes will be omitted.
   */
  public void setIgnoreAttributes(boolean ignore) {
    ignoreAttributes = ignore;
  }

  /**
   * Cleans the html file specified in the constructor and writes
   * the output to the outstream specifed in the constructor.
   */
  public void clean() throws IOException, com.quiotix.html.parser.ParseException {
    HtmlDocument document;

    if (reader != null) {
      document = new HtmlParser(reader).HtmlDocument();
    } else if (instream != null) {
      document = new HtmlParser(instream).HtmlDocument();
    } else {
      return;
    }


    document.accept(new HtmlCollector());
    //document.accept(new HtmlScrubber(HtmlScrubber.DEFAULT_OPTIONS

    //keep quotes for our special annotation tags
    document.accept(new HtmlScrubber(HtmlScrubber.ATTR_DOWNCASE | HtmlScrubber.TAGS_DOWNCASE | HtmlScrubber.TRIM_SPACES));

    document.accept(new CleanerHtmlVisitor());
  }


  /**
   * The default html elements to ignore are: comments; script, style,
   * server,
   * and applet tags and the text within those tags; meta and area
   * tags.
   *
   * @param val true indicates the default html is ignored
   */
  public void setDefaultIgnores(boolean val) {
    if (val) {
      ignores.put("style", notInScope);
      ignores.put("script", notInScope);
      ignores.put("server", notInScope);
      ignores.put("applet", notInScope);
      nospanIgnores.put("meta", notInScope);
      nospanIgnores.put("area", notInScope);
      ignoreComments = true;
    } else {
      ignores.remove("style");
      ignores.remove("script");
      ignores.remove("server");
      ignores.remove("applet");
      nospanIgnores.remove("meta");
      nospanIgnores.remove("area");
      ignoreComments = false;
    }
  }


  /**
   * Specifies whether comments should be ignored.  Passing true
   * into <code>setDefaultIgnores</code> overrides this setting.
   *
   * @param val true indicates comments are ignored
   */

  public void setIgnoreComments(boolean val) {
    ignoreComments = val;
  }


  /**
   * Specifies a type of html tag to ignore.  If one of the six
   * default ignored tags is passed in, and subsequently false
   * is passed into
   * <code>setDefaultIgnores</code>, that tag will no longer be
   * ignored.  Only html-style tags are supported.
   *
   * @param tagName the name of the tag to be ignored, ie "table"
   *                or "font" without brackets or attributes.
   * @param spans   true indicates the tag comes in start/end pairs,
   *                such as font or table.  Pass in false for tags such as br.
   */

  public void addIgnore(String tagName, boolean spans) {
    if (!spans) {
      nospanIgnores.put(tagName, notInScope);
    } else {
      ignores.put(tagName, notInScope);
    }
  }

  /**
   * Implements the writing out of the cleaned document via the
   * <code>HtmlVisitor</code> callback API.
   */
  private class CleanerHtmlVisitor extends HtmlVisitor {

    @Override
    public void finish() {
      out.flush();
    }


    @Override
    public void visit(HtmlDocument.Tag t) {
      String name = t.tagName;
      // System.err.println("visit tag |" + name + "|");
      if (ignores.get(name) != null) {
        ignores.put(name, inScope);
        return;
      } else if (nospanIgnores.get(name) != null) {
        return;
      }

      if (!scriptInScope() && (acceptableTags == null || acceptableTags.contains(name))) {
        newLinePrinted = false;
        // always output attributes for our special annotation tags
        if (ignoreAttributes && !name.equals("tag")) {
          out.print("<" + name + ">");
        } else {
          out.print(t);
        }
      }
    }


    @Override
    public void visit(HtmlDocument.EndTag t) {
      String name = t.tagName;
      // System.err.println("visit end tag |" + name + "|");
      if (ignores.get(name) == inScope) {
        ignores.put(name, notInScope);
        return;
      }

      if (!scriptInScope() && (acceptableTags == null || acceptableTags.contains(name))) {
        newLinePrinted = false;

        // always output attributes for our special annotation tags
        if (ignoreAttributes && !name.equals("tag")) {
          out.print("</" + name + ">");
        } else {
          out.print(t);
        }
      }
    }


    @Override
    public void visit(HtmlDocument.Comment c) {
      // System.err.println("visit comment |" + c + "|");
      if (!ignoreComments) {
        newLinePrinted = false;
        out.print(c);
      }
    }


    @Override
    public void visit(HtmlDocument.Text t) {
      // System.err.println("visit text |" + t + "|");
      if (!scriptInScope()) {
        newLinePrinted = false;
        // eliminate non-breaking spaces
        t.text = t.text.replaceAll("&nbsp;", " ");
        t.text = t.text.replaceAll("&lt;", "<");
        t.text = t.text.replaceAll("&gt;", ">");
        t.text = t.text.replaceAll("&amp;", "&");
        t.text = t.text.replaceAll("&quot", "\"");
        t.text = t.text.replaceAll("&apos", "'");
        out.print(t);
      }
    }


    @Override
    public void visit(HtmlDocument.Newline n) {
      if (!scriptInScope()) {
        if (!newLinePrinted) {
          out.println();
          newLinePrinted = true;
        }
      }
    }


    @Override
    public void visit(HtmlDocument.Annotation a) {
      out.print(a);
    }

  } // end of private class CleanerHtmlVisitor


  private boolean scriptInScope() {
    //return (ignores.containsValue(inScope));
    //Java 1.1 compliant
    return ignores.contains(inScope);
  }


  /**
   * Runs <code>HtmlCleaner</code> with default settings on a
   * specified file, printing the cleaned html to standard out.
   */

  public static void main(String[] args) {

    if (args.length < 1) {
      System.err.println("usage: java edu.stanford.nlp.annotation.HtmlCleaner filenameToBeCleaned");
      System.exit(0);
    }

    InputStream r = null;
    try {
      r = new BufferedInputStream(new FileInputStream(args[0]));
      HtmlCleaner c = new HtmlCleaner(r, System.out);
      c.clean();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (r != null) {
        try {
          r.close();
        } catch (Exception e) {
        }
      }
    }
  }

}
