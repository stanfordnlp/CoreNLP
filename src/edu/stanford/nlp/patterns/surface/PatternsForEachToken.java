package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;


import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Sonal Gupta on 10/8/14.
 */
public class PatternsForEachToken {

  @Option(name = "useDBForTokenPatterns")
  boolean useDBForTokenPatterns = false;

  @Option(name = "createTable")
  boolean createTable = false;

  @Option(name = "deleteExisting")
  boolean deleteExisting = false;

  @Option(name = "tableName")
  String tableName = null;

  @Option(name="deleteDBResourcesOnExit")
  boolean deleteDBResourcesOnExit = true;

//  @Option(name="allPatternsFile")
//  String allPatternsFile = null;
//
//  /**
//   * If all patterns should be computed. Otherwise patterns are read from
//   * allPatternsFile
//   */
//  @Option(name = "computeAllPatterns")
//  public boolean computeAllPatterns = true;

  private Map<String, Map<Integer, Set<Integer>>> patternsForEachToken = null;

  //Connection conn;

  public PatternsForEachToken(Properties props, Map<String, Map<Integer, Set<Integer>>> pats) throws SQLException, ClassNotFoundException, IOException {
    Execution.fillOptions(this, props);

    if (useDBForTokenPatterns) {
      Execution.fillOptions(SQLConnection.class, props);

      assert tableName != null : "tableName property is null!";
      tableName = tableName.toLowerCase();
      if (createTable && !deleteExisting)
        throw new RuntimeException("Cannot have createTable as true and deleteExisting as false!");
      if (createTable)
        createTable();
      createUpsertFunction();
    }else
      patternsForEachToken = new ConcurrentHashMap<String, Map<Integer, Set<Integer>>>();

    if(pats != null)
      addPatterns(pats);
  }

  public PatternsForEachToken(Properties props) throws SQLException, IOException, ClassNotFoundException {
    this(props, null);
  }

  void createTable() {
    String query ="";
    try {
      Connection conn = null;

      conn = SQLConnection.getConnection();

      DatabaseMetaData dbm = conn.getMetaData();
      ResultSet tables = dbm.getTables(null, null, tableName, null);
      if (tables.next()) {
        System.out.println("Found table " + tableName);
        if (deleteExisting) {
          System.out.println("deleting table " + tableName);
          Statement stmt = conn.createStatement();
          query = "DELETE FROM " + tableName;
          stmt.executeUpdate(query);
        }
      } else {
        Statement stmt = conn.createStatement();
        //query = "create table  IF NOT EXISTS " + tableName + " (\"sentid\" text, \"tokenid\" int, \"patterns\" bytea); ";
        query = "create table  IF NOT EXISTS " + tableName + " (\"sentid\" text, \"patterns\" bytea); ";
        stmt.execute(query);
      }
      conn.close();} catch (SQLException e) {
      throw new RuntimeException("Error executing query " + query + "\n" + e);
    }
  }


  public void addPatterns(Map<String, Map<Integer, Set<Integer>>> pats) throws IOException, SQLException {
    Connection conn = null;
    PreparedStatement pstmt = null;

    if(useDBForTokenPatterns) {
      conn = SQLConnection.getConnection();
      pstmt =getPreparedStmt(conn);
    }

    for (Map.Entry<String, Map<Integer, Set<Integer>>> en : pats.entrySet()) {
      addPattern(en.getKey(), en.getValue(), pstmt);
      if(useDBForTokenPatterns)
        pstmt.addBatch();
    }

    if(useDBForTokenPatterns){
      pstmt.executeBatch();
      conn.commit();
      pstmt.close();
      conn.close();
    }
  }


  public void addPatterns(String id, Map<Integer, Set<Integer>> p) throws IOException, SQLException {
    PreparedStatement pstmt = null;
    Connection conn= null;

    if(useDBForTokenPatterns) {
      conn = SQLConnection.getConnection();
      pstmt = getPreparedStmt(conn);
    }

    addPattern(id, p, pstmt);

    if(useDBForTokenPatterns){
      pstmt.execute();
      conn.commit();
      pstmt.close();
      conn.close();
    }
  }

  /*
  public void addPatterns(String id, Map<Integer, Set<Integer>> p, PreparedStatement pstmt) throws IOException, SQLException {
    for (Map.Entry<Integer, Set<Integer>> en2 : p.entrySet()) {
      addPattern(id, en2.getKey(), en2.getValue(), pstmt);
      if(useDBForTokenPatterns)
        pstmt.addBatch();
    }
  }
*/

/*
  public void addPatterns(String sentId, int tokenId, Set<Integer> patterns) throws SQLException, IOException{
    PreparedStatement pstmt = null;
    Connection conn= null;
    if(useDBForTokenPatterns) {
      conn = SQLConnection.getConnection();
      pstmt = getPreparedStmt(conn);
    }

    addPattern(sentId, tokenId, patterns, pstmt);

    if(useDBForTokenPatterns){
      pstmt.execute();
      conn.commit();
      pstmt.close();
      conn.close();
    }
  }
  */

