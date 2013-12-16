package org.apache.jsp;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.*;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Properties;
import java.util.Locale;
import java.util.Date;

public final class index_jsp extends org.apache.jasper.runtime.HttpJspBase
    implements org.apache.jasper.runtime.JspSourceDependent {


static final int MAXWORDS = 70;
static final String DEFAULT_LANG = "English";
static final boolean DEBUG = false;
// WARNING: this is tomcat specific
static final String BASE_DIR = System.getProperty("catalina.base");
static final String BASE_LOG_FILENAME = "/logs/parser.sentences";
static final String LOG_FILENAME = BASE_DIR + BASE_LOG_FILENAME;

class ParserPack {
  CRFClassifier segmenter;
  LexicalizedParser parser;
  TreebankLanguagePack tLP;
  TreePrint tagPrint, pennPrint, typDepPrint, typDepColPrint; 
  Function<List<HasWord>, List<HasWord>> escaper;
}

ParserPack loadParserPack(String parser, ServletContext application) 
   throws Exception {
  String SerializedParserPath = 
     application.getRealPath("/WEB-INF/data") + File.separator +
     nameToParserSer.get(parser);

  // load parser
  ParserPack pp = new ParserPack();
  pp.escaper = (Function<List<HasWord>, List<HasWord>>) 
     Class.forName(nameToEscaper.get(parser)).newInstance();
  pp.parser = LexicalizedParser.loadModel(SerializedParserPath);
  pp.tLP = pp.parser.getOp().tlpParams.treebankLanguagePack();
  pp.tagPrint = new TreePrint("wordsAndTags", pp.tLP);
  pp.pennPrint = new TreePrint("penn", pp.tLP);
  if (!parser.equals("Arabic")) {
     pp.typDepPrint = new TreePrint("typedDependencies", "basicDependencies", pp.tLP);
     pp.typDepColPrint = new TreePrint("typedDependencies", pp.tLP);  // default is now CCprocessed
  }

  // if appropriate, load segmenter
  if (parser.equals("Chinese")) {
    Properties props = new Properties();
    String dataDir = application.
      getRealPath("/WEB-INF/data/chinesesegmenter");
    CRFClassifier classifier = new CRFClassifier(props);
    BufferedInputStream bis = new BufferedInputStream(new GZIPInputStream(
      new FileInputStream(dataDir + File.separator + "05202008-ctb6.processed-chris6.lex.gz")));
    classifier.loadClassifier(bis,null); bis.close();

    // configure segmenter
    SeqClassifierFlags flags = classifier.flags;
    flags.sighanCorporaDict = dataDir;
    flags.normalizationTable = dataDir + File.separator + "norm.simp.utf8";
    flags.normTableEncoding = "UTF-8";
    flags.inputEncoding = "UTF-8";
    flags.keepAllWhitespaces = true;
    flags.keepEnglishWhitespaces = true;
    flags.sighanPostProcessing = true;

    pp.segmenter = classifier;
  }
  List<String> defaultQueryPieces;
  if (pp.segmenter != null) {
    defaultQueryPieces = pp.segmenter.segmentString(defaultQuery.get(parser));
  } else {
    defaultQueryPieces = Arrays.asList(defaultQuery.get(parser).split("\\s+"));
  }
  List<HasWord> defaultQueryWords = new ArrayList<HasWord>();
  for (String s : defaultQueryPieces) {
    defaultQueryWords.add(new Word(s));
  }
  pp.parser.parseTree(defaultQueryWords);
  return pp; 
}

static Map<String, String> nameToParserSer = new HashMap<String, String>();
static Map<String, String> nameToEscaper = new HashMap<String, String>();
static Map<String, ParserPack> parsers = new HashMap<String, ParserPack>();
static Map<String, String> defaultQuery = new HashMap<String, String>();

static {
  nameToParserSer.put("English", "englishPCFG.ser.gz");
  nameToParserSer.put("Chinese", "xinhuaFactored.ser.gz");
  nameToParserSer.put("Arabic",  "arabicFactored.ser.gz");
  nameToEscaper.put("English", "edu.stanford.nlp.process.PTBEscapingProcessor");
  nameToEscaper.put("Chinese",
     "edu.stanford.nlp.trees.international.pennchinese.ChineseEscaper"); 
  nameToEscaper.put("Arabic", "edu.stanford.nlp.process.PTBEscapingProcessor");
  defaultQuery.put("English", "My dog also likes eating sausage.");
  defaultQuery.put("Chinese", "猴子喜欢吃香蕉。");
  defaultQuery.put("Arabic", "هذا الرجل هو سعيد.");
}

private String treeToString(Tree t, TreePrint tp) { 
  StringWriter sw = new StringWriter(); 
  tp.printTree(t, (new PrintWriter(sw))); 
  return sw.toString(); 
}


  private static final javax.servlet.jsp.JspFactory _jspxFactory =
          javax.servlet.jsp.JspFactory.getDefaultFactory();

  private static java.util.List<java.lang.String> _jspx_dependants;

  private javax.el.ExpressionFactory _el_expressionfactory;
  private org.apache.tomcat.InstanceManager _jsp_instancemanager;

  public java.util.List<java.lang.String> getDependants() {
    return _jspx_dependants;
  }

  public void _jspInit() {
    _el_expressionfactory = _jspxFactory.getJspApplicationContext(getServletConfig().getServletContext()).getExpressionFactory();
    _jsp_instancemanager = org.apache.jasper.runtime.InstanceManagerFactory.getInstanceManager(getServletConfig());
  }

  public void _jspDestroy() {
  }

  public void _jspService(final javax.servlet.http.HttpServletRequest request, final javax.servlet.http.HttpServletResponse response)
        throws java.io.IOException, javax.servlet.ServletException {

    final javax.servlet.jsp.PageContext pageContext;
    final javax.servlet.ServletContext application;
    final javax.servlet.ServletConfig config;
    javax.servlet.jsp.JspWriter out = null;
    final java.lang.Object page = this;
    javax.servlet.jsp.JspWriter _jspx_out = null;
    javax.servlet.jsp.PageContext _jspx_page_context = null;


    try {
      response.setContentType("text/html;charset=UTF-8");
      pageContext = _jspxFactory.getPageContext(this, request, response,
      			null, false, 8192, true);
      _jspx_page_context = pageContext;
      application = pageContext.getServletContext();
      config = pageContext.getServletConfig();
      out = pageContext.getOut();
      _jspx_out = out;

      out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n\n<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \n \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n\n");
 /*
   -------------------- WARNING -------------------------------- 
     Do not edit this file unless your editor knows how to 
                  properly handle UTF-8. 
   -------------------------------------------------------------
*/ 
      out.write("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
      out.write('\n');
      out.write('\n');


request.setCharacterEncoding("UTF-8");

String parserSelect = request.getParameter("parserSelect");
if (parserSelect == null) { parserSelect = DEFAULT_LANG; }

ParserPack pp = parsers.get(parserSelect);
if (pp == null) {
  synchronized(parsers) {
    pp = parsers.get(parserSelect);
    if (pp == null) {
      pp = loadParserPack(parserSelect, application); 
      parsers.put(parserSelect, pp);
    }
  }
}

      out.write("\n\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n  <head>\n    <title>Stanford Parser</title>\n    <style type=\"text/css\">\n       div.parserOutput { padding-left: 3em; \n                          padding-top: 1em; padding-bottom: 0px; \n                          margin: 0px; }\n       div.parserOutputMonospace { \n                          padding-top: 1em; padding-bottom: 1em; margin: 0px; \n                          font-family: monospace; padding-left: 3em; }\n       .spacingFree { padding: 0px; margin: 0px; }\n    </style>\n\n    <link href=\"http://nlp.stanford.edu/nlp.css\" rel=\"stylesheet\" \n          type=\"text/css\" />\n\n    <script type=\"text/javascript\">\n    <!--//--><![CDATA[\n    ");
 ArrayList<String> langs = 
         new ArrayList<String>(nameToParserSer.keySet());
       Collections.sort(langs);  
      out.write("\n    \n    function showSample() {\n      query = document.getElementById(\"query\");\n      parserSelect = document.getElementById(\"parserSelect\");\n      query.value = document.getElementById(\"defaultQuery.\"+\n                 parserSelect.selectedIndex).value;\n    }\n\n    function handleOnChangeParserSelect() {\n      query = document.getElementById(\"query\");\n      parserSelect = document.getElementById(\"parserSelect\");\n      for (var i = 0; i < ");
      out.print( langs.size() );
      out.write(" ; i++) {\n         defaultQuery = document.getElementById(\"defaultQuery.\"+ i);\n         if (query.value == defaultQuery.value) { showSample(); break; }\n      } \n      parseButton = document.getElementById(\"parseButton\");\n      chineseParseButton = document.getElementById(\"chineseParseButton\");\n      if (parserSelect.value == \"Chinese\") {\n         parseButton.value = chineseParseButton.value;\n      } else {\n         parseButton.value = \"Parse\";\n      }\n    }\n    <!--//-->]]>\n    </script>\n\n    <link rel=\"icon\" type=\"image/x-icon\" href=\"/parser/favicon.ico\" />\n    <link rel=\"shortcut icon\" type=\"image/x-icon\" \n       href=\"/parser/favicon.ico\" />\n\n  </head>\n\n  <body>\n    ");
 
       String query = request.getParameter("query");
       if (query == null) query = "";
       query = query.replaceAll("^\\s*", "").replaceAll("\\s*$", "");

       if (query.length() == 0) query = defaultQuery.get(parserSelect); 

       PrintWriter sentenceLog = new PrintWriter(new BufferedWriter(
                                   new FileWriter(LOG_FILENAME, true)));
       sentenceLog.printf("%s:%s: [%s] - ", new Date(), parserSelect, query);
    
      out.write("\n\n    <h1>Stanford Parser</h1>\n\n    <div style=\"margin-top: 2em;\">\n    <form action=\"index.jsp\" method=\"post\">\n    <div style=\"width: 410px;\">\n    ");
 for (int i = 0; i < langs.size(); i++) { 
      out.write("\n       <input type=\"hidden\" name=\"defaultQuery.");
      out.print( i );
      out.write("\" \n              id=\"defaultQuery.");
      out.print( i );
      out.write("\" \n              value=\"");
      out.print( defaultQuery.get(langs.get(i)).
              replaceAll("\\\\", "\\\\").replaceAll("\"", "\\\"") );
      out.write("\" />\n    ");
 } 
      out.write("\n        <input type=\"hidden\" name=\"chineseParseButton\"  id=\"chineseParseButton\"\n               value = \"&#21078;&#26512; (Parse)\" />\n        <div> \n        Please enter a sentence to be parsed: <br/>\n        <textarea name=\"query\" id=\"query\"\n         style=\"width: 400px; height: 8em\" \n         rows=\"31\" cols=\"7\">");
      out.print( query );
      out.write("</textarea> \n        </div>\n        <div style=\"float: left\">\n        Language:\n        <select name=\"parserSelect\" id=\"parserSelect\"\n         onchange=\"handleOnChangeParserSelect();\" >\n        ");
 for (String lang : langs) {
             String selected = (lang.equals(parserSelect) ? 
               "selected=\"selected\"" : ""); 
      out.write("\n           <option value=\"");
      out.print( lang );
      out.write('"');
      out.write(' ');
      out.print( selected );
      out.write('>');
      out.print( lang );
      out.write("</option>\n        ");
 } 
      out.write("\n        </select>\n        </div>\n\n        <div style=\"float: left; padding-left: 2em;\">\n        <a href=\"#sample\" onclick=\"showSample();\">Sample Sentence</a>\n        </div>\n\n        <div style=\"float: right\">\n        ");
 if (parserSelect.equals("Chinese")) { 
      out.write("\n           <input type=\"submit\" value=\"&#21078;&#26512; (Parse)\" \n                  name=\"parse\" id=\"parseButton\"/>\n        ");
 } else { 
      out.write("\n           <input type=\"submit\" value=\"Parse\" name=\"parse\" id=\"parseButton\"/>\n        ");
 } 
      out.write("\n        </div>\n      </div>\n    </form>\n    </div>\n\n    <div style=\"clear: left; margin-top: 3em\">\n    ");
 if (query != null && query.length() > 0) { 
         String[] queryWords = query.split("\\s+"); 
         application.log("Parser query from " + request.getRemoteAddr()
           + ": " + query); 
      out.write("\n\n      <h3>Your query</h3>\n      <div class=\"parserOutput\"><em>");
      out.print( query );
      out.write("</em></div>\n\n      ");
 {
        boolean parseSuccessful = true;
        List<String> words = null;
        String toParse = null;
        long time = -System.currentTimeMillis();
        long tokens = 0;
        List<List<HasWord>> sentences = new ArrayList<List<HasWord>>();
        List<Tree> trees = new ArrayList<Tree>();

        try {
          if (pp.segmenter != null) {
            words = pp.segmenter.segmentString(query);
            toParse= ""; for (String word : words) { toParse += word + " "; }
            if (DEBUG) {
              
      out.write("Segmented String");
 
              for (String word : words) { 
      out.write(' ');
      out.write('\'');
      out.print( word );
      out.write("'<br/> ");
 }
            }
          } else { 
            toParse = query; 
          } 
          toParse = toParse.replaceAll("\t", " ");
          StringReader reader = new StringReader(toParse);
          // TODO: different preprocessor for Chinese and maybe Arabic
          DocumentPreprocessor dp = new DocumentPreprocessor(new StringReader(toParse));
          dp.setTokenizerFactory(pp.tLP.getTokenizerFactory());
          for (List<HasWord> sentence : dp) {
            if (sentence.size() > MAXWORDS) {
              
      out.write("\n                <p> \n                  Sorry, can't parse sentences containing more than \n                  ");
      out.print( MAXWORDS );
      out.write(" words. <br/>\n                  The sentence <em>");
      out.print( Sentence.listToString(sentence) );
      out.write("</em> has \n                  ");
      out.print( queryWords.length );
      out.write(" words.\n                </p>\n              ");

              parseSuccessful = false;
              break;
            }
            sentences.add(sentence);
            tokens += sentence.size();
            Tree tree = pp.parser.parseTree(sentence);
            if (tree == null) {
            
      out.write("<!-- non-exception parse failure -->");

              parseSuccessful = false;
              break;
            }
            trees.add(tree);
          }
        } catch (Exception e) { 
          parseSuccessful = false;
          
      out.write("<!-- exception occured  -->");

          System.err.printf("------------------\n");
          System.err.printf("Parser Select: '%s'\n", parserSelect);
          System.err.printf("Query: '%s'\n",query);
          if (pp.segmenter != null) { 
             System.err.printf("using segmenter....\n");
             System.err.printf("toParse: '%s'\n",toParse);
          }
          e.printStackTrace();
          if (DEBUG) {
            
      out.print( e );
      out.write("<br/>");
 
            for (StackTraceElement st : e.getStackTrace()) {
              
      out.print( st );
      out.write("<br/>");
 
            }
          }
        }
        time += System.currentTimeMillis();

        if (parseSuccessful) { 
          sentenceLog.printf("SUCCESS\n"); 
          if (words != null) { 
      out.write("\n            <h3>Segmentation</h3>\n            <div class=\"parserOutputMonospace\">\n            ");
 for (String word: words) { 
      out.write("\n              <div style=\"padding-right: 1em; float: left; white-space: nowrap;\">\n              ");
      out.print( word );
      out.write("</div>  ");
 } 
      out.write("\n            </div><div style=\"clear: left\"></div> ");
 
          } 
          
      out.write("\n          <h3>Tagging</h3>\n          <div class=\"parserOutputMonospace\">\n          ");
 for (Tree parse : trees) {
               if (parse != trees.get(0)) {
                 
      out.write(" <br> ");

               }
               for (String token : 
                    treeToString(parse, pp.tagPrint).split("\\s")) { 
      out.write("\n                 <div style=\"padding-right: 1em; float: left; white-space: nowrap;\">\n                 ");
      out.print( token );
      out.write("</div>\n          ");
 
               } 
             }
          
      out.write("\n          </div>\n\n          <div style=\"clear: left\"> </div>\n          <h3>Parse</h3>\n          <div class=\"parserOutput\">\n          <pre id=\"parse\" class=\"spacingFree\">");
 
            for (Tree parse : trees) { 
              if (parse != trees.get(0)) {
                
      out.print( "\n\n" );

              }
              
      out.print(
              treeToString(parse, pp.pennPrint).replaceAll("\n$", "") 
              );
 
            }
          
      out.write("</pre>\n          </div>\n\n\t        ");
 if (!parserSelect.equals("Arabic")) { 
      out.write("\n\n          <h3>Typed dependencies</h3>\n          <div class=\"parserOutput\">\n          <pre class=\"spacingFree\">");

            for (Tree parse : trees) {
              if (parse != trees.get(0)) {
                
      out.print( "\n\n" );

              }
              
      out.print(
              treeToString(parse, pp.typDepPrint).replaceAll("\n$","") 
              );

            }
          
      out.write("</pre>\n          </div>\n\n          <h3>Typed dependencies, collapsed</h3>\n          <div class=\"parserOutput\">\n          <pre class=\"spacingFree\">");

            for (Tree parse : trees) {
              if (parse != trees.get(0)) {
                
      out.print( "\n\n" );

              }
              
      out.print(
              treeToString(parse, pp.typDepColPrint).replaceAll("\n$","")
              );

            }
          
      out.write("</pre>\n          </div>\n          ");
 } 
      out.write("\n\n          <h3>Statistics</h3>\n\n          <br />Tokens: ");
      out.print( tokens );
      out.write(" <br /> Time: ");
      out.print( String.format("%.3f s", time/1000.0) );
      out.write(" <br />\n\n        ");
 } else {
          sentenceLog.printf("FAILURE\n"); 
      out.write("\n          <p>Sorry, failed to parse query. Is the correct language selected?</p>\n        ");
 }
         } 
       } 
      out.write("\n  </div>\n\n  ");
 sentenceLog.close(); 
      out.write("\n\n  <p>\n    <em><a href=\"http://nlp.stanford.edu/software/lex-parser.shtml\">Back to parser home</a></em>\n    <br>\n    <em>Last updated 2012-07-10</em>\n  </p>\n\n  <p style=\"text-align: right\">\n    <a href=\"http://validator.w3.org/check?uri=referer\"><img\n        style=\"border: 0px\" \n        src=\"http://www.w3.org/Icons/valid-xhtml10\"\n        alt=\"Valid XHTML 1.0 Strict\" height=\"31\" width=\"88\" /></a>\n  </p>\n  </body>\n</html>\n");
    } catch (java.lang.Throwable t) {
      if (!(t instanceof javax.servlet.jsp.SkipPageException)){
        out = _jspx_out;
        if (out != null && out.getBufferSize() != 0)
          try { out.clearBuffer(); } catch (java.io.IOException e) {}
        if (_jspx_page_context != null) _jspx_page_context.handlePageException(t);
      }
    } finally {
      _jspxFactory.releasePageContext(_jspx_page_context);
    }
  }
}
