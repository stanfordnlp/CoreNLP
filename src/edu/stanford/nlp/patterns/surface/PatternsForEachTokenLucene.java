package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.ArgumentParser.Option;
import edu.stanford.nlp.util.LuceneFieldType;
import edu.stanford.nlp.util.logging.Redwood;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by sonalg on 10/22/14.
 */
public class PatternsForEachTokenLucene<E extends Pattern> extends PatternsForEachToken<E> {

  static IndexWriter indexWriter;
  static File indexDir = null;
  static Directory dir;

  static Analyzer analyzer = new KeywordAnalyzer();

  static IndexWriterConfig iwc=new IndexWriterConfig(Version.LUCENE_42, analyzer);
  static DirectoryReader reader = null;
  static IndexSearcher searcher;

  //ProtobufAnnotationSerializer p = new ProtobufAnnotationSerializer();
  static AtomicBoolean openIndexWriter = new AtomicBoolean(false);

  @Option(name="allPatternsDir")
  String allPatternsDir;

  @Option(name="createPatLuceneIndex",required=true)
  boolean createPatLuceneIndex;

  public PatternsForEachTokenLucene(Properties props, Map<String, Map<Integer, Set<E>>> pats){
    ArgumentParser.fillOptions(this, props);

    if(allPatternsDir == null){

      File f;
      try {
        f = File.createTempFile("allpatterns", "index");
        System.out.println("No directory provided for creating patternsForEachToken lucene index. Making it at " + f.getAbsolutePath());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      f.deleteOnExit();
      allPatternsDir = f.getAbsolutePath();
    }

    if(createPatLuceneIndex) {
      Redwood.log("Deleting any exising index at " + allPatternsDir);
      IOUtils.deleteDirRecursively(new File(allPatternsDir));
    }

    indexDir = new File(allPatternsDir);

    if(pats!= null){
      addPatterns(pats);
    }
    //setIndexReaderSearcher();
  }

  public void checkClean(){
    try {
      dir = FSDirectory.open(indexDir);
    CheckIndex checkIndex = new CheckIndex(dir);
    CheckIndex.Status status = checkIndex.checkIndex();
    assert (status.clean) : "index is not clean";
      dir.close();
    } catch (IOException e) {
    throw new RuntimeException(e);
  }
  }

  public PatternsForEachTokenLucene(Properties props){
    this(props, null);
  }

  @Override
  public void setupSearch(){
    setIndexReaderSearcher();
  }

  static synchronized void setIndexReaderSearcher() {
    try{
    FSDirectory index = NIOFSDirectory.open(indexDir);
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
    }catch(IOException e){
      throw new RuntimeException(e);
    }
  }




  @Override
  public void addPatterns(Map<String, Map<Integer, Set<E>>> pats) {
    try {
      setIndexWriter();

      for(Map.Entry<String, Map<Integer, Set<E>>> en: pats.entrySet()){
        //String sentence = StringUtils.joinWords(en.getValue(), " ");
        addPatterns(en.getKey(), en.getValue(), false);
      }

      indexWriter.commit();

      //closeIndexWriter();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


//
//  @Override
//  public void finishUpdating() {
//    if(indexWriter != null){
//      try {
//        indexWriter.commit();
//      } catch (IOException e) {
//        throw new RuntimeException(e);
//      }
//    }
//    closeIndexWriter();
//  }
//
//  @Override
//  public void update(List<CoreLabel> tokens, String sentid) {
//    try {
//      setIndexWriter();
//      indexWriter.deleteDocuments(new TermQuery(new Term("sentid",sentid)));
//      add(tokens, sentid);
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//
//  }

  static synchronized void setIndexWriter()  {
    try{
    if(!openIndexWriter.get()){
      dir = FSDirectory.open(indexDir);
      Redwood.log(Redwood.DBG, "Updating lucene index at " + indexDir);
      indexWriter = new IndexWriter(dir, iwc);
      openIndexWriter.set(true);
    }
  }catch(IOException e){
    throw new RuntimeException(e);
  }
  }

  static synchronized void closeIndexWriter(){
    try {
      if(openIndexWriter.get()){
        indexWriter.close();
        openIndexWriter.set(false);
        indexWriter = null;
        Redwood.log(Redwood.DBG, "closing index writer");
      }

      if(dir != null)
        dir.close();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  @Override
  public void close(){
    closeIndexWriter();
  }

  @Override
  public void load(String allPatternsDir) {
    assert new File(allPatternsDir).exists();
  }

  @Override
  public void addPatterns(String id, Map<Integer, Set<E>> p) {
      addPatterns(id, p, true);
  }

  private void addPatterns(String id, Map<Integer, Set<E>> p, boolean commit) {
    try{
      setIndexWriter();
      Document doc = new Document();
      doc.add(new StringField("sentid", id, Field.Store.YES));
      doc.add(new Field("patterns", getBytes(p), LuceneFieldType.NOT_INDEXED));
      indexWriter.addDocument(doc);
      if(commit){
        indexWriter.commit();
        //closeIndexWriter();
      }
    }catch(IOException e){
      throw new RuntimeException(e);
    }
  }

  private byte[] getBytes(Map<Integer, Set<E>> p){
    try{
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(p);
    return  baos.toByteArray();}catch(IOException e){
      throw new RuntimeException(e);
    }
  }

//  private byte[] getProtoBufAnnotation(Map<Integer, Set<Integer>> p) {
//    //TODO: finish this
//    return new byte[0];
//  }

  @Override
  public void createIndexIfUsingDBAndNotExists() {
    //nothing to do
    return;
  }


  @Override
  public Map<Integer, Set<E>> getPatternsForAllTokens(String sentId) {
    try {
      TermQuery query = new TermQuery(new Term("sentid", sentId));
      TopDocs tp = searcher.search(query,1);
      if (tp.totalHits > 0) {
        for (ScoreDoc s : tp.scoreDocs) {
          int docId = s.doc;
          Document d = searcher.doc(docId);
          byte[] st = d.getBinaryValue("patterns").bytes;
          ByteArrayInputStream baip = new ByteArrayInputStream(st);
          ObjectInputStream ois = new ObjectInputStream(baip);
          return (Map<Integer, Set<E>>) ois.readObject();
        }
      } else
        throw new RuntimeException("Why no patterns for sentid " + sentId + ". Number of documents in index are " + size());

    }catch(IOException e){
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  @Override
  public boolean save(String dir) {
    //nothing to do
    return false;
  }

//  private Map<Integer, Set<Integer>> readProtoBuf(byte[] patterns) {
//      //TODO
//  }

//  @Override
//  public ConcurrentHashIndex<SurfacePattern> readPatternIndex(String dir) {
//    try {
//      return IOUtils.readObjectFromFile(dir+"/patternshashindex.ser");
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    } catch (ClassNotFoundException e) {
//      throw new RuntimeException(e);
//    }
//  }

//  @Override
//  public void savePatternIndex(ConcurrentHashIndex<SurfacePattern> index, String dir) {
//    try {
//      if(dir != null)
//        IOUtils.writeObjectToFile(index, dir+"/patternshashindex.ser");
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//  }


  @Override
  public Map<String, Map<Integer, Set<E>>> getPatternsForAllTokens(Collection<String> sentIds) {
    close();
    setIndexReaderSearcher();
    Map<String, Map<Integer, Set<E>>> pats = new HashMap<>();
    for(String s: sentIds){
      pats.put(s, getPatternsForAllTokens(s));
    }
    setIndexWriter();
    return pats;
  }

  @Override
  int size(){
    setIndexReaderSearcher();
    return searcher.getIndexReader().numDocs();
  }

}
