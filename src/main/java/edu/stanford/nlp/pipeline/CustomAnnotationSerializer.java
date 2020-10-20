package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.stanford.nlp.coref.CorefCoreAnnotations;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.*;

/**
 * Serializes Annotation objects using our own format.
 *
 * Note[gabor]: This is a lossy serialization! For similar performance, and
 * lossless (or less lossy) serialization see,
 * {@link edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer}.
 *
 * @author Mihai
 */
public class CustomAnnotationSerializer extends AnnotationSerializer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CustomAnnotationSerializer.class);

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
      int sentIndex = Integer.parseInt(bits[1]);
      for(int i = 2; i < bits.length; i ++){
        String bit = bits[i];
        String[] bbits = bit.split("-");
        int copyAnnotation = -1;
        boolean isRoot = false;
        if(bbits.length > 3){
          throw new RuntimeException("ERROR: Invalid format for dependency graph: " + line);
        } else if(bbits.length == 2){
          copyAnnotation = Integer.parseInt(bbits[1]);
        } else if(bbits.length == 3){
          copyAnnotation = Integer.parseInt(bbits[1]);
          isRoot = bbits[2].equals("R");
        }
        int index = Integer.parseInt(bbits[0]);
        graph.nodes.add(new IntermediateNode(docId, sentIndex, index, copyAnnotation, isRoot));
      }
    }

    // second line: list of deps
    line = reader.readLine().trim();
    if(line.length() > 0){
      String [] bits = line.split("\t");
      for(String bit: bits){
        String [] bbits = bit.split(" ");
        if(bbits.length < 3 || bbits.length > 6){
          throw new RuntimeException("ERROR: Invalid format for dependency graph: " + line);
        }
        String dep = bbits[0];
        int source = Integer.parseInt(bbits[1]);
        int target = Integer.parseInt(bbits[2]);
        boolean isExtra = (bbits.length == 4) ? Boolean.parseBoolean(bbits[3]) : false;
        int sourceCopy = (bbits.length > 4) ? Integer.parseInt(bbits[4]) : 0;
        int targetCopy = (bbits.length > 5) ? Integer.parseInt(bbits[5]) : 0;
        graph.edges.add(new IntermediateEdge(dep, source, sourceCopy, target, targetCopy, isExtra));
      }
    }

    return graph;
  }

  /**
   * Saves all arcs in the graph on two lines: first line contains the vertices, second the edges.
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
      if(node.copyCount() > 0){
        pw.print("-");
        pw.print(node.copyCount());
        // System.out.println("FOUND COPY ANNOTATION: " + node.get(CoreAnnotations.CopyAnnotation.class));
      }
      if (graph.getRoots().contains(node)) {
        if (node.copyCount() > 0) {
          pw.print("-R");
        } else {
          pw.print("-0-R");
        }
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
      if (edge.isExtra() || edge.getSource().copyCount() > 0 || edge.getTarget().copyCount() > 0) {
        pw.print(" ");
        pw.print(edge.isExtra());
        pw.print(" ");
        pw.print(edge.getSource().copyCount());
        pw.print(" ");
        pw.print(edge.getTarget().copyCount());
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
    for (Map.Entry<Integer, CorefChain> integerCorefChainEntry : chains.entrySet()) {
      // cluster id + how many mentions in the cluster
      saveCorefChain(pw, integerCorefChainEntry.getKey(), integerCorefChainEntry.getValue());
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
   * Serializes one coref cluster (i.e., one entity).
   *
   * @param pw the buffer
   * @param cid id of cluster to save
   * @param cluster the cluster
   */
  public static void saveCorefChain(PrintWriter pw, int cid, CorefChain cluster) {
    pw.println(cid + " " + countMentions(cluster));
    // each mention saved on one line
    Map<IntPair, Set<CorefChain.CorefMention>> mentionMap = cluster.getMentionMap();
    for (Map.Entry<IntPair, Set<CorefChain.CorefMention>> intPairSetEntry : mentionMap.entrySet()) {
      // all mentions with the same head
      IntPair mentionIndices = intPairSetEntry.getKey();
      Set<CorefChain.CorefMention> mentions = intPairSetEntry.getValue();
      for (CorefChain.CorefMention mention: mentions) {
        // one mention per line
        pw.print(mentionIndices.getSource() + " " + mentionIndices.getTarget());
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
    return Dictionaries.MentionType.valueOf(s);
  }
  private static Dictionaries.Number parseNumber(String s) {
    return Dictionaries.Number.valueOf(s);
  }
  private static Dictionaries.Gender parseGender(String s) {
    return Dictionaries.Gender.valueOf(s);
  }
  private static Dictionaries.Animacy parseAnimacy(String s) {
    return Dictionaries.Animacy.valueOf(s);
  }

  /**
   * Loads the CorefChain objects from the serialized buffer.
   *
   * @param reader the buffer
   * @return A map from cluster id to clusters
   * @throws IOException
   */
  private static Map<Integer, CorefChain> loadCorefChains(BufferedReader reader) throws IOException {
    String line = reader.readLine().trim();
    if (line.isEmpty()) return null;
    int clusterCount = Integer.parseInt(line);
    Map<Integer, CorefChain> chains = Generics.newHashMap();
    // read each cluster
    for(int c = 0; c < clusterCount; c ++) {
      line = reader.readLine().trim();
      String [] bits = line.split("\\s");
      int cid = Integer.parseInt(bits[0]);
      int mentionCount = Integer.parseInt(bits[1]);
      Map<IntPair, Set<CorefChain.CorefMention>> mentionMap =
              Generics.newHashMap();
      CorefChain.CorefMention representative = null;
      // read each mention in this cluster
      for(int m = 0; m < mentionCount; m ++) {
        line = reader.readLine();
        bits = line.split("\\s");
        IntPair key = new IntPair(
                Integer.parseInt(bits[0]),
                Integer.parseInt(bits[1]));
        boolean rep = bits[2].equals("1");

        Dictionaries.MentionType mentionType = parseMentionType(bits[3]);
        Dictionaries.Number number = parseNumber(bits[4]);
        Dictionaries.Gender gender = parseGender(bits[5]);
        Dictionaries.Animacy animacy = parseAnimacy(bits[6]);
        int startIndex = Integer.parseInt(bits[7]);
        int endIndex = Integer.parseInt(bits[8]);
        int headIndex = Integer.parseInt(bits[9]);
        int clusterID = Integer.parseInt(bits[10]);
        int mentionID = Integer.parseInt(bits[11]);
        int sentNum = Integer.parseInt(bits[12]);
        int posLen = Integer.parseInt(bits[13]);
        int [] posElems = new int[posLen];
        for(int i = 0; i < posLen; i ++) {
          posElems[i] = Integer.parseInt(bits[14 + i]);
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
          mentionsWithThisHead = Generics.newHashSet();
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

  @Override
  public OutputStream write(Annotation corpus, OutputStream os) throws IOException {
    if (!(os instanceof GZIPOutputStream)) {
      if(compress) os = new GZIPOutputStream(os);
    }
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
    pw.flush();
    return os;
  }

  @Override
  public Pair<Annotation, InputStream> read(InputStream is) throws IOException {
    if(compress && !(is instanceof GZIPInputStream)) is = new GZIPInputStream(is);
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
      List<Pair<IntTuple, IntTuple>> corefGraph = new ArrayList<>();
      for(int i = 0; i < bits.length; i += 4){
        IntTuple src = new IntTuple(2);
        IntTuple dst = new IntTuple(2);
        src.set(0, Integer.parseInt(bits[i]));
        src.set(1, Integer.parseInt(bits[i + 1]));
        dst.set(0, Integer.parseInt(bits[i + 2]));
        dst.set(1, Integer.parseInt(bits[i + 3]));
        corefGraph.add(new Pair<>(src, dst));
      }
      doc.set(CorefCoreAnnotations.CorefGraphAnnotation.class, corefGraph);
    }

    // read individual sentences
    List<CoreMap> sentences = new ArrayList<>();
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
      List<CoreLabel> tokens = new ArrayList<>();
      while((line = reader.readLine()) != null){
        if(line.length() == 0) break;
        CoreLabel token = loadToken(line, haveExplicitAntecedent);
        tokens.add(token);
      }
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);

      // convert the intermediate graph to an actual SemanticGraph
      SemanticGraph collapsedDeps = intermCollapsedDeps.convertIntermediateGraph(tokens);
      sentence.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, collapsedDeps);
      SemanticGraph uncollapsedDeps = intermUncollapsedDeps.convertIntermediateGraph(tokens);
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, uncollapsedDeps);
      SemanticGraph ccDeps = intermCcDeps.convertIntermediateGraph(tokens);
      sentence.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, ccDeps);

      sentences.add(sentence);
    }
    doc.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    return Pair.makePair(doc, is);
  }

  private static final String SPACE_HOLDER = "##";

  private static CoreLabel loadToken(String line, boolean haveExplicitAntecedent) {
    CoreLabel token = new CoreLabel();
    String [] bits = line.split("\t", -1);
    if(bits.length < 7) throw new RuntimeIOException("ERROR: Invalid format token for serialized token (only " + bits.length + " tokens): " + line);

    // word
    String word = bits[0].replaceAll(SPACE_HOLDER, " ");
    token.set(CoreAnnotations.TextAnnotation.class, word);
    token.set(CoreAnnotations.ValueAnnotation.class, word);
    // if(word.length() == 0) log.info("FOUND 0-LENGTH TOKEN!");

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
    if (word == null) {
      word = token.get(CoreAnnotations.ValueAnnotation.class);
    }
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
    if (loadFile != null && ! loadFile.isEmpty()) {
      CustomAnnotationSerializer ser = new CustomAnnotationSerializer(false, false);
      InputStream is = new FileInputStream(loadFile);
      Pair<Annotation, InputStream> pair = ser.read(is);
      pair.second.close();
      Annotation anno = pair.first;
      System.out.println(anno.toShorterString(StringUtils.EMPTY_STRING_ARRAY));
      is.close();
    } else if (file != null && ! file.equals("")) {
      String text = edu.stanford.nlp.io.IOUtils.slurpFile(file);
      Annotation doc = new Annotation(text);
      pipeline.annotate(doc);

      CustomAnnotationSerializer ser = new CustomAnnotationSerializer(false, false);
      PrintStream os = new PrintStream(new FileOutputStream(file + ".ser"));
      ser.write(doc, os).close();
      log.info("Serialized annotation saved in " + file + ".ser");
    } else {
      log.info("usage: CustomAnnotationSerializer [-file file] [-loadFile file]");
    }
  }

}
