package old.edu.stanford.nlp.web;

import old.edu.stanford.nlp.io.IOUtils;
import old.edu.stanford.nlp.util.StringUtils;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.*;
import java.net.URL;

/**
 * Parses an HTML document and returns the plain text (and title).
 * The main thing that HTMLParser is used for is the
 * <code>parse(String url)</code> method, which will return a String with the
 * contents of an HTML page, without the tags. After calling parse, you can get
 * the HTML title (contents of the TITLE tag) by calling title().
 * Subclasses may override the handleText(), handleComment(),
 * handleStartTag(), etc. methods so that <code>parse(String url)</code>
 * returns something other than the text of the web page.  (For example, one
 * may be interested in returning only part of the text, or only the links.)
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 */


public class HTMLParser extends HTMLEditorKit.ParserCallback {

  protected StringBuffer textBuffer;
  protected String title;
  protected boolean isTitle;
  protected boolean isBody;
  protected boolean isScript;

  public HTMLParser() {
    super();
    title = "";
    isTitle = false;
    isBody = false;
    isScript = false;
  }

  @Override
  public void handleText(char[] data, int pos) {
    if (isTitle) {
      title = new String(data);
    } else if (isBody && !isScript) {
      textBuffer.append(data).append(" ");
    }
  }


  /**
   * Sets a flag if the start tag is the "TITLE" element start tag.
   */

  @Override
  public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrSet, int pos) {
    if (tag == HTML.Tag.TITLE) {
      isTitle = true;
    } else if (tag == HTML.Tag.BODY) {
      isBody = true;
    } else if (tag == HTML.Tag.SCRIPT) {
      isScript = true;
    }
    
  }

  /**
   * Sets a flag if the end tag is the "TITLE" element end tag
   */

  @Override
  public void handleEndTag(HTML.Tag tag, int pos) {
    if (tag == HTML.Tag.TITLE) {
      isTitle = false;
    } else if (tag == HTML.Tag.BODY) {
      isBody = false;
    } else if (tag == HTML.Tag.SCRIPT) {
      isScript = false;
    }
  }

  public String parse(URL url) throws IOException {
    return (parse(IOUtils.slurpURL(url)));
  }

  public String parse(Reader r) throws IOException {
    return parse(IOUtils.slurpReader(r));
  }

  /**
   * The parse method that actually does the work.
   * Now it first gets rid of singleton tags before running.
   * @throws IOException
   */
  public String parse(String text) throws IOException {
    text = StringUtils.searchAndReplace(text, "/>", ">");
    StringReader r = new StringReader(text);
    textBuffer = new StringBuffer(200);
    new ParserDelegator().parse(r, this, true);
    return textBuffer.toString();
  }

  public String title() {
    return title;
  }


  public static void main(String[] args) throws IOException {
    HTMLParser parser = new HTMLParser();
    String result = parser.parse(new URL(args[0]));
    System.out.println(result);
  }

}
