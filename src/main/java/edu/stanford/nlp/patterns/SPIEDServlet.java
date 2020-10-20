package edu.stanford.nlp.patterns;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.patterns.TextAnnotationPatterns;
import edu.stanford.nlp.util.StringUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonParser;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;


/**
 * A simple web frontend to the SPIED System.
 *
 * Shamelessly copied from Gabor's Naturali
 * @author Sonal
 */
public class SPIEDServlet extends HttpServlet {

  Logger logger = Logger.getAnonymousLogger();

  String testPropertiesFile;
  Map<String, String> modelNametoDirName;
  /**
   * Set the properties to the paths they appear at on the servlet.
   * See build.xml for where these paths get copied.
   * @throws javax.servlet.ServletException Thrown by the implementation
   */
  public void init()  throws ServletException {
    testPropertiesFile = getServletContext().getRealPath("/WEB-INF/data/test.properties");
    modelNametoDirName = new HashMap<>();
    modelNametoDirName.put("food","food");

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
  private void run(PrintWriter out, String q, String seedWords, String model) throws Exception{
    // Clean the string a bit
    q = q.trim();
    if (q.length() == 0) {
      return;
    }
    char lastChar = q.charAt(q.length() - 1);
    if (lastChar != '.' && lastChar != '!' && lastChar != '?') {
      q = q + ".";
    }

    TextAnnotationPatterns annotate = new TextAnnotationPatterns();
    String quotedString = quote(q);

    String jsonObject = "{\"input\":"+quotedString+",\"seedWords\":{\"NAME\":[\""+ StringUtils.join(seedWords.split("[,;]"), "\",\"")+"\"]}}";

    boolean testmode = true;
    if(model.equalsIgnoreCase("new"))
      testmode = false;

    logger.info("Testmode is " + testmode);

    logger.info("model is " + model);


    String suggestions;
    // Collect results
    if(testmode){
      Properties testProps= new Properties();
      if(testPropertiesFile != null && new File(testPropertiesFile).exists()){
        try {
          String props = IOUtils.stringFromFile(testPropertiesFile);
          testProps.load(new StringReader(props));
        } catch (IOException e) {
          writeError(e, out, "Cannot read test properties file");
          return;
        }
      } else{
        writeError(new RuntimeException("test prop file not found"), out, "Test properties file not found");
        return;
      }

      String modelDir = getServletContext().getRealPath("/WEB-INF/data/"+modelNametoDirName.get(model));
      testProps.setProperty("patternsWordsDir", modelDir);
      logger.info("Reading saved model from " + modelDir);
      String seedWordsFiles="NAME,"+modelDir+"/NAME/seedwords.txt,"+modelDir+"/NAME/phrases.txt";
      String modelPropertiesFile = modelDir+"/model.properties";
      logger.info("Loading model properties from " + modelPropertiesFile);
      String stopWordsFile = modelDir+"/stopwords.txt";
      boolean writeOutputFile = false;
      annotate.setUpProperties(jsonObject, false, writeOutputFile, seedWordsFiles);
      suggestions = annotate.suggestPhrasesTest(testProps, modelPropertiesFile, stopWordsFile);
    }
    else{
      boolean writeOutputFile = false;
      annotate.setUpProperties(jsonObject, false, writeOutputFile, null);
      annotate.processText(writeOutputFile);
      suggestions = annotate.suggestPhrases();
    }

    out.print(suggestions);
  }

  /**
   * {@inheritDoc}
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    logger.info("GET SPIED query from " + request.getRemoteAddr());
    if (request.getCharacterEncoding() == null) {
      request.setCharacterEncoding("utf-8");
    }
    response.setContentType("text/json; charset=UTF-8");

    PrintWriter out = response.getWriter();
    try {
      String raw = request.getParameter("q");
      String seedwords = request.getParameter("seedwords");

      String model = request.getParameter("model");
      if (raw == null || "".equals(raw)) {
        out.print("{\"okay\":false,\"reason\":\"No data provided\"}");
      } else {
        run(out, raw, seedwords, model);
      }
    } catch (Exception t) {
      writeError(t, out, request.toString());
    }

    out.close();
  }

  void writeError(Throwable t, PrintWriter out, String input){
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    logger.info("input is " + input);
    logger.info(sw.toString());
    out.print("{\"okay\":false, \"reason\":\"Something bad happened. Contact the author.\"}");
  }

  /**
   * {@inheritDoc}
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    logger.info("Responding to the request for SPIED");
    getServletContext().log("Responding through SPIED through servlet context!!");
  doGet(request, response);
//    logger.info("POST SPIED query from " + request.getRemoteAddr());
//
//    //StringBuffer jb = new StringBuffer();
//    String line = "";
//    response.setContentType("text/json; charset=UTF-8");
//    PrintWriter out = response.getWriter();
//    try {
//      String raw = StringUtils.toAscii(request.getParameter("q"));
//      String seedwords = request.getParameter("seedwords");
////      BufferedReader reader = request.getReader();
////      while ((line = reader.readLine()) != null)
////        jb.append(line);
////      JsonReader jsonReader = Json.createReader(new StringReader(jb.toString()));
////      JsonObject obj = jsonReader.readObject();
////      String raw = obj.get("q").toString();
////      String seedwords = obj.get("seedwords").toString();
//      line = request.toString();
//      if (raw == null || "".equals(raw)) {
//        out.print("{\"okay\":false,\"reason\":\"No data provided\"}");
//      } else {
//        run(out, raw, seedwords);
//      }
//    } catch (Throwable t) {
//      writeError(t, out, line);
//    }
//
//    out.close();

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
