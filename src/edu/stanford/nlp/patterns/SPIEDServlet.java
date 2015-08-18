package edu.stanford.nlp.patterns;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.patterns.TextAnnotationPatterns;
import edu.stanford.nlp.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.logging.Logger;


/**
 * A simple web frontend to the SPIED System.
 *
 * Shamelessly copied from Gabor's Naturali
 * @author Sonal
 */
public class SPIEDServlet extends HttpServlet {


  /**
   * Set the properties to the paths they appear at on the servlet.
   * See build.xml for where these paths get copied.
   * @throws javax.servlet.ServletException Thrown by the implementation
   */
  public void init()  throws ServletException {
  }

  /**
   * Originally extracted from Jettison; copied from http://stackoverflow.com/questions/3020094/how-should-i-escape-strings-in-json
   * @param string The string to quote.
   * @return A quoted version of the string, safe to send over the wire.
   */
  public static String quote(String string) {
    if (string == null || string.length() == 0) {
      return "\"\"";
    }

    char         c = 0;
    int          i;
    int          len = string.length();
    StringBuilder sb = new StringBuilder(len + 4);
    String       t;

    sb.append('"');
    for (i = 0; i < len; i += 1) {
      c = string.charAt(i);
      switch (c) {
        case '\\':
        case '"':
          sb.append('\\');
          sb.append(c);
          break;
        case '/':
          //                if (b == '<') {
          sb.append('\\');
          //                }
          sb.append(c);
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\r':
          sb.append("\\r");
          break;
        default:
          if (c < ' ') {
            t = "000" + Integer.toHexString(c);
            sb.append("\\u" + t.substring(t.length() - 4));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
    return sb.toString();
  }

  /**
   * Actually perform the GET request, given all the relevant information (already sanity checked).
   * This is the meat of the servlet code.
   * @param out The writer to write the output to.
   * @param q The query string.
   */
  private void doGet(PrintWriter out, String q, String seedWords) {




//    // Clean the string a bit
//    q = q.trim();
//    if (q.length() == 0) {
//      return;
//    }
//    char lastChar = q.charAt(q.length() - 1);
//    if (lastChar != '.' && lastChar != '!' && lastChar != '?') {
//      q = q + ".";
//    }

    TextAnnotationPatterns annotate = new TextAnnotationPatterns();
    // Annotate
    try {
      String jsonObject = "{\"input\":\""+q+"\",\"seedWords\":{\"name\":[\""+ StringUtils.join(seedWords.split("[,;]"), "\",")+"\"]}}";
      annotate.processText(jsonObject, false, false);
      String suggestions = annotate.suggestPhrases();
      // Collect results
      out.print(suggestions);
    } catch (Throwable t) {
      Logger.getAnonymousLogger().info(t.toString());
      out.print("{\"okay\":\"false\"}");
    }
  }

  /**
   * {@inheritDoc}
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Logger.getAnonymousLogger().info("SPIED query from " + request.getRemoteAddr());
    if (request.getCharacterEncoding() == null) {
      request.setCharacterEncoding("utf-8");
    }
    response.setContentType("text/json; charset=UTF-8");
    PrintWriter out = response.getWriter();

    String raw = request.getParameter("q");
    String seedwords = request.getParameter("seedwords");
    if (raw == null || "".equals(raw)) {
      out.print("{\"okay\":\"false\"}");
    } else {
      doGet(out, raw, seedwords);
    }

    out.close();
  }

  /**
   * {@inheritDoc}
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doGet(request, response);
  }

  /**
   * A helper so that we can see how the servlet sees the world, modulo model paths, at least.
   */
  public static void main(String[] args) throws ServletException, IOException {
    SPIEDServlet servlet = new SPIEDServlet();
    servlet.init();
//    IOUtils.console(line -> {
//      StringWriter str = new StringWriter();
//      PrintWriter out = new PrintWriter(str);
//      servlet.doGet(new PrintWriter(out), line,"obama");
//      out.close();
//      System.out.println(str.toString());
//    });
  }
}