  /*
  private void addPattern(String sentId, int tokenId, Set<Integer> patterns, PreparedStatement pstmt) throws SQLException, IOException {

    if(pstmt != null){
//      ByteArrayOutputStream baos = new ByteArrayOutputStream();
//      ObjectOutputStream oos = new ObjectOutputStream(baos);
//      oos.writeObject(patterns);
//      byte[] patsAsBytes = baos.toByteArray();
//      ByteArrayInputStream bais = new ByteArrayInputStream(patsAsBytes);
//      pstmt.setBinaryStream(1, bais, patsAsBytes.length);
//      pstmt.setObject(2, sentId);
//      pstmt.setInt(3, tokenId);
//      pstmt.setString(4,sentId);
//      pstmt.setInt(5, tokenId);
//      ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
//      ObjectOutputStream oos2 = new ObjectOutputStream(baos2);
//      oos2.writeObject(patterns);
//      byte[] patsAsBytes2 = baos2.toByteArray();
//      ByteArrayInputStream bais2 = new ByteArrayInputStream(patsAsBytes2);
//      pstmt.setBinaryStream(6, bais2, patsAsBytes2.length);
//      pstmt.setString(7,sentId);
//      pstmt.setInt(8, tokenId);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(patterns);
      byte[] patsAsBytes = baos.toByteArray();
      ByteArrayInputStream bais = new ByteArrayInputStream(patsAsBytes);
      pstmt.setBinaryStream(3, bais, patsAsBytes.length);
      pstmt.setObject(1, sentId);
      pstmt.setInt(2, tokenId);


    } else{
      if(!patternsForEachToken.containsKey(sentId))
        patternsForEachToken.put(sentId, new ConcurrentHashMap<Integer, Set<Integer>>());
      patternsForEachToken.get(sentId).put(tokenId, patterns);
    }
  }*/


  private void addPattern(String sentId, Map<Integer, Set<Integer>> patterns, PreparedStatement pstmt) throws SQLException, IOException {

    if(pstmt != null){
//      ByteArrayOutputStream baos = new ByteArrayOutputStream();
//      ObjectOutputStream oos = new ObjectOutputStream(baos);
//      oos.writeObject(patterns);
//      byte[] patsAsBytes = baos.toByteArray();
//      ByteArrayInputStream bais = new ByteArrayInputStream(patsAsBytes);
//      pstmt.setBinaryStream(1, bais, patsAsBytes.length);
//      pstmt.setObject(2, sentId);
//      pstmt.setInt(3, tokenId);
//      pstmt.setString(4,sentId);
//      pstmt.setInt(5, tokenId);
//      ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
//      ObjectOutputStream oos2 = new ObjectOutputStream(baos2);
//      oos2.writeObject(patterns);
//      byte[] patsAsBytes2 = baos2.toByteArray();
//      ByteArrayInputStream bais2 = new ByteArrayInputStream(patsAsBytes2);
//      pstmt.setBinaryStream(6, bais2, patsAsBytes2.length);
//      pstmt.setString(7,sentId);
//      pstmt.setInt(8, tokenId);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(patterns);
      byte[] patsAsBytes = baos.toByteArray();
      ByteArrayInputStream bais = new ByteArrayInputStream(patsAsBytes);
      pstmt.setBinaryStream(2, bais, patsAsBytes.length);
      pstmt.setObject(1, sentId);
      //pstmt.setInt(2, tokenId);


    } else{
      if(!patternsForEachToken.containsKey(sentId))
        patternsForEachToken.put(sentId, new ConcurrentHashMap<Integer, Set<Integer>>());
      patternsForEachToken.get(sentId).putAll(patterns);
    }
  }


  public void createUpsertFunction() throws SQLException {
    Connection conn = SQLConnection.getConnection();
    String s = "CREATE OR REPLACE FUNCTION upsert_patterns(sentid1 text, pats1 bytea) RETURNS VOID AS $$\n" +
      "DECLARE\n" +
      "BEGIN\n" +
      "    UPDATE " + tableName+ " SET patterns = pats1 WHERE sentid = sentid1;\n" +
      "    IF NOT FOUND THEN\n" +
      "    INSERT INTO " + tableName + "  values (sentid1, pats1);\n" +
      "    END IF;\n" +
      "END;\n" +
      "$$ LANGUAGE 'plpgsql';\n";
    Statement st = conn.createStatement();
    st.execute(s);
    conn.close();
  }

