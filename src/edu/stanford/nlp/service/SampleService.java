package edu.stanford.nlp.service;

import java.io.IOException;

import edu.stanford.cs.ra.util.IOUtils;
import edu.stanford.nlp.util.Function;

/**
 * Sample service .. returns the uppercase version of an input string.
 * 
 * @author dramage
 */
public class SampleService implements Function<String,String> {
  
  /** Sample processing method .. just return upper case */
  public String apply(String request) {
    return request.toUpperCase();
  }
  
  /**
   * Invokes as service through the registry and tests the connection.
   * Can also do connections direct by using SocketService and SocketClient.
   */
  public static void main(String[] args) throws IOException {
    ServiceRegistry registry = new ServiceRegistry();
    
    if (!registry.hasService(SampleService.class.getName())) {
      System.err.println("Running SampleService - run me again in " +
      		"a new terminal to connect ...");
      new Thread(registry.registerSocketService(new SampleService())).run();
    } else {
      Function<String,String> session =
        registry.getService(SampleService.class.getName());
      System.err.println("Connected - type some lines and then press ^d - " +
      		"the service conver them to upper case");
      
      for (String line : IOUtils.readLines(System.in)) {
        System.out.println(session.apply(line));
      }
      
      registry.stopService(SampleService.class.getName());
    }
  }
}
