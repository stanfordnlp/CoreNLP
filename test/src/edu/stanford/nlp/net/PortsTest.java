package edu.stanford.nlp.net;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PortsTest {
  /*
   * rfc6335 defines valid ports as 16-bit unsigned integers
   */
  @Test(expected = IllegalArgumentException.class)
  public void testLowerBoundary() {
    assertTrue(Ports.available(-1));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpperBoundary() {
    assertTrue(Ports.available(66000));
  }
  
  /*
   * Port 0 is a reserved port. It indicates that an arbitrary port open port
   * shall be used. Thus, requesting a ServerSocket for port 0 will not throw
   * an error but yield a Socket with an open port likely in the "User Port
   * Range".
   */
  @Test(expected = IllegalArgumentException.class)
  public void testReservedPorts() {
    assertTrue(Ports.available(0));
  }

  @Test
  public void testRepeatability() {
    int portToTest = 41463;
    boolean available = Ports.available(portToTest);
    boolean stillAvailable = Ports.available(portToTest);
    assertTrue("Port should be freed if available and running the same check should not be subject to race conditions",
               available == stillAvailable);
  }


}
