package edu.stanford.nlp.pipeline;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.dcoref.Dictionaries;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.*;

/**
 * Serializes Annotation objects using our own format.
 *
 * @author Mihai
 */
public class CustomAnnotationSerializer implements AnnotationSerializer {

  private final boolean compress;

  /**
   * If true, it means we store/load also AntecedentAnnotation
   * This annotation is used ONLY in our KBP annotation.
   * By default, it is not needed because we store the entire coref graph anyway.
   */
  private final boolean haveExplicitAntecedent;

  public CustomAnnotationSerializer() {
    this(true, false);
  }

  public CustomAnnotationSerializer(boolean compress, boolean haveAnte) {
    this.compress = compress;
    this.haveExplicitAntecedent = haveAnte;
  }

  /** This method does its own buffering of the passed in InputStream. */
  public Annotation load(InputStream is) throws IOException, ClassNotFoundException, ClassCastException {
    is = new BufferedInputStream(is);
    if(compress) is = new GZIPInputStream(is);
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    Annotation doc = new Annotation("");
    String line;

    // read the coref graph (new format)
    Map<Integer, CorefChain> chains = loadCorefChains(reader);
    if(chains != null) doc.set(CorefCoreAnnotations.CorefChainAnnotation.class, chains);

    // read the coref graph (old format)
    line = reader.readLine().trim();
    if(line.length() > 0){
      String [] bits = line.split(" ");
      if(bits.length % 4 != 0){
        throw new RuntimeIOException("ERROR: Incorrect format for the serialized coref graph: " + line);
      }
      List<Pair<IntTuple, IntTuple>> corefGraph = new ArrayList<Pair<IntTuple,IntTuple>>();
      for(int i = 0; i < bits.length; i += 4){
        IntTuple src = new IntTuple(2);
        IntTuple dst = new IntTuple(2);
        src.set(0, Integer.parseInt(bits[i]));
        src.set(1, Integer.parseInt(bits[i + 1]));
        dst.set(0, Integer.parseInt(bits[i + 2]));
        dst.set(1, Integer.parseInt(bits[i + 3]));
        corefGraph.add(new Pair<IntTuple, IntTuple>(src, dst));
      }
      doc.set(CorefCoreAnnotations.CorefGraphAnnotation.class, corefGraph);
    }

    // read individual sentences
    List<CoreMap> sentences = new ArrayList<CoreMap>();
    while((line = reader.readLine()) != null){
      CoreMap sentence = new Annotation("");

      // first line is the parse tree. construct it with CoreLabels in Tree nodes
      Tree tree = new PennTreeReader(new StringReader(line), new LabeledScoredTreeFactory(CoreLabel.factory())).readTree();
      sentence.set(TreeCoreAnnotations.TreeAnnotation.class, tree);

      // read the dependency graphs
      IntermediateSemanticGraph intermCollapsedDeps = loadDependencyGraph(reader);
      IntermediateSemanticGraph intermUncollapsedDeps = loadDependencyGraph(reader);
      IntermediateSemanticGraph intermCcDeps = loadDependencyGraph(reader);

      // the remaining lines until empty line are tokens
      List<CoreLabel> tokens = new ArrayList<CoreLabel>();
      while((line = reader.readLine()) != null){
        if(line.length() == 0) break;
        CoreLabel token = loadToken(line, haveExplicitAntecedent);
        tokens.add(token);
      }
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);

      // convert the intermediate graph to an actual SemanticGraph
      SemanticGraph collapsedDeps = convertIntermediateGraph(intermCollapsedDeps, tokens);
      sentence.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, collapsedDeps);
      SemanticGraph uncollapsedDeps = convertIntermediateGraph(intermUncollapsedDeps, tokens);
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, uncollapsedDeps);
      SemanticGraph ccDeps = convertIntermediateGraph(intermCcDeps, tokens);
      sentence.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, ccDeps);

      sentences.add(sentence);
    }
    doc.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    reader.close();
    return doc;
  }

  private static final Object LOCK = new Object();

  static SemanticGraph convertIntermediateGraph(IntermediateSemanticGraph ig, List<CoreLabel> sentence) {
    SemanticGraph graph = new SemanticGraph();

    // first construct the actual nodes; keep them indexed by their index
    Map<Integer, IndexedWord> nodes = new HashMap<Integer, IndexedWord>();
    for(IntermediateNode in: ig.nodes){
      CoreLabel token = sentence.get(in.index - 1); // index starts at 1!
      IndexedWord word = new IndexedWord(in.docId, in.sentIndex, in.index, token);
      word.set(CoreAnnotations.ValueAnnotation.class, word.get(CoreAnnotations.TextAnnotation.class));
      if(in.copyAnnotation >= 0){
        word.set(CoreAnnotations.CopyAnnotation.class, in.copyAnnotation);
      }
      nodes.put(word.index(), word);
    }
    for(IndexedWord node: nodes.values()){
      graph.addVertex(node);
    }

    // add all edges to the actual graph
    for(IntermediateEdge ie: ig.edges){
      IndexedWord source = nodes.get(ie.source);
      assert(source != null);
      IndexedWord target = nodes.get(ie.target);
      assert(target != null);
      synchronized (LOCK) {
        // this is not thread-safe: there are static fields in GrammaticalRelation
        GrammaticalRelation rel = GrammaticalRelation.valueOf(ie.dep);
        graph.addEdge(source, target, rel, 1.0, ie.isExtra);
      }
    }

    // compute root nodes if non-empty
    if( ! graph.isEmpty()){
      graph.resetRoots();
    }

    return graph;
  }

  /**
   * This stores the loaded SemanticGraph *before* we could convert the nodes to IndexedWords
   * This conversion take places later, after we load all sentence tokens
   */
  private static class IntermediateSemanticGraph {
    List<IntermediateNode> nodes;
    List<IntermediateEdge> edges;
    IntermediateSemanticGraph() {
      nodes = new ArrayList<IntermediateNode>();
      edges = new ArrayList<IntermediateEdge>();
    }
  }

  private static class IntermediateNode {
    String docId;
    int sentIndex;
    int index;
    int copyAnnotation;
    IntermediateNode(String docId, int sentIndex, int index, int copy) {
      this.docId = docId;
      this.sentIndex = sentIndex;
      this.index = index;
      this.copyAnnotation = copy;
    }
  }

  private static class IntermediateEdge {
    int source;
    int target;
    String dep;
    boolean isExtra;
    IntermediateEdge(String dep, int source, int target, boolean isExtra) {
      this.dep = dep;
      this.source = source;
      this.target = target;
      this.isExtra = isExtra;
    }
  }

  private static IntermediateSemanticGraph loadDependencyGraph(BufferedReader reader) throws IOException {
    IntermediateSemanticGraph graph = new IntermediateSemanticGraph();

    // first line: list of nodes
    String line = reader.readLine().trim();
    // System.out.println("PARSING LINE: " + line);
    if(line.length() > 0){
      String [] bits = line.split("\t");
      if(bits.length < 3) throw new RuntimeException("ERROR: Invalid dependency node line: " + line);
      String docId = bits[0];
      if(docId.equals("-")) docId = "";
      int sentIndex = Integer.valueOf(bits[1]);
      for(int i = 2; i < bits.length; i ++){
        String bit = bits[i];
        String [] bbits = bit.split("-");
        int copyAnnotation = -1;
        if(bbits.length > 2){
          throw new RuntimeException("ERROR: Invalid format for dependency graph: " + line);
        } else if(bbits.length == 2){
          copyAnnotation = Integer.valueOf(bbits[1]);
        }
        int index = Integer.valueOf(bbits[0]);
        graph.nodes.add(new IntermediateNode(docId, sentIndex, index, copyAnnotation));
      }
    }

    // second line: list of deps
    line = reader.readLine().trim();
    if(line.length() > 0){
      String [] bits = line.split("\t");
      for(String bit: bits){
        String [] bbits = bit.split(" ");
        if(bbits.length < 3 || bbits.length > 4){
          throw new RuntimeException("ERROR: Invalid format for dependency graph: " + line);
        }
        String dep = bbits[0];
        int source = Integer.valueOf(bbits[1]);
        int target = Integer.valueOf(bbits[2]);
        boolean isExtra = (bbits.length == 4) ? Boolean.valueOf(bbits[3]) : false;
        graph.edges.add(new IntermediateEdge(dep, source, target, isExtra));
      }
    }

    return graph;
  }

  /**
   * Saves all arcs in the graph on two lines: first line contains the vertices, second the edges
   * @param graph
   * @param pw
   */
  private static void saveDependencyGraph(SemanticGraph graph, PrintWriter pw) {
    if(graph == null){
      pw.println();
      pw.println();
      return;
    }
    boolean outputHeader = false;
    for (IndexedWord node: graph.vertexSet()){
      // first line: sentence index for all nodes; we recover the words
      // from the original tokens the first two tokens in this line
      // indicate: docid, sentence index
      if (!outputHeader) {
        String docId = node.get(CoreAnnotations.DocIDAnnotation.class);
        if(docId != null && docId.length() > 0) pw.print(docId);
        else pw.print("-");
        pw.print("\t");
        pw.print(node.get(CoreAnnotations.SentenceIndexAnnotation.class));
        outputHeader = true;
      }

      pw.print("\t");
      pw.print(node.index());
      // CopyAnnotations indicate copied (or virtual nodes) generated due to CCs (see EnglishGrammaticalStructure)
      // These annotations are usually not set, so print them only if necessary
      if(node.containsKey(CoreAnnotations.CopyAnnotation.class)){
        pw.print("-");
        pw.print(node.get(CoreAnnotations.CopyAnnotation.class));
        // System.out.println("FOUND COPY ANNOTATION: " + node.get(CoreAnnotations.CopyAnnotation.class));
      }
    }
    pw.println();

    // second line: all edges
    boolean first = true;
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      if(! first) pw.print("\t");
      String rel = edge.getRelation().toString();
      // no spaces allowed in the relation name
      // note that they might occur due to the tokenization of HTML/XML/RDF tags
      rel = rel.replaceAll("\\s+", "");
      pw.print(rel);
      pw.print(" ");
      pw.print(edge.getSource().index());
      pw.print(" ");
      pw.print(edge.getTarget().index());
      if (edge.isExtra()) {
        pw.print(" ");
        pw.print(edge.isExtra());
      }
      first = false;
    }
    pw.println();
  }

  /** Serializes the CorefChain objects
   *
   * @param chains all clusters in a doc
   * @param pw the buffer
   */
  private static void saveCorefChains(Map<Integer, CorefChain> chains, PrintWriter pw) {
    if(chains == null) {
      pw.println();
      return;
    }

    // how many clusters
    pw.println(chains.size());

    // save each cluster
    for(Integer cid: chains.keySet()) {
      // cluster id + how many mentions in the cluster
      CorefChain cluster = chains.get(cid);
      saveCorefChain(pw, cid, cluster);
    }

    // an empty line at end
    pw.println();
  }

  private static int countMentions(CorefChain cluster) {
    int count = 0;
    for(IntPair mid: cluster.getMentionMap().keySet()) {
      count += cluster.getMentionMap().get(mid).size();
    }
    return count;
  }

  /**
   * Serializes one coref cluster (i.e., one entity)
   * @param pw the buffer
   * @param cid id of cluster to save
   * @param cluster the cluster
   */
  public static void saveCorefChain(PrintWriter pw, int cid, CorefChain cluster) {
    pw.println(cid + " " + countMentions(cluster));
    // each mention saved on one line
    Map<IntPair, Set<CorefChain.CorefMention>> mentionMap = cluster.getMentionMap();
    for(IntPair mid: mentionMap.keySet()) {
      // all mentions with the same head
      Set<CorefChain.CorefMention> mentions = mentionMap.get(mid);
      for(CorefChain.CorefMention mention: mentions) {
        // one mention per line
        pw.print(mid.getSource() + " " + mid.getTarget());
        if(mention == cluster.getRepresentativeMention()) pw.print(" " + 1);
        else pw.print(" " + 0);

        pw.print(" " + mention.mentionType);
        pw.print(" " + mention.number);
        pw.print(" " + mention.gender);
        pw.print(" " + mention.animacy);
        pw.print(" " + mention.startIndex);
        pw.print(" " + mention.endIndex);
        pw.print(" " + mention.headIndex);
        pw.print(" " + mention.corefClusterID);
        pw.print(" " + mention.mentionID);
        pw.print(" " + mention.sentNum);
        pw.print(" " + mention.position.length());
        for(int i = 0; i < mention.position.length(); i ++)
          pw.print(" " + mention.position.get(i));
        pw.print(" " + escapeSpace(mention.mentionSpan));
        pw.println();
      }
    }
  }

  private static String escapeSpace(String s) {
    return s.replaceAll("\\s", SPACE_HOLDER);
  }
  private static String unescapeSpace(String s) {
    return s.replaceAll(SPACE_HOLDER, " ");
  }
  private static Dictionaries.MentionType parseMentionType(String s) {
    if(s.equals("PRONOMINAL")) return Dictionaries.MentionType.PRONOMINAL;
    if(s.equals("NOMINAL")) return Dictionaries.MentionType.NOMINAL;
    if(s.equals("PROPER")) return Dictionaries.MentionType.PROPER;
    throw new RuntimeException("Unknown value: " + s);
  }
  private static Dictionaries.Number parseNumber(String s) {
    if(s.equals("SINGULAR")) return Dictionaries.Number.SINGULAR;
    if(s.equals("PLURAL")) return Dictionaries.Number.PLURAL;
    if(s.equals("UNKNOWN")) return Dictionaries.Number.UNKNOWN;
    throw new RuntimeException("Unknown value: " + s);
  }
  private static Dictionaries.Gender parseGender(String s) {
    if(s.equals("MALE")) return Dictionaries.Gender.MALE;
    if(s.equals("FEMALE")) return Dictionaries.Gender.FEMALE;
    if(s.equals("NEUTRAL")) return Dictionaries.Gender.NEUTRAL;
    if(s.equals("UNKNOWN")) return Dictionaries.Gender.UNKNOWN;
    throw new RuntimeException("Unknown value: " + s);
  }
  private static Dictionaries.Animacy parseAnimacy(String s) {
    if(s.equals("ANIMATE")) return Dictionaries.Animacy.ANIMATE;
    if(s.equals("INANIMATE")) return Dictionaries.Animacy.INANIMATE;
    if(s.equals("UNKNOWN")) return Dictionaries.Animacy.UNKNOWN;
    throw new RuntimeException("Unknown value: " + s);
  }

  /**
   * Loads the CorefChain objects from the serialized buffer
   * @param reader the buffer
   * @return A map from cluster id to clusters
   * @throws IOException
   */
  private static Map<Integer, CorefChain> loadCorefChains(BufferedReader reader) throws IOException {
    String line = reader.readLine().trim();
    if(line.length() == 0) return null;
    int clusterCount = Integer.valueOf(line);
    Map<Integer, CorefChain> chains = new HashMap<Integer, CorefChain>();
    // read each cluster
    for(int c = 0; c < clusterCount; c ++) {
      line = reader.readLine().trim();
      String [] bits = line.split("\\s");
      int cid = Integer.valueOf(bits[0]);
      int mentionCount = Integer.valueOf(bits[1]);
      Map<IntPair, Set<CorefChain.CorefMention>> mentionMap =
              new HashMap<IntPair, Set<CorefChain.CorefMention>>();
      CorefChain.CorefMention representative = null;
      // read each mention in this cluster
      for(int m = 0; m < mentionCount; m ++) {
        line = reader.readLine();
        bits = line.split("\\s");
        IntPair key = new IntPair(
                Integer.valueOf(bits[0]),
                Integer.valueOf(bits[1]));
        boolean rep = bits[2].equals("1");

        Dictionaries.MentionType mentionType = parseMentionType(bits[3]);
        Dictionaries.Number number = parseNumber(bits[4]);
        Dictionaries.Gender gender = parseGender(bits[5]);
        Dictionaries.Animacy animacy = parseAnimacy(bits[6]);
        int startIndex = Integer.valueOf(bits[7]);
        int endIndex = Integer.valueOf(bits[8]);
        int headIndex = Integer.valueOf(bits[9]);
        int clusterID = Integer.valueOf(bits[10]);
        int mentionID = Integer.valueOf(bits[11]);
        int sentNum = Integer.valueOf(bits[12]);
        int posLen = Integer.valueOf(bits[13]);
        int [] posElems = new int[posLen];
        for(int i = 0; i < posLen; i ++) {
          posElems[i] = Integer.valueOf(bits[14 + i]);
        }
        IntTuple position = new IntTuple(posElems);
        String span = unescapeSpace(bits[14 + posLen]);
        CorefChain.CorefMention mention = new CorefChain.CorefMention(
                mentionType,
                number,
                gender,
                animacy,
                startIndex,
                endIndex,
                headIndex,
                clusterID,
                mentionID,
                sentNum,
                position,
                span);

        Set<CorefChain.CorefMention> mentionsWithThisHead =
                mentionMap.get(key);
        if(mentionsWithThisHead == null) {
          mentionsWithThisHead = new HashSet<CorefChain.CorefMention>();
          mentionMap.put(key, mentionsWithThisHead);
        }
        mentionsWithThisHead.add(mention);
        if(rep) representative = mention;
      }
      // construct the cluster
      CorefChain chain = new CorefChain(cid, mentionMap, representative);
      chains.put(cid, chain);
    }
    reader.readLine();
    return chains;
  }

  public void save(Annotation corpus, OutputStream os) throws IOException {
    os = new BufferedOutputStream(os);
    if(compress) os = new GZIPOutputStream(os);
    PrintWriter pw = new PrintWriter(os);

    // save the coref graph in the new format
    Map<Integer, CorefChain> chains = corpus.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    saveCorefChains(chains, pw);

    // save the coref graph on one line
    // Note: this is the old format!
    List<Pair<IntTuple, IntTuple>> corefGraph = corpus.get(CorefCoreAnnotations.CorefGraphAnnotation.class);
    if(corefGraph != null){
      boolean first = true;
      for(Pair<IntTuple, IntTuple> arc: corefGraph){
        if(! first) pw.print(" ");
        pw.printf("%d %d %d %d", arc.first.get(0), arc.first.get(1), arc.second.get(0), arc.second.get(1));
        first = false;
      }
    }
    pw.println();

    // save sentences separated by an empty line
    List<CoreMap> sentences = corpus.get(CoreAnnotations.SentencesAnnotation.class);
    for(CoreMap sent: sentences){
      // save the parse tree first, on a single line
      Tree tree = sent.get(TreeCoreAnnotations.TreeAnnotation.class);
      if(tree != null){
        String treeString = tree.toString();
        // no \n allowed in the parse tree string (might happen due to tokenization of HTML/XML/RDF tags)
        treeString = treeString.replaceAll("\n", " ");
        pw.println(treeString);
      }
      else pw.println();

      SemanticGraph collapsedDeps = sent.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
      saveDependencyGraph(collapsedDeps, pw);
      SemanticGraph uncollapsedDeps = sent.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      saveDependencyGraph(uncollapsedDeps, pw);
      SemanticGraph ccDeps = sent.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
      saveDependencyGraph(ccDeps, pw);

      // save all sentence tokens
      List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
      if(tokens != null){
        for(CoreLabel token: tokens){
          saveToken(token, haveExplicitAntecedent, pw);
          pw.println();
        }
      }

      // add an empty line after every sentence
      pw.println();
    }

    pw.close();
  }

  private static final String SPACE_HOLDER = "##";

  private static CoreLabel loadToken(String line, boolean haveExplicitAntecedent) {
    CoreLabel token = new CoreLabel();
    String [] bits = line.split("\t", -1);
    if(bits.length < 7) throw new RuntimeIOException("ERROR: Invalid format token for serialized token (only " + bits.length + " tokens): " + line);

    // word
    String word = bits[0].replaceAll(SPACE_HOLDER, " ");
    token.set(CoreAnnotations.TextAnnotation.class, word);
    // if(word.length() == 0) System.err.println("FOUND 0-LENGTH TOKEN!");

    // lemma
    if(bits[1].length() > 0 || bits[0].length() == 0){
      String lemma = bits[1].replaceAll(SPACE_HOLDER, " ");
      token.set(CoreAnnotations.LemmaAnnotation.class, lemma);
    }
    // POS tag
    if(bits[2].length() > 0) token.set(CoreAnnotations.PartOfSpeechAnnotation.class, bits[2]);
    // NE tag
    if(bits[3].length() > 0) token.set(CoreAnnotations.NamedEntityTagAnnotation.class, bits[3]);
    // Normalized NE tag
    if(bits[4].length() > 0) token.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, bits[4]);
    // Character offsets
    if(bits[5].length() > 0) token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, Integer.parseInt(bits[5]));
    if(bits[6].length() > 0) token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, Integer.parseInt(bits[6]));

    if(haveExplicitAntecedent){
      // This block is specific to KBP
      // We may have AntecedentAnnotation
      if(bits.length > 7){
        String aa = bits[7].replaceAll(SPACE_HOLDER, " ");
        if(aa.length() > 0) token.set(CoreAnnotations.AntecedentAnnotation.class, aa);
      }
    }

    return token;
  }

  /**
   * Saves one individual sentence token, in a simple tabular format, in the style of CoNLL
   * @param token
   * @param pw
   */
  private static void saveToken(CoreLabel token, boolean haveExplicitAntecedent, PrintWriter pw) {
    String word = token.get(CoreAnnotations.TextAnnotation.class);
    if(word != null){
      word = word.replaceAll("\\s+", SPACE_HOLDER); // spaces are used for formatting
      pw.print(word);
    }

    pw.print("\t");
    String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
    if(lemma != null){
      lemma = lemma.replaceAll("\\s+", SPACE_HOLDER); // spaces are used for formatting
      pw.print(lemma);
    }

    pw.print("\t");
    String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
    if(pos != null) pw.print(pos);

    pw.print("\t");
    String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
    if(ner != null) pw.print(ner);

    pw.print("\t");
    String normNer = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
    if(normNer != null) pw.print(normNer);

    pw.print("\t");
    Integer charBegin = token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    if(charBegin != null) pw.print(charBegin);

    pw.print("\t");
    Integer charEnd = token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    if(charEnd != null) pw.print(charEnd);

    if(haveExplicitAntecedent){
      // This block is specific to KBP
      // in some cases where we now the entity in focus (i.e., web queries), AntecedentAnnotation is generated
      // let's save it as an optional, always last, token
      String aa = token.get(CoreAnnotations.AntecedentAnnotation.class);
      if(aa != null){
        pw.print("\t");
        aa = aa.replaceAll("\\s+", SPACE_HOLDER); // spaces are used for formatting
        pw.print(aa);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    String file = props.getProperty("file");
    String loadFile = props.getProperty("loadFile");
    if (loadFile != null && ! loadFile.equals("")) {
      CustomAnnotationSerializer ser = new CustomAnnotationSerializer(false, false);
      InputStream is = new FileInputStream(loadFile);
      Annotation anno = ser.load(is);
      System.out.println(anno.toShorterString(new String[0]));
      is.close();
    } else if (file != null && ! file.equals("")) {
      String text = edu.stanford.nlp.io.IOUtils.slurpFile(file);
      Annotation doc = new Annotation(text);
      pipeline.annotate(doc);

      CustomAnnotationSerializer ser = new CustomAnnotationSerializer(false, false);
      PrintStream os = new PrintStream(new FileOutputStream(file + ".ser"));
      ser.save(doc, os);
      os.close();
      System.err.println("Serialized annotation saved in " + file + ".ser");
    } else {
      System.err.println("usage: CustomAnnotationSerializer [-file file] [-loadFile file]");
    }
  }

}
