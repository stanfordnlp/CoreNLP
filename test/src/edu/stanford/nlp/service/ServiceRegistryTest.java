package edu.stanford.nlp.service;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

/**
 * Tests that we can read and write a registry.
 *
 * @author dramage
 */
public class ServiceRegistryTest extends TestCase {

  public void testServiceRegistry() throws IOException {
    File file = File.createTempFile("bob", "");
    file.delete();
    ServiceRegistry registry = new ServiceRegistry(file);
    assertFalse(registry.hasService(SampleService.class.getName()));
    new Thread(registry.registerSocketService(new SampleService())).start();
    assertTrue(registry.hasService(SampleService.class.getName()));
    registry.registerSocketService(new SampleService());
    assertEquals(registry.getService(SampleService.class.getName()).apply("hi"), "HI");
    file.delete();
  }
}
