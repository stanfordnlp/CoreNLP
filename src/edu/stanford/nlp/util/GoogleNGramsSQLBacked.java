package edu.stanford.nlp.util;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.ArgumentParser.Option;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** To query Google Ngrams counts from SQL in a memory efficient manner.
 *  To get count of a phrase, use GoogleNGramsSQLBacked.getCount(phrase). Set this class options using
 *  Execution.fillOptions(GoogleNGramsSQLBacked.class, props);
 *
 *  @author Sonal Gupta
 */

public class GoogleNGramsSQLBacked  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(GoogleNGramsSQLBacked.class);

  @Option(name="populateTables")
  static boolean populateTables = false;

  @Option(name="ngramsToPopulate")
  static Set<Integer> ngramsToPopulate = null;

  @Option(name="dataDir")
  static String dataDir ="/u/nlp/scr/data/google-ngrams/data";

  @Option(name="googleNgram_hostname", gloss="where psql is located.")
  static String googleNgram_hostname = "jonsson";

  @Option(name="googleNgram_dbname", gloss="the database name")
  static String googleNgram_dbname;

  @Option(name="googleNgram_username")
  static String googleNgram_username="nlp";

  @Option(name="tablenamePrefix")
  static String tablenamePrefix ="googlengrams_";

  @Option(name="escapetag")
  static String escapetag = "tag";

  static Set<String> existingTablenames = null;

  static Connection connection = null;
  private static String DBName;

  static void connect () throws SQLException{
    if(connection == null) {
      assert googleNgram_dbname != null : "set googleNgram_dbname variable through the properties file";
      connection = DriverManager.getConnection(
        "jdbc:postgresql://" + googleNgram_hostname + "/" + googleNgram_dbname, googleNgram_username, "");
    }
  }

  static String escapeString(String str){
    return "$"+escapetag+"$"+ str + "$"+escapetag+"$" ;
  }

  public static boolean existsTable(String tablename) throws SQLException {
    if(existingTablenames == null){
      existingTablenames = new HashSet<>();
      DatabaseMetaData md = connection.getMetaData();
      ResultSet rs = md.getTables(null, null, "%", null);
      while (rs.next()) {
        existingTablenames.add(rs.getString(3).toLowerCase());
      }
    }
    return (existingTablenames.contains(tablename.toLowerCase()));
  }

  /**
   * Queries the SQL tables for the count of the phrase.
   * Returns -1 if the phrase doesn't exist
   * @param str : phrase
   * @return : count, if exists. -1 if not.
   * @throws SQLException
   */
  public static long getCount(String str) {
    String query = null;
    try{
    connect();
    str = str.trim();
    if(str.contains("'")){
      str = StringUtils.escapeString(str, new char[]{'\''},'\'');
    }

    int ngram = str.split("\\s+").length;
    String table = tablenamePrefix + ngram;

    if(!existsTable(table))
      return -1;

    String phrase = escapeString(str);

    query = "select count from " + table + " where phrase='" + phrase+"';";
    Statement stmt = connection.createStatement();
    ResultSet result = stmt.executeQuery(query);
    if(result.next()){
      return result.getLong("count");
    }else
      return -1;
    }catch(SQLException e){
      log.info("Error getting count for " + str+ ". The query was " + query);
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public static List<Pair<String, Long>> getCounts(Collection<String> strs) throws SQLException {
    connect();
    List<Pair<String, Long>> counts = new ArrayList<>();
    String query = "";
    for(String str: strs) {
      str = str.trim();
      int ngram = str.split("\\s+").length;
      String table = tablenamePrefix + ngram;
      if (!existsTable(table)){
        counts.add(new Pair(str, (long) -1));
        continue;
      }
      String phrase = escapeString(str);
      query += "select count from " + table + " where phrase='" + phrase + "';";
    }

    if(query.isEmpty())
      return counts;

    PreparedStatement stmt = connection.prepareStatement(query);
    boolean isresult = stmt.execute();
    ResultSet rs;
    Iterator<String> iter = strs.iterator();
    do {
      rs = stmt.getResultSet();
      String ph = iter.next();

      if (rs.next()) {
        counts.add(new Pair(ph, rs.getLong("count")));
      } else
        counts.add(new Pair(ph, (long) -1));

      isresult = stmt.getMoreResults();
    } while (isresult);


    assert(counts.size() == strs.size());
    return counts;
  }

  //Adding google ngrams to the tables for the first time
  public static void populateTablesInSQL(String dir, Collection<Integer> typesOfPhrases) throws SQLException{
    connect();
    Statement stmt = connection.createStatement();

    for(Integer n: typesOfPhrases) {
      String table = tablenamePrefix + n;

      if(!existsTable(table))
        throw new RuntimeException("Table " + table + " does not exist in the database! Run the following commands in the psql prompt:" +
          "create table GoogleNgrams_<NGRAM> (phrase text primary key not null, count bigint not null); create index phrase_<NGRAM> on GoogleNgrams_<NGRAM>(phrase);");

      for(String line: IOUtils.readLines(new File(dir + "/" + n + "gms/vocab_cs.gz"), GZIPInputStream.class)){
        String[] tok = line.split("\t");
        String q = "INSERT INTO " + table + " (phrase, count) VALUES (" + escapeString(tok[0]) +" , " + tok[1]+");";
        stmt.execute(q);
      }
    }
  }

  /** Note that this is really really slow for ngram > 1
   * TODO: make this fast (if we had been using mysql we could have)
   * **/
  public static int getTotalCount(int ngram){
    try{
      connect();
      Statement stmt = connection.createStatement();
      String table = tablenamePrefix + ngram;
      String q = "select count(*) from " + table+";";
      ResultSet s = stmt.executeQuery(q);
      if(s.next()){
        return s.getInt(1);
      } else
        throw new RuntimeException("getting table count is not working!");
    }
    catch(SQLException e){
      throw new RuntimeException("getting table count is not working! " + e);
    }
  }

  /** Return rank of 1 gram in google ngeams if it is less than 20k. Otherwise -1. */
  public static int get1GramRank(String str) {
    String query = null;
    try{
      connect();
      str = str.trim();
      if(str.contains("'")){
        str = StringUtils.escapeString(str, new char[]{'\''},'\'');
      }

      int ngram = str.split("\\s+").length;
      if(ngram > 1)
        return -1;
      String table =  "googlengrams_1_ranked20k";

      if(!existsTable(table))
        return -1;

      String phrase = escapeString(str);

      query = "select rank from " + table + " where phrase='" + phrase+"';";
      Statement stmt = connection.createStatement();
      ResultSet result = stmt.executeQuery(query);
      if(result.next()){
        return result.getInt("rank");
      }else
        return -1;
    }catch(SQLException e){
      log.info("Error getting count for " + str+ ". The query was " + query);
      e.printStackTrace();
      throw new RuntimeException(e);
    }

  }

  static public void closeConnection() throws SQLException {
    if(connection != null)
      connection.close();
    connection = null;
  }

  public static void main(String[] args){
    try{
      Properties props = StringUtils.argsToPropertiesWithResolve(args);
      ArgumentParser.fillOptions(GoogleNGramsSQLBacked.class, props);

      connect();

      //if(populateTables)
      //  populateTablesInSQL(dataDir, ngramsToPopulate);

      //testing
      System.out.println("For head,the count is " + getCount("head"));
      //System.out.println(getCount("what the heck"));
      //System.out.println(getCount("my name is john"));

      System.out.println(getCounts(Arrays.asList("cancer","disease")));
      System.out.println("Get count 1 gram " + getTotalCount(1));

      if(props.getProperty("phrase") != null) {
        String p = props.getProperty("phrase");
        System.out.println("count for phrase " + p + " is " + getCount(p));
      }

      if(props.getProperty("rank") != null){
        String p = props.getProperty("rank");
        System.out.println("Rank of " + p+ " is " + get1GramRank(p));
      }
      closeConnection();

    }catch(Exception e){
      e.printStackTrace();
    }
  }

  public static void setDBName(String DBName) {
    googleNgram_dbname = DBName;
  }


}
