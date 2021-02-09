package edu.stanford.nlp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;


public class MemoryMonitorTest  {


  @Test
  public void testMBRepresentationOfMaxMemory() {
    Runtime mockedRuntime = mock(Runtime.class);
    when(mockedRuntime.maxMemory()).thenReturn(16 * 1000l * 1024l);

    MemoryMonitor m = new MemoryMonitor(10, mockedRuntime);

    int k = m.getMaxAvailableMemory();
    assertEquals(16000, k);
  }

  @Test
  public void testGetMemoryUsed() {
    Runtime mockedRuntime = mock(Runtime.class);
    when(mockedRuntime.totalMemory()).thenReturn(16 * 1000l * 1024l);
    when(mockedRuntime.freeMemory()).thenReturn(15 * 1000l * 1024l);

    MemoryMonitor m = new MemoryMonitor(10, mockedRuntime);
    
    int k = m.getUsedMemory();
    assertEquals(1000, k);
  }
  @Test
  public void testGetMemoryUsedStringKB() {
    Runtime mockedRuntime = mock(Runtime.class);
    when(mockedRuntime.totalMemory()).thenReturn(2 * 1024l);
    when(mockedRuntime.freeMemory()).thenReturn(1024l);

    String k = MemoryMonitor.getUsedMemoryString(mockedRuntime);
    assertEquals("1k", k);
  }
  @Test
  public void testGetMemoryUsedStringMB() {
    Runtime mockedRuntime = mock(Runtime.class);
    when(mockedRuntime.totalMemory()).thenReturn(2 * 1024l * 1024l);
    when(mockedRuntime.freeMemory()).thenReturn(1024l * 1024l);

    String k = MemoryMonitor.getUsedMemoryString(mockedRuntime);
    assertEquals("1m", k);
  }
}
