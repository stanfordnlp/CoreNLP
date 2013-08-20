package edu.stanford.nlp.math.mtj;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.io.MatrixVectorReader;
import no.uib.cipr.matrix.sparse.CompColMatrix;
import no.uib.cipr.matrix.sparse.CompRowMatrix;
import edu.stanford.cs.ra.stringify.Stringify;
import edu.stanford.cs.ra.util.IOUtils;

/**
 * Adds FromString to automatically load various types of matrices from files.
 * 
 * @author dramage
 */
public class MatrixIO {

  @Stringify.StaticFromString
  public static CompColMatrix loadCompColMatrix(File file) {
    MatrixVectorReader reader = new MatrixVectorReader(
        new InputStreamReader(IOUtils.openFile(file)));
    try {
      return new CompColMatrix(reader);
    } catch (IOException e) {
      throw new IOUtils.QuietIOException(e);
    }
  }

  @Stringify.StaticFromString
  public static DenseMatrix loadDenseMatrix(File file) {
    MatrixVectorReader reader = new MatrixVectorReader(
        new InputStreamReader(IOUtils.openFile(file)));
    try {
      return new DenseMatrix(reader);
    } catch (IOException e) {
      throw new IOUtils.QuietIOException(e);
    }
  }

  @Stringify.StaticFromString
  public static CompRowMatrix loadCompRowMatrix(File file) {
    MatrixVectorReader reader = new MatrixVectorReader(
        new InputStreamReader(IOUtils.openFile(file)));
    try {
      return new CompRowMatrix(reader);
    } catch (IOException e) {
      throw new IOUtils.QuietIOException(e);
    }
  }
  
}
