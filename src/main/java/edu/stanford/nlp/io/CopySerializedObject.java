package edu.stanford.nlp.io;

import java.io.IOException;

/**
 * Copies a serialized object from one file to another.
 * <br>
 * Why bother?  In case you need to change the format of the
 * serialized object, so you implement readObject() to handle the old
 * object and want to update existing models instead of retraining
 * them.  I've had to write this program so many times that it seemed
 * worthwhile to just check it in.
 *
 * @author John Bauer
 */
public class CopySerializedObject {
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    Object o = IOUtils.readObjectFromFile(args[0]);
    IOUtils.writeObjectToFile(o, args[1]);
  }
}
