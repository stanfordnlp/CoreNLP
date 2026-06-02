package edu.stanford.nlp.parser.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.net.Socket;
import java.util.Set;

import edu.stanford.nlp.trees.Tree;

/**
 * The sister class to LexicalizedParserServer.  This class connects
 * to the given host and port.  It can then either return a Tree or a
 * string with the output of the Tree, depending on the method called.
 * getParse gets the string output, getTree returns a Tree.
 */
public class LexicalizedParserClient {
  final String host;
  final int port;
  
  public LexicalizedParserClient(String host, int port) 
    throws IOException
  {
    this.host = host;
    this.port = port; 
  }

  /**
   * Reads a text result from the given socket
   */
  private static String readResult(Socket socket) 
    throws IOException
  {
    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf-8"));
    StringBuilder result = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      if (result.length() > 0) {
        result.append("\n");
      }
      result.append(line);
    }
    return result.toString();
  }

  /**
   * Tokenize the text according to the parser's tokenizer, 
   * return it as whitespace tokenized text.
   */
  public String getTokenizedText(String query) 
    throws IOException
  {
    Socket socket = new Socket(host, port);

    Writer out = new OutputStreamWriter(socket.getOutputStream(), "utf-8");
    out.write("tokenize " + query + "\n");
    out.flush();

    String result = readResult(socket);
    socket.close();
    return result;
  }

  /**
   * Get the lemmas for the text according to the parser's lemmatizer
   * (only applies to English), return it as whitespace tokenized text.
   */
  public String getLemmas(String query) 
    throws IOException
  {
    Socket socket = new Socket(host, port);

    Writer out = new OutputStreamWriter(socket.getOutputStream(), "utf-8");
    out.write("lemma " + query + "\n");
    out.flush();

    String result = readResult(socket);
    socket.close();
    return result;
  }

  /**
   * Returns the String output of the dependencies.
   * <br>
   * TODO: use some form of Mode enum (such as the one in SemanticGraphFactory) 
   * instead of a String
   */
  public String getDependencies(String query, String mode) 
    throws IOException
  {
    Socket socket = new Socket(host, port);

    Writer out = new OutputStreamWriter(socket.getOutputStream(), "utf-8");
    out.write("dependencies:" + mode + " " + query + "\n");
    out.flush();

    String result = readResult(socket);
    socket.close();
    return result;
  }

  /**
   * Returns the String output of the parse of the given query.
   * <br>
   * The "parse" method in the server is mostly useful for clients
   * using a language other than Java who don't want to import or wrap
   * Tree in any way.  However, it is useful to provide getParse to
   * test that functionality in the server.
   */
  public String getParse(String query, boolean binarized)
    throws IOException
  {
    Socket socket = new Socket(host, port);

    Writer out = new OutputStreamWriter(socket.getOutputStream(), "utf-8");
    out.write("parse" + (binarized ? ":binarized " : " ") + query + "\n");
    out.flush();

    String result = readResult(socket);
    socket.close();
    return result;
  }

  private static final Set<String> ALLOWED = Set.of("edu.stanford.nlp.ling.CoreLabel",
                                                    "edu.stanford.nlp.trees.LabeledScoredTreeNode",
                                                    "edu.stanford.nlp.trees.Tree",
                                                    "edu.stanford.nlp.util.ArrayCoreMap",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$AfterAnnotation",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$BeforeAnnotation",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$CategoryAnnotation",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$CharacterOffsetBeginAnnotation",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$CharacterOffsetEndAnnotation",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$OriginalTextAnnotation",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$PartOfSpeechAnnotation",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$TextAnnotation",
                                                    "edu.stanford.nlp.ling.CoreAnnotations$ValueAnnotation");

  private static boolean isAllowed(Class<?> clazz) {
    if (clazz == null) {
        return true;
    }

    while (clazz.isArray()) {
        clazz = clazz.getComponentType();
    }

    String name = clazz.getName();
    if (name.startsWith("java.lang."))
      return true;
    if (ALLOWED.contains(name))
      return true;
    return false;
  }

  public static ObjectInputFilter allowlistedTreeFilter() {
    return info -> {
      Class<?> clazz = info.serialClass();

      if (clazz == null) {
        return ObjectInputFilter.Status.UNDECIDED;
      }
      if (isAllowed(clazz)) {
        return ObjectInputFilter.Status.ALLOWED;
      }
      System.err.println("Rejected deserialization class: " + clazz.getName());
      return ObjectInputFilter.Status.REJECTED;
    };
  }

  /**
   * Returs a Tree from the server connected to at host:port.
   *<br>
   * Filter logic limited to a small set of classes expected for a basic serialized tree.
   * If more classes are necessary, please file an issue on github.
   * Logic developed with the help of ChatGPT
   *<br>
   * Another option would be to switch to protobuf, but then we wouldn't be compatible
   * across versions, in case that is a desired property.
   */
  public Tree getTree(String query) 
    throws IOException
  {
    Socket socket = new Socket(host, port);

    Writer out = new OutputStreamWriter(socket.getOutputStream(), "utf-8");
    out.write("tree " + query + "\n");
    out.flush();

    ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
    ois.setObjectInputFilter(allowlistedTreeFilter());
    Object o;
    try {
      o = ois.readObject();
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    if (!(o instanceof Tree)) {
      throw new IllegalArgumentException("Expected a tree");
    }
    Tree tree = (Tree) o;

    socket.close();
    return tree;
  }

  /**
   * Tell the server to exit
   */
  public void sendQuit() 
    throws IOException
  {
    Socket socket = new Socket(host, port);
    
    Writer out = new OutputStreamWriter(socket.getOutputStream(), "utf-8");
    out.write("quit\n");
    out.flush();    

    socket.close();
  }

  public static void main(String[] args) 
    throws IOException
  {
    System.setOut(new PrintStream(System.out, true, "utf-8"));
    System.setErr(new PrintStream(System.err, true, "utf-8"));

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", 
                                  LexicalizedParserServer.DEFAULT_PORT);
    String query = "John Bauer works at Stanford.";
    System.out.println(query);
    Tree tree = client.getTree(query);
    System.out.println(tree);
    String results = client.getParse(query, false);
    System.out.println(results);
  }
}
