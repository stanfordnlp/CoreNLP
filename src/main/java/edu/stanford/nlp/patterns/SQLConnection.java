package edu.stanford.nlp.patterns;

import edu.stanford.nlp.util.ArgumentParser.Option;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Created by sonalg on 10/8/14.
 */
public class SQLConnection {

  @Option(name="dbLocation",required=true)
  public static String dbLocation;

  @Option(name="dbusername",required=true)
  public static String dbusername;

  @Option(name="dbpassword",required=true)
  public static String dbpassword;

  @Option(name="host",required = true)
  public static String host;

  public static Connection getConnection() throws SQLException {

    //System.out.println("username is " + dbusername + " and location is " + dbLocation);
    return DriverManager.getConnection(dbLocation + "?host="+host+ "&user="
      + dbusername + "&password=" + dbpassword + "&characterEncoding=utf-8&"
      + "useUnicode=true");
  }

}
