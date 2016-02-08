package edu.stanford.nlp.patterns;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.surface.Token;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.LuceneFieldType;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.ArgumentParser.Option;
import edu.stanford.nlp.util.logging.Redwood;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.*;

import java.util.*;
import java.util.function.Function;



/**
 * To create a lucene inverted index from tokens to sentence ids.
 * (Right now it is not storing all core tokens although some functions might suggest that.)
 *
 * @author Sonal Gupta on 10/14/14.
 */
public class LuceneSentenceIndex<E extends Pattern> extends SentenceIndex<E> {

  @Option(name="saveTokens")
  boolean saveTokens = false;

  IndexWriter indexWriter;
  File indexDir = null;
  Directory dir;

  Analyzer analyzer = new KeywordAnalyzer();
//  Analyzer analyzer = new Analyzer() {
//    @Override
//    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
//      Tokenizer source = new KeywordTokenizer(reader);
//      TokenStream result = new StopWordsFilter(source);
//      return new TokenStreamComponents(source, result);
//    }
//  };

//  public final class StopWordsFilter extends FilteringTokenFilter {
//    /**
//     * Build a filter that removes words that are too long or too
//     * short from the text.
//     */
//    public StopWordsFilter(TokenStream in) {
//      super(true, in);
//    }
//
//    @Override
//    public boolean accept() throws IOException {
//      return !stopWords.contains(input.toString().toLowerCase());
//    }
//  }

  //StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
  IndexWriterConfig iwc=new IndexWriterConfig(Version.LUCENE_42, analyzer);
  DirectoryReader reader = null;
  IndexSearcher searcher;
  ProtobufAnnotationSerializer p = new ProtobufAnnotationSerializer();

  //The fields in index are: tokens, sentence id, List<CoreLabel> annotation of the sentence (optional; not used when sentences are in memory)
  public LuceneSentenceIndex(Properties props, Set<String> stopWords, String indexDirStr, Function<CoreLabel, Map<String, String>> transformer) {
    super(stopWords, transformer);
    ArgumentParser.fillOptions(this, props);
    indexDir = new File(indexDirStr);
  }


  void setIndexReaderSearcher() throws IOException {
    FSDirectory index = FSDirectory.open(indexDir);
    if(reader == null){
      reader = DirectoryReader.open(index);
      searcher = new IndexSearcher(reader);
    }else{
      DirectoryReader newreader = DirectoryReader.openIfChanged(reader);
      if(newreader != null) {
        reader.close();
        reader = newreader;
        searcher = new IndexSearcher(reader);
      }
    }
  }

//  SentenceIndex.SentenceIteratorWithWords queryIndex(SurfacePattern pat){
//
//
//    String[] n = pat.getSimplerTokensNext();
//    String[] pr = pat.getSimplerTokensPrev();
//    boolean rest = false;
//    if(n!=null){
//      for(String e: n){
//        if(!specialWords.contains(e)){
//          rest = true;
//          break;
//        }
//      }
//    }
//    if(rest == false && pr!=null){
//      for(String e: pr){
//        if(!specialWords.contains(e) && !stopWords.contains(e)){
//          rest = true;
//          break;
//        }
//      }
//    }
//
//  }

  /**
   * give all sentences that have these words
   * **/
  Set<String> queryIndexGetSentences(CollectionValuedMap<String, String> words) throws IOException, ParseException {
    setIndexReaderSearcher();
    BooleanQuery query = new BooleanQuery();
    String pkey  = Token.getKeyForClass(PatternsAnnotations.ProcessedTextAnnotation.class);

    for(Map.Entry<String, Collection<String>> en: words.entrySet()){
      boolean processedKey = en.getKey().equals(pkey);
      for(String en2: en.getValue()){
          if(!processedKey || !stopWords.contains(en2.toLowerCase()))
            query.add(new BooleanClause(new TermQuery(new Term(en.getKey(), en2)), BooleanClause.Occur.MUST));
      }
    }
    //query.add(new BooleanClause(new TermQuery(new Term("textannotation","sonal")), BooleanClause.Occur.MUST));

//    String queryStr = "";
//    for(Map.Entry<String, Collection<String>> en: words.entrySet()){
//      for(String en2: en.getValue()){
//        queryStr+= " " + en.getKey() + ":"+en2;
//      }
//    }
//    QueryParser queryParser = new QueryParser(Version.LUCENE_42, "sentence", analyzer);
//
//    queryParser.setDefaultOperator(QueryParser.Operator.AND);
//
//    Query query = queryParser.parse(queryStr);

    //Map<String, List<CoreLabel>> sents = null;
    TopDocs tp = searcher.search(query, Integer.MAX_VALUE);
    Set<String> sentids = new HashSet<>();
    if (tp.totalHits > 0) {
      for (ScoreDoc s : tp.scoreDocs) {
        int docId = s.doc;
        Document d = searcher.doc(docId);
//        byte[] sent = d.getBinaryValue("tokens").bytes;
//        if(saveTokens) {
//          sents = new HashMap<String, List<CoreLabel>>();
//          List<CoreLabel> tokens = readProtoBufAnnotation(sent);
//          sents.put(d.get("sentid"), tokens);
//        } else{
        sentids.add(d.get("sentid"));
        //}
      }
    } else {
      throw new RuntimeException("how come no documents for " + words + ". Query formed is " + query);
      //System.out.println("number of sentences for tokens " + words + " are " + sentids);
//    if(!saveTokens){
//      sents = getSentences(sentids);
//    }
    }
    return sentids;
  }


