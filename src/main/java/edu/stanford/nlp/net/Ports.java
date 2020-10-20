package edu.stanford.nlp.net;

import java.io.*;
import java.net.*;

/**
 * Contains a couple useful utility methods related to networks.  For
 * example, contains a method which checks if a port is available, and
 * contains a method which scans a range of ports until it finds an
 * available port.
 * <br>
 * @author John Bauer
 */
public class Ports {
  /**
   * Checks to see if a specific port is available.
   * <br>
   * Source: Apache's mina project, via stack overflow
   *  http://stackoverflow.com/questions/434718/
   *    sockets-discover-port-availability-using-java
   *
   * @param port the port to check for availability
   */
  public static boolean available(int port) {
    ServerSocket ss = null;
    DatagramSocket ds = null;
    try {
      ss = new ServerSocket(port);
      ss.setReuseAddress(true);
      ds = new DatagramSocket(port);
      ds.setReuseAddress(true);
      return true;
    } catch (IOException e) {
    } finally {
      if (ds != null) {
        ds.close();
      }
      
      if (ss != null) {
        try {
          ss.close();
        } catch (IOException e) {
          // should not be thrown
        }
      }
    }
    
    return false;
  }

  /**
   * Scan a range of ports to find the first available one.
   */
  public static int findAvailable(int min, int max) {
    for (int port = min; port < max; ++port) {
      if (available(port)) {
        return port;
      }
    }
    return max;
  }
}