  public void writeIndex(ConcurrentHashIndex<SurfacePattern> index){

  }



  private PreparedStatement getPreparedStmt(Connection conn) throws SQLException {
    conn.setAutoCommit(false);
    //return conn.prepareStatement("UPDATE " + tableName + " SET patterns = ? WHERE sentid = ? and tokenid = ?; " +
    //  "INSERT INTO " + tableName + " (sentid, tokenid, patterns) (SELECT ?,?,? WHERE NOT EXISTS (SELECT sentid FROM " + tableName + " WHERE sentid  =? and tokenid=?));");
    //  return conn.prepareStatement("INSERT INTO " + tableName + " (sentid, tokenid, patterns) (SELECT ?,?,? WHERE NOT EXISTS (SELECT sentid FROM " + tableName + " WHERE sentid  =? and tokenid=?))");
    return conn.prepareStatement("select upsert_patterns(?,?)");
  }




/*
  public Set<Integer> getPatterns(String sentId, Integer tokenId) throws SQLException, IOException, ClassNotFoundException {
    if(useDBForTokenPatterns){
      Connection conn = SQLConnection.getConnection();

      String query = "Select patterns from " + tableName + " where sentid=\'" + sentId + "\' and tokenid = " + tokenId;
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(query);
      Set<Integer> pats = null;
      if(rs.next()){
        byte[] st = (byte[]) rs.getObject(1);
        ByteArrayInputStream baip = new ByteArrayInputStream(st);
        ObjectInputStream ois = new ObjectInputStream(baip);
        pats = (Set<Integer>) ois.readObject();

      }
      conn.close();
      return pats;
    }
    else
      return patternsForEachToken.get(sentId).get(tokenId);
  }*/


  public Map<Integer, Set<Integer>> getPatternsForAllTokens(String sentId) throws SQLException, IOException, ClassNotFoundException {
    if(useDBForTokenPatterns){
      Connection conn = SQLConnection.getConnection();
      //Map<Integer, Set<Integer>> pats = new ConcurrentHashMap<Integer, Set<Integer>>();
      String query = "Select patterns from " + tableName + " where sentid=\'" + sentId + "\'";
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(query);
      Map<Integer, Set<Integer>> patsToken = new HashMap<Integer, Set<Integer>>();
      if(rs.next()){
        byte[] st = (byte[]) rs.getObject(1);
        ByteArrayInputStream baip = new ByteArrayInputStream(st);
        ObjectInputStream ois = new ObjectInputStream(baip);
        patsToken = (Map<Integer, Set<Integer>>) ois.readObject();
        //pats.put(rs.getInt("tokenid"), patsToken);
      }
      conn.close();
      return patsToken;
    }
    else
      return patternsForEachToken.containsKey(sentId) ? patternsForEachToken.get(sentId): Collections.emptyMap();
  }



  boolean getUseDBForTokenPatterns(){
    return useDBForTokenPatterns;
  }

  public boolean writePatternsIfInMemory(String allPatternsFile) throws IOException {
    if(!useDBForTokenPatterns)
    {
      IOUtils.writeObjectToFile(this.patternsForEachToken, allPatternsFile);
      return true;
    }
    return false;
  }


  public boolean containsSentId(String sentId) throws SQLException {
    if(!useDBForTokenPatterns)
      return this.patternsForEachToken.containsKey(sentId);
    else {
      Connection conn = SQLConnection.getConnection();
      String query = "Select tokenid from " + tableName + " where sentid=\'" + sentId + "\' limit 1";
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(query);

      boolean contains = false;

      while(rs.next()){
        contains = true;
        break;
      }

      conn.close();
      return contains;
    }
  }

  /**
   * not yet supported if backed by DB
   * @return
   */
  public Set<Map.Entry<String, Map<Integer, Set<Integer>>>> entrySet() {
    if(!useDBForTokenPatterns)
      return patternsForEachToken.entrySet();
    else
      //not yet supported if backed by DB
      throw new UnsupportedOperationException();
  }

  public void updatePatterns(Map<String, Map<Integer, Set<Integer>>> tempPatsForSents) {
    try {
    for(Map.Entry<String, Map<Integer, Set<Integer>>> en :tempPatsForSents.entrySet()){
      Map<Integer, Set<Integer>> m = getPatternsForAllTokens(en.getKey());
      if(m == null)
        m = new HashMap<Integer, Set<Integer>>();
      //m.putAll(en.getValue());
      tempPatsForSents.get(en.getKey()).putAll(m);
    }
      this.addPatterns(tempPatsForSents);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (SQLException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }
}
