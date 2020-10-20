package edu.stanford.nlp.io;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.stanford.nlp.util.StringUtils;

import org.junit.Assert;
import junit.framework.TestCase;

public class IOUtilsTest extends TestCase {

  private String dirPath;
  private File dir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    dir = File.createTempFile("IOUtilsTest", ".dir");
    assertTrue(dir.delete());
    assertTrue(dir.mkdir());
    dirPath = dir.getAbsolutePath();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    this.delete(this.dir);
  }

  public void testReadWriteStreamFromString() throws IOException, ClassNotFoundException {
    ObjectOutputStream oos = IOUtils.writeStreamFromString(dirPath + "/objs.obj");
    oos.writeObject(Integer.valueOf(42));
    oos.writeObject("forty two");
    oos.close();
    ObjectInputStream ois = IOUtils.readStreamFromString(dirPath + "/objs.obj");
    Object i = ois.readObject();
    Object s = ois.readObject();
    Assert.assertTrue(Integer.valueOf(42).equals(i));
    Assert.assertTrue("forty two".equals(s));
    ois.close();
  }

  public void testReadLines() throws Exception {
    File file = new File(this.dir, "lines.txt");
    Iterable<String> iterable;

    write("abc", file);
    iterable = IOUtils.readLines(file);
    Assert.assertEquals("abc", StringUtils.join(iterable, "!"));
    Assert.assertEquals("abc", StringUtils.join(iterable, "!"));

    write("abc\ndef\n", file);
    iterable = IOUtils.readLines(file);
    Assert.assertEquals("abc!def", StringUtils.join(iterable, "!"));
    Assert.assertEquals("abc!def", StringUtils.join(iterable, "!"));

    write("\na\nb\n", file);
    iterable = IOUtils.readLines(file.getPath());
    Assert.assertEquals("!a!b", StringUtils.join(iterable, "!"));
    Assert.assertEquals("!a!b", StringUtils.join(iterable, "!"));

    write("", file);
    iterable = IOUtils.readLines(file);
    Assert.assertFalse(iterable.iterator().hasNext());

    write("\n", file);
    iterable = IOUtils.readLines(file.getPath());
    Iterator<String> iterator = iterable.iterator();
    Assert.assertTrue(iterator.hasNext());
    iterator.next();

    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
        new GZIPOutputStream(new FileOutputStream(file))));
    writer.write("\nzipped\ntext\n");
    writer.close();
    iterable = IOUtils.readLines(file, GZIPInputStream.class);
    Assert.assertEquals("!zipped!text", StringUtils.join(iterable, "!"));
    Assert.assertEquals("!zipped!text", StringUtils.join(iterable, "!"));
  }

  private static void checkLineIterable(boolean includeEol) throws IOException {
    String[] expected = {
            "abcdefhij\r\n",
            "klnm\r\n",
            "opqrst\n",
            "uvwxyz\r",
            "I am a longer line than the rest\n",
            "12345"
    };
    String testString = StringUtils.join(expected, "");

    Reader reader = new StringReader(testString);
    int i = 0;
    Iterable<String> iterable = IOUtils.getLineIterable(reader, 10, includeEol);
    for (String line:iterable) {
      String expLine = expected[i];
      if (!includeEol) expLine = expLine.replaceAll("\\r|\\n", "");
      assertEquals("Checking line " + i, expLine, line);
      i++;
    }
    assertEquals("Check got all lines", expected.length, i);
    IOUtils.closeIgnoringExceptions(reader);
  }

  public void testLineIterableWithEol() throws IOException {
    checkLineIterable(true);
  }

  public void testLineIterableWithoutEol() throws IOException {
    checkLineIterable(false);
  }

  public void testIterFilesRecursive() throws IOException {
    File dir = new File(this.dir, "recursive");
    File a = new File(dir, "x/a");
    File b = new File(dir, "x/y/b.txt");
    File c = new File(dir, "c.txt");
    File d = new File(dir, "dtxt");

    write("A", a);
    write("B", b);
    write("C", c);
    write("D", d);

    Set<File> actual = toSet(IOUtils.iterFilesRecursive(dir));
    Assert.assertEquals(toSet(Arrays.asList(a, b, c, d)), actual);

    actual = toSet(IOUtils.iterFilesRecursive(dir, ".txt"));
    Assert.assertEquals(toSet(Arrays.asList(b, c)), actual);

    actual = toSet(IOUtils.iterFilesRecursive(dir, Pattern.compile(".txt")));
    Assert.assertEquals(toSet(Arrays.asList(b, c, d)), actual);
  }

  protected void delete(File file) {
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          this.delete(child);
        }
      }
    }
    // Use an Assert here to make sure that all files were closed properly
    Assert.assertTrue(file.delete());
  }

  protected static void write(String text, File file) throws IOException {
    if (!file.getParentFile().exists()) {
      //noinspection ResultOfMethodCallIgnored
      file.getParentFile().mkdirs();
    }
    FileWriter writer = new FileWriter(file);
    writer.write(text);
    writer.close();
  }

  private static <E> Set<E> toSet(Iterable<E> iter) {
    Set<E> set = new HashSet<E>();
    for (E item: iter) {
      set.add(item);
    }
    return set;
  }
  public void testCpSourceFileTargetNotExists() throws IOException {
    File source = File.createTempFile("foo", ".file");
    IOUtils.writeStringToFile("foo", source.getPath(), "utf-8");
    File dst = File.createTempFile("foo", ".file");
    assertTrue(dst.delete());
    IOUtils.cp(source, dst);
    assertEquals("foo", IOUtils.slurpFile(dst));
    assertTrue( source.delete() );
    assertTrue( dst.delete() );
  }

  public void testCpSourceFileTargetExists() throws IOException {
    File source = File.createTempFile("foo", ".file");
    IOUtils.writeStringToFile("foo", source.getPath(), "utf-8");
    File dst = File.createTempFile("foo", ".file");
    IOUtils.cp(source, dst);
    assertEquals("foo", IOUtils.slurpFile(dst));
    assertTrue( source.delete() );
    assertTrue( dst.delete() );
  }

  public void testCpSourceFileTargetIsDir() throws IOException {
    File source = File.createTempFile("foo", ".file");
    IOUtils.writeStringToFile("foo", source.getPath(), "utf-8");
    File dst = File.createTempFile("foo", ".file");
    assertTrue(dst.delete());
    assertTrue(dst.mkdir());
    IOUtils.cp(source, dst);
    assertEquals("foo", IOUtils.slurpFile(dst.getPath() + File.separator + source.getName()));
    assertTrue( source.delete() );
    assertTrue( new File(dst.getPath() + File.separator + source.getName()).delete() );
    assertTrue( dst.delete() );
  }

  public void testCpSourceDirTargetNotExists() throws IOException {
    // create source
    File sourceDir = File.createTempFile("foo", ".file");
    assertTrue( sourceDir.delete() );
    assertTrue( sourceDir.mkdir() );
    File foo = new File(sourceDir + File.separator + "foo");
    IOUtils.writeStringToFile("foo", foo.getPath(), "utf-8");

    // create destination
    File dst = File.createTempFile("foo", ".file");
    assertTrue(dst.delete());

    // copy
    IOUtils.cp(sourceDir, dst, true);
    assertEquals("foo", IOUtils.slurpFile(dst.getPath() + File.separator + "foo"));

    // clean up
    assertTrue( foo.delete() );
    assertTrue( sourceDir.delete() );
    assertTrue( new File(dst.getPath() + File.separator + "foo").delete() );
    assertTrue( dst.delete() );
  }

  public void testCpSourceDirTargetIsDir() throws IOException {
    // create source
    File sourceDir = File.createTempFile("foo", ".file");
    assertTrue( sourceDir.delete() );
    assertTrue( sourceDir.mkdir() );
    File foo = new File(sourceDir + File.separator + "foo");
    IOUtils.writeStringToFile("foo", foo.getPath(), "utf-8");

    // create destination
    File dst = File.createTempFile("foo", ".file");
    assertTrue( dst.delete() );
    assertTrue( dst.mkdir() );

    // copy
    IOUtils.cp(sourceDir, dst, true);
    assertEquals("foo", IOUtils.slurpFile(dst.getPath() + File.separator + sourceDir.getName() + File.separator + "foo"));

    // clean up
    assertTrue( foo.delete() );
    assertTrue( sourceDir.delete() );
    assertTrue( new File(dst.getPath() + File.separator + sourceDir.getName() + File.separator + "foo").delete() );
    assertTrue( new File(dst.getPath() + File.separator + sourceDir.getName()).delete() );
    assertTrue( dst.delete() );
  }

  public void testCpRecursive() throws IOException {
    // create source
    // d1/
    //   d2/
    //     foo
    //   bar
    File sourceDir = File.createTempFile("directory", ".file");
    assertTrue( sourceDir.delete() );
    assertTrue( sourceDir.mkdir() );
    File sourceSubDir = new File(sourceDir + File.separator + "d2");
    assertTrue( sourceSubDir.mkdir() );
    File foo = new File(sourceSubDir + File.separator + "foo");
    IOUtils.writeStringToFile("foo", foo.getPath(), "utf-8");
    File bar = new File(sourceDir + File.separator + "bar");
    IOUtils.writeStringToFile("bar", bar.getPath(), "utf-8");

    // create destination
    File dst = File.createTempFile("dst", ".file");
    assertTrue( dst.delete() );

    // copy
    IOUtils.cp(sourceDir, dst, true);
    assertEquals("foo", IOUtils.slurpFile(dst + File.separator + "d2" + File.separator + "foo"));
    assertEquals("bar", IOUtils.slurpFile(dst + File.separator + "bar"));

    // clean up
    assertTrue( foo.delete() );
    assertTrue( bar.delete() );
    assertTrue( sourceSubDir.delete() );
    assertTrue( sourceDir.delete() );
    assertTrue( new File(dst + File.separator + "d2" + File.separator + "foo").delete() );
    assertTrue( new File(dst + File.separator + "d2").delete() );
    assertTrue( new File(dst + File.separator + "bar").delete() );
    assertTrue( dst.delete() );
  }

  public void testTail() throws IOException {
    File f = File.createTempFile("totail", ".file");
    // Easy case
    IOUtils.writeStringToFile("line 1\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7", f.getPath(), "utf-8");
    assertEquals("line 7", IOUtils.tail(f, 1)[0]);
    assertEquals("line 6", IOUtils.tail(f, 2)[0]);
    assertEquals("line 7", IOUtils.tail(f, 2)[1]);
    // Hard case
    IOUtils.writeStringToFile("line 1\nline 2\n\nline 3\n", f.getPath(), "utf-8");
    assertEquals("", IOUtils.tail(f, 1)[0]);
    assertEquals("", IOUtils.tail(f, 3)[0]);
    assertEquals("line 3", IOUtils.tail(f, 3)[1]);
    assertEquals("", IOUtils.tail(f, 3)[2]);
    // Too few lines
    IOUtils.writeStringToFile("line 1\nline 2", f.getPath(), "utf-8");
    assertEquals(0, IOUtils.tail(f, 0).length);
    assertEquals(1, IOUtils.tail(f, 1).length);
    assertEquals(2, IOUtils.tail(f, 3).length);
    assertEquals(2, IOUtils.tail(f, 2).length);
    // UTF-reading
    IOUtils.writeStringToFile("↹↝\n۝æ", f.getPath(), "utf-8");
    assertEquals("↹↝", IOUtils.tail(f, 2)[0]);
    assertEquals("۝æ", IOUtils.tail(f, 2)[1]);
    // Clean up
    assertTrue(f.delete());
  }

}
