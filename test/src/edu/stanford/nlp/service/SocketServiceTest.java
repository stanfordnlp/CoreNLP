package edu.stanford.nlp.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

import junit.framework.TestCase;

import edu.stanford.nlp.util.Function;

/**
 * Tests invoking the SampleService using all transports.
 * 
 * @author dramage
 */
public class SocketServiceTest extends TestCase {
  
  /** port to connect on */
  static final int port = 1976;
  
  /** localhost, initialized in static block */
  static final InetAddress localhost;
  
  static {
    try {
      localhost = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }
  
  private static class ExceptionService implements Function<String,String> {
    static final String message = "Gah!";
    public String apply(String request) {
      throw new RuntimeException(message);
    }
  }

  /** Tests that we get exceptions */
  public void testException() {
    SocketService service = new SocketService(new ExceptionService(), port);
    new Thread(service).start();
    
    SocketClient<String,String> client = SocketClient.connect(localhost, port);
    try {
      client.apply("hi");
      assertTrue("Should have thrown exception", false);
    } catch (RuntimeException e) {
      assertTrue("Wrong exception", e.getMessage().equals(ExceptionService.message));
    }
    client.disconnect();
    
    service.stop();
  }
  
  /**
   * Tests running SampleService over the socket layer.
   */
  public void testSocketService() {
    final int PORT = 9090;
    
    // SocketService thread
    SocketService service = new SocketService(new SampleService(), PORT);
    new Thread(service).start();
    
    // SocketClient connection
    SocketClient<String,String> client = SocketClient.connect(localhost, PORT);
    assertEquals(client.apply("hi"),"HI");
    assertEquals(client.apply("woot!"),"WOOT!");
    client.disconnect();
    
    service.stop();
  }

}
