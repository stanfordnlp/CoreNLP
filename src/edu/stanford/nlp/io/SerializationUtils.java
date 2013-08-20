package edu.stanford.nlp.io;

import edu.stanford.nlp.util.ErasureUtils;

import java.io.IOException;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.ByteBuffer;

/**
 * Functions for maninulating files containing serialized objects.
 *
 * @author Michel Galley
 */
public class SerializationUtils {

  public static final boolean VERBOSE = false;

  public static void main(String[] args) throws Exception {

    for(int i=0; i<args.length; ++i) {
      if(args[i].equalsIgnoreCase("-renameClass")) {
        String[] renameArgs = args[++i].split(":");
        String srcFile = renameArgs[0];
        String tgtFile = renameArgs[1];
        String oldName = renameArgs[2];
        String newName = renameArgs[3];
        Long newUID = renameArgs.length == 5 ? Long.parseLong(renameArgs[4]) : null;
        refactorSerializedFile(srcFile, tgtFile, oldName, newName, newUID);
      } else {
        throw new UnsupportedOperationException("Unknown operation: "+args[i]);
      }
    }
  }


  private static final int MAX_CLASS_NAME_LEN = 512;

  /**
   * This function edits a serialized file by renaming all instances of an
   * old class to a new class. The assumption is that the old
   * and new classes contain the same data fields, and essentially only differ
   * by their names (perhaps as a result of refactoring, e.g., the historical
   * move from Index to HashIndex in JavaNLP).
   *
   * If serialVersionUID is not null, its value is used to modify the
   * serialVersionUID field of each serialized object that is affected by the name
   * change.
   *
   * This function is still experimental.
   *
   * @param inFileName Input serialized file.
   * @param outFileName New serialized file (which contains new class name).
   * @param oldName Old class name.
   * @param newName New class name.
   * @param serialVersionUID (if null, each serialized object keeps its old serialVersionUID).
   * @throws java.io.IOException
   */
  public static void refactorSerializedFile
         (String inFileName, String outFileName,
          String oldName, String newName, Long serialVersionUID)
     throws IOException {

    BufferedInputStream in = new BufferedInputStream
         (inFileName.endsWith(".gz") ?
          new GZIPInputStream(new FileInputStream(inFileName)) :
          new FileInputStream(inFileName), MAX_CLASS_NAME_LEN);
    BufferedOutputStream out = new BufferedOutputStream
         (outFileName.endsWith(".gz") ?
              new GZIPOutputStream(new FileOutputStream(outFileName)) :
              new FileOutputStream(outFileName));
    System.err.println("Input file: "+inFileName);
    System.err.println("Output file: "+outFileName);

    StringBuilder sb = new StringBuilder();
    byte[] id = new byte[8];

    while (in.available() > 0) {
      // Try to match an UTF string: (if not a string, need to backtrack to the mark)
      in.mark(3);
      // Read string length (short):
      int s1 = in.read();
      int s2 = in.read();
      int s = (s1 << 8) + s2;
      // Read first character:
      char c = (char)(in.read() & 0xFF);

      if(Character.isLetter(c) && s > 0 && s <= MAX_CLASS_NAME_LEN) {
        boolean isJavaString = true;
        if(VERBOSE)
          System.err.printf("possible string of length %d.\n", s);
        sb.setLength(0);
        in.reset();
        in.mark(s+2);
        ErasureUtils.noop(in.read());
        ErasureUtils.noop(in.read());
        for(int i=0; i<s; ++i) {
          c = (char)(in.read() & 0xFF);
          if(!Character.isLetterOrDigit(c) && c != '.') {
            isJavaString = false;
            break;
          }
          sb.append(c);
        }
        if(isJavaString) {
          if(sb.toString().equals(oldName)) {

            // Write size, string, and UID:
            System.err.printf("renamed class: %s (%d) -> %s (%d)\n",
               oldName, oldName.length(), newName, newName.length());
            ByteBuffer b = ByteBuffer.allocate(newName.length()+10);
            b.putShort((short)newName.length());
            b.put(newName.getBytes());

            // Read old UID:
            ByteBuffer oldb = ByteBuffer.allocate(8);
            if(in.read(id) != 8)
              throw new RuntimeException("couldn't read full UID");
            oldb.mark();
            oldb.put(id);
            oldb.reset();

            // Write new UID:
            b.mark();
            if(serialVersionUID != null) {
              b.putLong(serialVersionUID);
            } else {
              b.put(id);
            }
            out.write(b.array());
            b.reset();
            long oldUID = oldb.getLong();
            long newUID = b.getLong();
            if(oldUID != newUID)
              System.err.printf("changed serial version UID: %d -> %d\n", oldUID, newUID);
            continue;
          }
        }
      }

      // Failed to match a well-formed Java UTF string:
      in.reset();
      try {
        out.write(in.read());
      } catch(EOFException e) {
        throw new RuntimeException(e);
      }
    }
    in.close();
    out.close();
  }
}
