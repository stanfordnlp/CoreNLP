package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.patterns.SQLConnection;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * Created by sonalg on 10/22/14.
 */
public class PatternsForEachTokenDB<E extends Pattern> extends PatternsForEachToken<E>{


  @ArgumentParser.Option(name = "createTable")
  boolean createTable = false;

  @ArgumentParser.Option(name = "deleteExisting")
  boolean deleteExisting = false;

  @ArgumentParser.Option(name = "tableName")
  String tableName = null;

  @ArgumentParser.Option(name = "patternindicesTable")
  String patternindicesTable = "patternindices";

  @ArgumentParser.Option(name="deleteDBResourcesOnExit")
  boolean deleteDBResourcesOnExit = true;

  public PatternsForEachTokenDB(Properties props, Map<String, Map<Integer, Set<E>>> pats){

    ArgumentParser.fillOptions(this, props);


      ArgumentParser.fillOptions(SQLConnection.class, props);

      assert tableName != null : "tableName property is null!";
      tableName = tableName.toLowerCase();
      if (createTable && !deleteExisting)
        throw new RuntimeException("Cannot have createTable as true and deleteExisting as false!");
      if (createTable){
        createTable();
        createUpsertFunction();
      }else{
        assert DBTableExists() : "Table " + tableName + " does not exists. Pass createTable=true to create a new table";
      }


    if(pats != null)
      addPatterns(pats);
  }

  public PatternsForEachTokenDB(Properties props) {
    this(props, null);
  }

  void createTable() {
    String query ="";
    try {
      Connection conn = SQLConnection.getConnection();
      if(DBTableExists()){
        if (deleteExisting) {
          System.out.println("deleting table " + tableName);
          Statement stmt = conn.createStatement();
          query = "drop table " + tableName;
          stmt.execute(query);
          stmt.close();
          Statement stmtindex = conn.createStatement();
          query = "DROP INDEX IF EXISTS " + tableName+"_index";
          stmtindex.execute(query);
          stmtindex.close();
        }
      }
      System.out.println("creating table " + tableName);
      Statement stmt = conn.createStatement();
      //query = "create table  IF NOT EXISTS " + tableName + " (\"sentid\" text, \"tokenid\" int, \"patterns\" bytea); ";
      query = "create table IF NOT EXISTS " + tableName + " (sentid text, patterns bytea); ";
      stmt.execute(query);
      stmt.close();
      conn.close();} catch (SQLException e) {
      throw new RuntimeException("Error executing query " + query + "\n" + e);
    }
  }

  @Override
  public void addPatterns(Map<String, Map<Integer, Set<E>>> pats){
    try {
      Connection conn = null;
      PreparedStatement pstmt = null;


      conn = SQLConnection.getConnection();
      pstmt = getPreparedStmt(conn);


      for (Map.Entry<String, Map<Integer, Set<E>>> en : pats.entrySet()) {
        addPattern(en.getKey(), en.getValue(), pstmt);

        pstmt.addBatch();
      }


      pstmt.executeBatch();
      conn.commit();
      pstmt.close();
      conn.close();
    } catch (SQLException | IOException e) {
      throw new RuntimeException(e);
    }
  }


