package edu.stanford.nlp.ie;

import java.io.*;

/* Useful for making command line calls through a Java program.
 *  @author Vijay Krishnan
 */

public class CommandLineCall extends Thread {
  InputStream s;

  String n;

  CommandLineCall(InputStream s, String n) {
    this.s = s;
    this.n = n;
  }

  @Override
  public void run() {
    try {
      BufferedReader r = new BufferedReader(new InputStreamReader(s));
      String l;
      while ((l = r.readLine()) != null)
        System.out.println(n + "> " + l);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void execute(String cmd) throws IOException,
      InterruptedException {
    System.err.println("Executing: " + cmd);
    Process proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});
    CommandLineCall errGobbler = new CommandLineCall(proc.getErrorStream(), "ERR");
    CommandLineCall outGobbler = new CommandLineCall(proc.getInputStream(), "OUT");
    errGobbler.start();
    outGobbler.start();
    int exitVal = proc.waitFor();
    if (exitVal != 0) {
      System.err.println("Command `" + cmd + "` failed with exit value: "
          + exitVal);
      System.exit(exitVal);
    }
  }

}
