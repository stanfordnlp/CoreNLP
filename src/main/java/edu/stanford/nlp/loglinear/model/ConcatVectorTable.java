package edu.stanford.nlp.loglinear.model;

import edu.stanford.nlp.loglinear.model.proto.ConcatVectorTableProto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * Created on 8/9/15.
 * @author keenon
 * <p>
 * This is basically a type specific wrapper over NDArray
 */
public class ConcatVectorTable extends NDArray<Supplier<ConcatVector>> {
  /**
   * Constructor takes a list of neighbor variables to use for this factor. This must not change after construction,
   * and the number of states of those variables must also not change.
   *
   * @param dimensions list of neighbor variables assignment range sizes
   */
  public ConcatVectorTable(int[] dimensions) {
    super(dimensions);
  }

  /**
   * Convenience function to write this factor directly to a stream, encoded as proto. Reversible with readFromStream.
   *
   * @param stream the stream to write to. does not flush automatically
   * @throws IOException passed through from the stream
   */
  public void writeToStream(OutputStream stream) throws IOException {
    getProtoBuilder().build().writeTo(stream);
  }

  /**
   * Convenience function to read a factor (assumed serialized with proto) directly from a stream.
   *
   * @param stream the stream to be read from
   * @return a new in-memory feature factor
   * @throws IOException passed through from the stream
   */
  public static ConcatVectorTable readFromStream(InputStream stream) throws IOException {
    return readFromProto(ConcatVectorTableProto.ConcatVectorTable.parseFrom(stream));
  }

  /**
   * Returns the proto builder object for this feature factor. Recursively constructs protos for all the concat
   * vectors in factorTable.
   *
   * @return proto Builder object
   */
  public ConcatVectorTableProto.ConcatVectorTable.Builder getProtoBuilder() {
    ConcatVectorTableProto.ConcatVectorTable.Builder b = ConcatVectorTableProto.ConcatVectorTable.newBuilder();
    for (int n : getDimensions()) {
      b.addDimensionSize(n);
    }
    for (int[] assignment : this) {
      b.addFactorTable(getAssignmentValue(assignment).get().getProtoBuilder());
    }
    return b;
  }

  /**
   * Creates a new in-memory feature factor from a proto serialization,
   *
   * @param proto the proto object to be turned into an in-memory feature factor
   * @return an in-memory feature factor, complete with in-memory concat vectors
   */
  public static ConcatVectorTable readFromProto(ConcatVectorTableProto.ConcatVectorTable proto) {
    int[] neighborSizes = new int[proto.getDimensionSizeCount()];
    for (int i = 0; i < neighborSizes.length; i++) {
      neighborSizes[i] = proto.getDimensionSize(i);
    }
    ConcatVectorTable factor = new ConcatVectorTable(neighborSizes);
    int i = 0;
    for (int[] assignment : factor) {
      final ConcatVector vector = ConcatVector.readFromProto(proto.getFactorTable(i));
      factor.setAssignmentValue(assignment, () -> vector);
      i++;
    }
    return factor;
  }

  /**
   * Deep comparison for equality of value, plus tolerance, for every concatvector in the table, plus dimensional
   * arrangement. This is mostly useful for testing.
   *
   * @param other     the vector table to compare against
   * @param tolerance the tolerance to use in value comparisons
   * @return whether the two tables are equivalent by value
   */
  public boolean valueEquals(ConcatVectorTable other, double tolerance) {
    if (!Arrays.equals(other.getDimensions(), getDimensions())) return false;
    for (int[] assignment : this) {
      if (!getAssignmentValue(assignment).get().valueEquals(other.getAssignmentValue(assignment).get(), tolerance)) {
        return false;
      }
    }
    return true;
  }

  NDArray<Supplier<ConcatVector>> originalThunks = null;

  /**
   * This is an optimization that will fault all the ConcatVectors into memory, and future .get() on the Supplier objs
   * will result in a very fast return by reference. Basically this works by wrapping the output of the old thunks
   * inside new, thinner closures that carry around the answer in memory. This is a no-op if vectors were already
   * cached.
   */
  public void cacheVectors() {
    if (originalThunks != null) return;

    originalThunks = new NDArray<>(getDimensions());

    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.
    Iterator<int[]> fastPassByReferenceIterator = fastPassByReferenceIterator();
    int[] assignment = fastPassByReferenceIterator.next();
    while (true) {
      Supplier<ConcatVector> originalThunk = getAssignmentValue(assignment);
      originalThunks.setAssignmentValue(assignment, originalThunk);

      // Construct a new, thinner closure around the cached value
      ConcatVector result = originalThunk.get();
      setAssignmentValue(assignment, () -> result);

      // Set the assignment arrays correctly
      if (fastPassByReferenceIterator.hasNext()) fastPassByReferenceIterator.next();
      else break;
    }
  }

  /**
   * This will release references to the cached ConcatVectors created by cacheVectors(), so that they can be cleaned
   * up by the GC. If no cache was constructed, this is a no-op.
   */
  public void releaseCache() {
    if (originalThunks != null) {
      // OPTIMIZATION:
      // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
      // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.
      Iterator<int[]> fastPassByReferenceIterator = fastPassByReferenceIterator();
      int[] assignment = fastPassByReferenceIterator.next();
      while (true) {
        setAssignmentValue(assignment, originalThunks.getAssignmentValue(assignment));

        // Set the assignment arrays correctly
        if (fastPassByReferenceIterator.hasNext()) fastPassByReferenceIterator.next();
        else break;
      }
      // Release our replicated set of original thunks
      originalThunks = null;
    }
  }

  /**
   * Clones the table, but keeps the values by reference.
   *
   * @return a new NDArray, a perfect replica of this one
   */
  public ConcatVectorTable cloneTable() {
    ConcatVectorTable copy = new ConcatVectorTable(getDimensions().clone());
    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.
    Iterator<int[]> fastPassByReferenceIterator = fastPassByReferenceIterator();
    int[] assignment = fastPassByReferenceIterator.next();
    while (true) {
      copy.setAssignmentValue(assignment, getAssignmentValue(assignment));
      // Set the assignment arrays correctly
      if (fastPassByReferenceIterator.hasNext()) fastPassByReferenceIterator.next();
      else break;
    }
    return copy;
  }
}