  @Override
  public void add(Map<String, DataInstance> sentences, boolean addProcessedText) {
    try {
      this.setIndexWriter();

      for(Map.Entry<String, DataInstance> en: sentences.entrySet()){
        //String sentence = StringUtils.joinWords(en.getValue(), " ");
        add(en.getValue().getTokens(), en.getKey(), addProcessedText);
      }

      indexWriter.commit();
      closeIndexWriter();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public Map<E, Set<String>> queryIndex(Collection<E> patterns) {
    try{
      Map<E, Set<String>> sents = new HashMap<>();
      for(E p : patterns){
        Set<String> sentids = queryIndexGetSentences(p.getRelevantWords());
        sents.put(p, sentids);
      }
      return sents;
    }
    catch (ParseException | IOException e) {
      throw new RuntimeException(e);
    }
  }



  public void listAllDocuments() throws IOException {
    setIndexReaderSearcher();
    for(int i = 0; i < reader.numDocs(); i++){
      Document d = searcher.doc(i);
//      byte[] sent = d.getBinaryValue("tokens").bytes;
//      List<CoreLabel> tokens = readProtoBufAnnotation(sent);
      System.out.println(d.get("sentid"));
    }
  }

  private List<CoreLabel> readProtoBufAnnotation(byte[] sent) throws IOException {
    ProtobufAnnotationSerializer p = new ProtobufAnnotationSerializer();
    List<CoreLabel> toks = new ArrayList<>();
    ByteArrayInputStream is = new ByteArrayInputStream(sent);
    CoreNLPProtos.Token d;
    do{
      d = CoreNLPProtos.Token.parseDelimitedFrom(is);
      if(d != null)
        toks.add(p.fromProto(d));
    } while(d != null);
    return toks;
  }



  byte[] getProtoBufAnnotation(List<CoreLabel> tokens) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    for(CoreLabel token: tokens){
      CoreNLPProtos.Token ptoken = p.toProto(token);
      ptoken.writeDelimitedTo(os);
    }
    os.flush();
    return os.toByteArray();
  }

  @Override
  protected void add(List<CoreLabel> tokens, String sentid, boolean addProcessedText){
    try{
      setIndexWriter();

      Document doc = new Document();

      for(CoreLabel l : tokens) {
        for (Map.Entry<String, String> en: transformCoreLabeltoString.apply(l).entrySet()) {
          doc.add(new StringField(en.getKey(), en.getValue(), Field.Store.YES));//, ANALYZED));
        }
        if(addProcessedText){
          String ptxt = l.get(PatternsAnnotations.ProcessedTextAnnotation.class);
          if(!stopWords.contains(ptxt.toLowerCase()))
            doc.add(new StringField(Token.getKeyForClass(PatternsAnnotations.ProcessedTextAnnotation.class), ptxt, Field.Store.YES));//, ANALYZED));
        }
      }
      doc.add(new StringField("sentid", sentid, Field.Store.YES));


      if(tokens!= null && saveTokens)
        doc.add(new Field("tokens", getProtoBufAnnotation(tokens), LuceneFieldType.NOT_INDEXED));

      indexWriter.addDocument(doc);

    }catch(IOException e){
      throw new RuntimeException(e);
    }
  }

  @Override
  public void finishUpdating() {
    if(indexWriter != null){
    try {
      indexWriter.commit();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    }
    closeIndexWriter();
  }

  @Override
  public void update(List<CoreLabel> tokens, String sentid) {
    try {
      setIndexWriter();
      indexWriter.deleteDocuments(new TermQuery(new Term("sentid",sentid)));
      add(tokens, sentid, true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  void setIndexWriter()  {try{
    if(indexWriter == null){
      dir = FSDirectory.open(indexDir);
      Redwood.log(Redwood.DBG, "Updating lucene index at " + indexDir);
      indexWriter = new IndexWriter(dir, iwc);
    }}catch(IOException e){
    throw new RuntimeException(e);
  }
  }

  void closeIndexWriter(){
    try {
    if(indexWriter != null)
        indexWriter.close();
    indexWriter = null;

      if(dir != null)
      dir.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void saveIndex(String dir){
    if(!indexDir.toString().equals(dir)){
      try {
        IOUtils.cp(indexDir, new File(dir), true);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }


  public static LuceneSentenceIndex createIndex(Map<String, List<CoreLabel>> sentences,  Properties props, Set<String> stopWords, String indexDiskDir, Function<CoreLabel, Map<String, String>> transformer) {
    try{
      LuceneSentenceIndex sentindex = new LuceneSentenceIndex(props, stopWords, indexDiskDir, transformer);
      System.out.println("Creating lucene index at " + indexDiskDir);
      IOUtils.deleteDirRecursively(sentindex.indexDir);
      if(sentences!= null){
        sentindex.setIndexWriter();

        sentindex.add(sentences, true);

        sentindex.closeIndexWriter();
        sentindex.setIndexReaderSearcher();
        System.out.println("Number of documents added are " + sentindex.reader.numDocs());
        sentindex.numAllSentences += sentindex.reader.numDocs();
      }
      return sentindex;
    }catch(IOException e){
      throw new RuntimeException(e);
    }
  }

  public static LuceneSentenceIndex loadIndex(Properties props, Set<String> stopwords, String dir,  Function<CoreLabel, Map<String, String>> transformSentenceToString) {
    try {
    LuceneSentenceIndex sentindex = new LuceneSentenceIndex(props, stopwords, dir, transformSentenceToString);
    sentindex.setIndexReaderSearcher();
    System.out.println("Number of documents read from the index " + dir + " are " + sentindex.reader.numDocs());
    sentindex.numAllSentences += sentindex.reader.numDocs();
    return sentindex;
    } catch (IOException e) {
     throw new RuntimeException(e);
    }
  }

}