  public void addPatterns(String id, Map<Integer, Set<E>> p){
    try {
    PreparedStatement pstmt = null;
    Connection conn= null;


      conn = SQLConnection.getConnection();
      pstmt = getPreparedStmt(conn);

    addPattern(id, p, pstmt);


      pstmt.execute();
      conn.commit();

    pstmt.close();
      conn.close();
    } catch (SQLException | IOException e) {
      throw new RuntimeException(e);
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


  private void addPattern(String sentId, Map<Integer, Set<E>> patterns, PreparedStatement pstmt) throws SQLException, IOException {

    if(pstmt != null){
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(patterns);
      byte[] patsAsBytes = baos.toByteArray();
      ByteArrayInputStream bais = new ByteArrayInputStream(patsAsBytes);
      pstmt.setBinaryStream(2, bais, patsAsBytes.length);
      pstmt.setObject(1, sentId);
      //pstmt.setInt(2, tokenId);


    }
  }


  public void createUpsertFunction() {
    try{
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
    conn.close();}catch(SQLException e){
      throw new RuntimeException(e);
    }
  }

  public void createUpsertFunctionPatternIndex() throws SQLException {
    Connection conn = SQLConnection.getConnection();
    String s = "CREATE OR REPLACE FUNCTION upsert_patternindex(tablename1 text, index1 bytea) RETURNS VOID AS $$\n" +
      "DECLARE\n" +
      "BEGIN\n" +
      "    UPDATE " + patternindicesTable + " SET index = index1 WHERE  tablename = tablename1;\n" +
      "    IF NOT FOUND THEN\n" +
      "    INSERT INTO " + patternindicesTable + "  values (tablename1, index1);\n" +
      "    END IF;\n" +
      "END;\n" +
      "$$ LANGUAGE 'plpgsql';\n";
    Statement st = conn.createStatement();
    st.execute(s);
    conn.close();
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



  @Override
  public Map<Integer, Set<E>> getPatternsForAllTokens(String sentId){
  try{
      Connection conn = SQLConnection.getConnection();
      //Map<Integer, Set<Integer>> pats = new ConcurrentHashMap<Integer, Set<Integer>>();
      String query = "Select patterns from " + tableName + " where sentid=\'" + sentId + "\'";
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(query);
      Map<Integer, Set<E>> patsToken = new HashMap<>();
      if(rs.next()){
        byte[] st = (byte[]) rs.getObject(1);
        ByteArrayInputStream baip = new ByteArrayInputStream(st);
        ObjectInputStream ois = new ObjectInputStream(baip);
        patsToken = (Map<Integer, Set<E>>) ois.readObject();
        //pats.put(rs.getInt("tokenid"), patsToken);
      }
      conn.close();
      return patsToken;
  } catch (SQLException | ClassNotFoundException | IOException e) {
    throw new RuntimeException(e);
  }
  }

  @Override
  public boolean save(String dir) {
    //nothing to do
    return false;
  }

  @Override
  public void setupSearch() {
    //nothing to do
  }

  public boolean containsSentId(String sentId){
      try {
        Connection conn = SQLConnection.getConnection();
        String query = "Select tokenid from " + tableName + " where sentid=\'" + sentId + "\' limit 1";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);

        boolean contains = false;

        while (rs.next()) {
          contains = true;
          break;
        }

        conn.close();
        return contains;
      }catch(SQLException e){
        throw new RuntimeException(e);
      }
  }

  @Override
  public void createIndexIfUsingDBAndNotExists(){
      try {
        Redwood.log(Redwood.DBG, "Creating index for " + tableName);
        Connection conn = SQLConnection.getConnection();
        Statement stmt = conn.createStatement();
        boolean doesnotexist = false;

        //check if the index already exists
        try{
          Statement stmt2 = conn.createStatement();
          String query = "SELECT '"+tableName+"_index'::regclass";
          stmt2.execute(query);
        } catch (SQLException e){
          doesnotexist = true;
        }

        if(doesnotexist){
          String indexquery ="create index CONCURRENTLY " + tableName +"_index on " + tableName+ " using hash(\"sentid\") ";
          stmt.execute(indexquery);
          Redwood.log(Redwood.DBG, "Done creating index for " + tableName);
        }
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }

  }

//  /**
//   * not yet supported if backed by DB
//   * @return
//   */
//  public Set<Map.Entry<String, Map<Integer, Set<Integer>>>> entrySet() {
//    if(!useDBForTokenPatterns)
//      return patternsForEachToken.entrySet();
//    else
//      //not yet supported if backed by DB
//      throw new UnsupportedOperationException();
//  }

  public boolean DBTableExists() {
    try {
      Connection conn = null;

      conn = SQLConnection.getConnection();

      DatabaseMetaData dbm = conn.getMetaData();
      ResultSet tables = dbm.getTables(null, null, tableName, null);
      if (tables.next()) {
        System.out.println("Found table " + tableName);
        conn.close();
        return true;
      }
      conn.close();
      return false;
    }catch(SQLException e){
      throw new RuntimeException(e);

    }
  }
//
//  @Override
//  public ConcurrentHashIndex<SurfacePattern> readPatternIndex(String dir){
//    //dir parameter is not used!
//    try{
//      Connection conn = SQLConnection.getConnection();
//      //Map<Integer, Set<Integer>> pats = new ConcurrentHashMap<Integer, Set<Integer>>();
//      String query = "Select index from " + patternindicesTable + " where tablename=\'" + tableName + "\'";
//      Statement stmt = conn.createStatement();
//      ResultSet rs = stmt.executeQuery(query);
//      ConcurrentHashIndex<SurfacePattern> index = null;
//      if(rs.next()){
//        byte[] st = (byte[]) rs.getObject(1);
//        ByteArrayInputStream baip = new ByteArrayInputStream(st);
//        ObjectInputStream ois = new ObjectInputStream(baip);
//        index  = (ConcurrentHashIndex<SurfacePattern>) ois.readObject();
//      }
//      assert index != null;
//      return index;
//    }catch(SQLException e){
//      throw new RuntimeException(e);
//    } catch (ClassNotFoundException e) {
//      throw new RuntimeException(e);
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//  }
//
//  @Override
//  public void savePatternIndex(ConcurrentHashIndex<SurfacePattern> index, String file) {
//    try {
//      createUpsertFunctionPatternIndex();
//      Connection conn = SQLConnection.getConnection();
//      PreparedStatement  st = conn.prepareStatement("select upsert_patternindex(?,?)");
//      st.setString(1,tableName);
//      ByteArrayOutputStream baos = new ByteArrayOutputStream();
//      ObjectOutputStream oos = new ObjectOutputStream(baos);
//      oos.writeObject(index);
//      byte[] patsAsBytes = baos.toByteArray();
//      ByteArrayInputStream bais = new ByteArrayInputStream(patsAsBytes);
//      st.setBinaryStream(2, bais, patsAsBytes.length);
//      st.execute();
//      st.close();
//      conn.close();
//      System.out.println("Saved the pattern hash index for " + tableName + " in DB table " + patternindicesTable);
//    }catch (SQLException e){
//      throw new RuntimeException(e);
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//  }

  //batch processing below is copied from Java Ranch
  public static final int SINGLE_BATCH = 1;
  public static final int SMALL_BATCH = 4;
  public static final int MEDIUM_BATCH = 11;
  public static final int LARGE_BATCH = 51;

  //TODO: make this into an iterator!!
  @Override
  public Map<String, Map<Integer, Set<E>>> getPatternsForAllTokens(Collection<String> sampledSentIds) {
    try{
      Map<String, Map<Integer, Set<E>>> pats = new HashMap<>();
      Connection conn = SQLConnection.getConnection();
      Iterator<String> iter = sampledSentIds.iterator();
      int totalNumberOfValuesLeftToBatch = sampledSentIds.size();
      while ( totalNumberOfValuesLeftToBatch > 0 ) {

        int batchSize = SINGLE_BATCH;
        if (totalNumberOfValuesLeftToBatch >= LARGE_BATCH) {
          batchSize = LARGE_BATCH;
        } else if (totalNumberOfValuesLeftToBatch >= MEDIUM_BATCH) {
          batchSize = MEDIUM_BATCH;
        } else if (totalNumberOfValuesLeftToBatch >= SMALL_BATCH) {
          batchSize = SMALL_BATCH;
        }
        totalNumberOfValuesLeftToBatch -= batchSize;


        StringBuilder inClause = new StringBuilder();

        for (int i = 0; i < batchSize; i++) {
          inClause.append('?');
          if (i != batchSize - 1) {
            inClause.append(',');
          }
        }
        PreparedStatement stmt = conn.prepareStatement(
          "select sentid, patterns from " + tableName + " where sentid in (" + inClause.toString() + ")");
        for (int i=0; i < batchSize && iter.hasNext(); i++) {
          stmt.setString(i+1, iter.next());  // or whatever values you are trying to query by
        }
        stmt.execute();
        ResultSet rs = stmt.getResultSet();
        while(rs.next()){
          String sentid = rs.getString(1);
          byte[] st = (byte[]) rs.getObject(2);
          ByteArrayInputStream baip = new ByteArrayInputStream(st);
          ObjectInputStream ois = new ObjectInputStream(baip);
          pats.put(sentid, (Map<Integer, Set<E>>) ois.readObject());
        }

      }
      conn.close();
      return pats;
    } catch(SQLException | ClassNotFoundException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    //nothing to do
  }

  @Override
  public void load(String allPatternsDir) {
    //nothing to do
  }

  @Override
  public int size(){
    //TODO: NOT IMPLEMENTED
    return Integer.MAX_VALUE;
  }

}
