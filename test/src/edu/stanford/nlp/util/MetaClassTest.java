package edu.stanford.nlp.util;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;
import org.junit.*;

public class MetaClassTest {

  private static final String CLASS =  MetaClassTest.class.getName();

  //Interface
  public static interface ISomething{
    public String display();
  }
  public static class IInstSomething implements ISomething{
    public String display(){ return "Isomething"; }
  }
  //Abstract
  public static abstract class ASomething{
    public abstract String display();
  }
  public static class AInstSomething extends ASomething{
    public String display(){ return "Asomething"; }
  }
  //Superclass
  public static class SSomething{
    public String display(){ return "FAIL"; }
  }
  public static class SInstSomething extends SSomething{
    public String display(){ return "Ssomething"; }
  }
  //Simpleclass
  public static class Something{
    public String display(){ return "something"; }
  }
  public static class SomethingWrapper{
    private ISomething isomething;
    private ASomething asomething;
    private SSomething ssomething;
    private Something something;
    public SomethingWrapper(ISomething something){
      this.isomething = something;
    }
    public SomethingWrapper(ASomething something){
      this.asomething = something;
    }
    public SomethingWrapper(SSomething something){
      this.ssomething = something;
    }
    public SomethingWrapper(Something something){
      this.something = something;
    }
    public String display(){ return something.display(); }
    public String displayI(){ return isomething.display(); }
    public String displayA(){ return asomething.display(); }
    public String displayS(){ return ssomething.display(); }
  }
  public static class SubSSomething extends SSomething{
    public String display(){ return "subssomething"; }
  }
  public static class ManyConstructors {
    private int constructorInvoked = -1;
    public ManyConstructors(Object a){
      constructorInvoked = 0;
    }
    public ManyConstructors(Something a){
      constructorInvoked = 1;
    }
    public ManyConstructors(SSomething a){
      constructorInvoked = 2;
    }
    public ManyConstructors(SubSSomething a){
      constructorInvoked = 3;
    }
    public ManyConstructors(Object a, Object b){
      this.constructorInvoked = 4;
    }
    public ManyConstructors(Object a, Something b){
      this.constructorInvoked = 5;
    }
    public ManyConstructors(Object a, SSomething b){
      this.constructorInvoked = 6;
    }
    public ManyConstructors(Object a, SubSSomething b){
      this.constructorInvoked = 7;
    }

    public ManyConstructors(Something a, Object b){
      this.constructorInvoked = 8;
    }
    public ManyConstructors(Something a, Something b){
      this.constructorInvoked = 9;
    }
    public ManyConstructors(Something a, SSomething b){
      this.constructorInvoked = 10;
    }
    public ManyConstructors(Something a, SubSSomething b){
      this.constructorInvoked = 11;
    }

    public ManyConstructors(SSomething a, Object b){
      this.constructorInvoked = 12;
    }
    public ManyConstructors(SSomething a, Something b){
      this.constructorInvoked = 13;
    }
    public ManyConstructors(SSomething a, SSomething b){
      this.constructorInvoked = 14;
    }
    public ManyConstructors(SSomething a, SubSSomething b){
      this.constructorInvoked = 15;
    }

    public ManyConstructors(SubSSomething a, Object b){
      this.constructorInvoked = 16;
    }
    public ManyConstructors(SubSSomething a, Something b){
      this.constructorInvoked = 17;
    }
    public ManyConstructors(SubSSomething a, SSomething b){
      this.constructorInvoked = 18;
    }
    public ManyConstructors(SubSSomething a, SubSSomething b){
      this.constructorInvoked = 19;
    }
    public ManyConstructors(Something a, Something b, Something c){
      this.constructorInvoked = 20;
    }
    public int constructorInvoked(){ return constructorInvoked; }
    public boolean equals(Object o){
      if(o instanceof ManyConstructors){
        return this.constructorInvoked == ((ManyConstructors) o).constructorInvoked;
      }
      return false;
    }
    public String toString(){
      return "" + constructorInvoked;
    }
  }


  public static class Primitive{
    public Primitive(int i){}
    public Primitive(double d){}
  }

  public static class VarArgs{
    public int a[];
    public VarArgs(int ...args){
      a = args;
    }
  }

  @Test
  public void testBasic(){
    //--Basics
    //(succeed)
    MetaClass.create("java.lang.String");
    assertEquals(MetaClass.create("java.lang.String").createInstance("hello"), "hello");
    //(fail)
    try{
      MetaClass.create(CLASS+"$SomethingWrapper").createInstance("hello");
    } catch(MetaClass.ClassCreationException e){
      assertTrue("Should not instantiate Super with String", true);
    }

    //--Argument Length
    MetaClass.create(CLASS+"$ManyConstructors").createInstance(new Something(), new Something(), new Something());
    assertEquals(
            ((ManyConstructors) MetaClass.create(CLASS+"$ManyConstructors").createInstance(
                    new Something()
            )).constructorInvoked(),
            new ManyConstructors(
                    new Something()
            ).constructorInvoked()
    );
    assertEquals(
            ((ManyConstructors) MetaClass.create(CLASS+"$ManyConstructors").createInstance(
                    new Something(), new Something()
            )).constructorInvoked(),
            new ManyConstructors(
                    new Something(), new Something()
            ).constructorInvoked()
    );
    assertEquals(
            ((ManyConstructors) MetaClass.create(CLASS+"$ManyConstructors").createInstance(
                    new Something(), new Something(), new Something()
            )).constructorInvoked(),
            new ManyConstructors(
                    new Something(), new Something(), new Something()
            ).constructorInvoked()
    );

    assertEquals(new ManyConstructors(new String("hi")), MetaClass.create(CLASS+"$ManyConstructors").createInstance(new String("hi")));
    assertEquals(new ManyConstructors(new Something()), MetaClass.create(CLASS+"$ManyConstructors").createInstance(new Something()));
    assertEquals(new ManyConstructors(new SSomething()), MetaClass.create(CLASS+"$ManyConstructors").createInstance(new SSomething()));
    assertEquals(new ManyConstructors(new SubSSomething()), MetaClass.create(CLASS+"$ManyConstructors").createInstance(new SubSSomething()));
  }

  @Test
  public void testInheritance(){
    //--Implementing Class
    try{
      Object o = MetaClass.create(CLASS+"$SomethingWrapper").createInstance(new Something());
      assertTrue("Returned class should be a SomethingWrapper", o instanceof SomethingWrapper );
      assertEquals(((SomethingWrapper) o).display(), "something");
    } catch(MetaClass.ClassCreationException e){
      e.printStackTrace();
      assertFalse("Should not exception on this call", true);
    }
    //--Implementing super class
    try{
      Object o = MetaClass.create(CLASS+"$SomethingWrapper").createInstance(new SInstSomething());
      assertTrue("Returned class should be a SomethingWrapper", o instanceof SomethingWrapper );
      assertEquals(((SomethingWrapper) o).displayS(), "Ssomething");
    } catch(MetaClass.ClassCreationException e){
      e.printStackTrace();
      assertFalse("Should not exception on this call", true);
    }
    //--Implementing abstract classes
    try{
      Object o = MetaClass.create(CLASS+"$SomethingWrapper").createInstance(new AInstSomething());
      assertTrue("Returned class should be a SomethingWrapper", o instanceof SomethingWrapper );
      assertEquals(((SomethingWrapper) o).displayA(), "Asomething");
    } catch(MetaClass.ClassCreationException e){
      e.printStackTrace();
      assertFalse("Should not exception on this call", true);
    }
    //--Implementing interfaces
    try{
      Object o = MetaClass.create(CLASS+"$SomethingWrapper").createInstance(new IInstSomething());
      assertTrue("Returned class should be a SomethingWrapper", o instanceof SomethingWrapper );
      assertEquals(((SomethingWrapper) o).displayI(), "Isomething");
    } catch(MetaClass.ClassCreationException e){
      e.printStackTrace();
      assertFalse("Should not exception on this call", true);
    }
  }

  private ManyConstructors makeRef(int i, int j){
    switch(i){
      case 0:
        switch(j){
          case 0:
            return new ManyConstructors(new String("hi"), new String("hi"));
          case 1:
            return new ManyConstructors(new String("hi"), new Something());
          case 2:
            return new ManyConstructors(new String("hi"), new SSomething());
          case 3:
            return new ManyConstructors(new String("hi"), new SubSSomething());
        }
        return null;
      case 1:
        switch(j){
          case 0:
            return new ManyConstructors(new Something(), new String("hi"));
          case 1:
            return new ManyConstructors(new Something(), new Something());
          case 2:
            return new ManyConstructors(new Something(), new SSomething());
          case 3:
            return new ManyConstructors(new Something(), new SubSSomething());
        }
        return null;
      case 2:
        switch(j){
          case 0:
            return new ManyConstructors(new SSomething(), new String("hi"));
          case 1:
            return new ManyConstructors(new SSomething(), new Something());
          case 2:
            return new ManyConstructors(new SSomething(),  new SSomething());
          case 3:
            return new ManyConstructors(new SSomething(),  new SubSSomething());
        }
        return null;
      case 3:
        switch(j){
          case 0:
            return new ManyConstructors(new SubSSomething(), new String("hi"));
          case 1:
            return new ManyConstructors(new SubSSomething(), new Something());
          case 2:
            return new ManyConstructors(new SubSSomething(), new SSomething());
          case 3:
            return new ManyConstructors(new SubSSomething(), new SubSSomething());
        }
        return null;
    }
    return null;
  }

  private static ManyConstructors makeRef(int i){
    switch(i){
      case 0:
        return new ManyConstructors(new String("hi"));
      case 1:
        return new ManyConstructors(new Something());
      case 2:
        return new ManyConstructors(new SSomething());
      case 3:
        return new ManyConstructors(new SubSSomething());
    }
    return null;
  }

  @Test
  public void testConsistencyWithJava(){
    Object[] options = new Object[]{ new String("hi"), new Something(), new SSomething(), new SubSSomething() };
		/*
		 * Single Term
		 */
    //--Cast everything as an object
    for (Object option : options) {
      ManyConstructors ref = new ManyConstructors(option);
      ManyConstructors test = (ManyConstructors)
              MetaClass.create(CLASS + "$ManyConstructors")
                      .createFactory(Object.class)
                      .createInstance(option);
      assertEquals(ref.constructorInvoked(), test.constructorInvoked());
    }
    //--Use native types
    for(int i=0; i<options.length; i++){
      ManyConstructors ref = makeRef(i);
      ManyConstructors test = (ManyConstructors)
              MetaClass.create(CLASS+"$ManyConstructors")
                      .createInstance(options[i]);
      assertEquals(ref, test);
    }
		/*
		 * Multi Term
		 */
    //--Use native types
    for(int i=0; i<options.length; i++){
      for(int j=0; j<options.length; j++){
        ManyConstructors ref = makeRef(i,j);
        ManyConstructors test = (ManyConstructors)
                MetaClass.create(CLASS+"$ManyConstructors")
                        .createInstance(options[i], options[j]);
        assertEquals(ref, test);
      }
    }
  }

  @Test
  public void testPrimitives(){
    // pass a value as a class
    MetaClass.create(CLASS+"$Primitive").createInstance(Integer.valueOf(7));
    MetaClass.create(CLASS+"$Primitive").createInstance(Double.valueOf(7));
    // pass a value as a primitive
    MetaClass.create(CLASS+"$Primitive").createInstance(7);
    MetaClass.create(CLASS+"$Primitive").createInstance(2.8);
    //(fail)
    try{
      MetaClass.create(CLASS+"$Primitive").createInstance(7L);
    } catch(MetaClass.ClassCreationException e){
      assertTrue("Should not be able to case Long int Primitive()", true);
    }
  }

  @Test
  public void testCastSimple() {
    assertEquals(Double.valueOf(1.0), MetaClass.cast("1.0", Double.class));
    assertEquals(Integer.valueOf(1), MetaClass.cast("1", Integer.class));
    assertEquals(Integer.valueOf(1), MetaClass.cast("1.0", Integer.class));
    assertEquals(Long.valueOf(1L), MetaClass.cast("1.0", Long.class));
    assertEquals(Short.valueOf((short) 1), MetaClass.cast("1.0", Short.class));
    assertEquals(Byte.valueOf((byte) 1), MetaClass.cast("1.0", Byte.class));
    assertEquals("Hello", MetaClass.cast("Hello", String.class));
    assertEquals(true, MetaClass.cast("true", Boolean.class));
    assertEquals(true, MetaClass.cast("1", Boolean.class));
    assertEquals(false, MetaClass.cast("False", Boolean.class));
    assertEquals(new File("/path/to/file"), MetaClass.cast("/path/to/file", File.class));
  }

  @Test
  public void testCastArray() {
    Integer[] ints1 = MetaClass.cast("[1,2,3]", Integer[].class);
    assertArrayEquals(new Integer[]{1,2,3}, ints1);
    Integer[] ints2 = MetaClass.cast("(1,2,3)", Integer[].class);
    assertArrayEquals(new Integer[]{1,2,3}, ints2);
    Integer[] ints3 = MetaClass.cast("1, 2, 3", Integer[].class);
    assertArrayEquals(new Integer[]{1,2,3}, ints3);
    Integer[] ints4 = MetaClass.cast("1 2 3", Integer[].class);
    assertArrayEquals(new Integer[]{1,2,3}, ints4);
    Integer[] ints5 = MetaClass.cast("1   2   3", Integer[].class);
    assertArrayEquals(new Integer[]{1,2,3}, ints5);
    Integer[] ints6 = MetaClass.cast("\n1 \n\n  2   3", Integer[].class);
    assertArrayEquals(new Integer[]{1,2,3}, ints6);

    Integer[] intsEmpty = MetaClass.cast("", Integer[].class);
    assertArrayEquals(new Integer[]{}, intsEmpty);
  }

  @Test
  public void testCastStringArray() throws IOException {
    String[] strings1 = MetaClass.cast("[a,b,c]", String[].class);
    assertArrayEquals(new String[]{"a", "b", "c"}, strings1);

    String string1 = Files.createTempFile("TestCastString", "tmp").toString();
    String string2 = Files.createTempFile("TestCastString", "tmp").toString();
    String[] strings2 = MetaClass.cast("['" + string1 + "','" + string2 + "']", String[].class);
    assertArrayEquals(new String[]{string1, string2}, strings2);

    String[] strings3 = MetaClass.cast("['a','b','c']", String[].class);
    assertArrayEquals(new String[]{"a", "b", "c"}, strings3);
  }

  private static enum Fruits {
    APPLE,
    Orange,
    grape
  }

  @Test
  public void testCastEnum() {
    assertEquals(Fruits.APPLE, MetaClass.cast("APPLE", Fruits.class));
    assertEquals(Fruits.APPLE, MetaClass.cast("apple", Fruits.class));
    assertEquals(Fruits.APPLE, MetaClass.cast("Apple", Fruits.class));
    assertEquals(Fruits.APPLE, MetaClass.cast("aPPlE", Fruits.class));
    assertEquals(Fruits.Orange, MetaClass.cast("orange", Fruits.class));
    assertEquals(Fruits.grape, MetaClass.cast("grape", Fruits.class));
    assertEquals(Fruits.grape, MetaClass.cast("Grape", Fruits.class));
    assertEquals(Fruits.grape, MetaClass.cast("GRAPE", Fruits.class));
  }

  @Test
  public void testCastCollection() {
    Set<String> set = new HashSet<>();
    set.add("apple");
    set.add("banana");
    Set<String> castedSet = MetaClass.cast("[apple, banana]", Set.class);
    Set<String> castedSet2 = MetaClass.cast("[apple ,    banana ]", Set.class);
    Set<String> castedSet3 = MetaClass.cast("{apple ,    banana }", Set.class);
    assertEquals(set, castedSet);
    assertEquals(set, castedSet2);
    assertEquals(set, castedSet3);

    List<String> list = new LinkedList<>();
    list.add("apple");
    list.add("banana");
    List<String> castedList = MetaClass.cast("[apple, banana]", List.class);
    assertEquals(list, castedList);
  }

  private static class Pointer<E> {
    public E value;
    public Pointer(E value) {
      this.value = value;
    }
    @SuppressWarnings("UnusedDeclaration") // used via reflection
    public static <E> Pointer<E> fromString(String value) {
      E v = MetaClass.castWithoutKnowingType(value);
      return new Pointer<E>(v);
    }
  }

  @Test
  public void testCastMap() {
    Map<String, String> a = MetaClass.cast("{ a -> 1, b -> 2 }", Map.class);
    assertEquals(2, a.size());
    assertEquals("1", a.get("a"));
    assertEquals("2", a.get("b"));

    Map<String, String> b = MetaClass.cast("a => 1, b -> 2", Map.class);
    assertEquals(2, b.size());
    assertEquals("1", b.get("a"));
    assertEquals("2", b.get("b"));

    Map<String, String> c = MetaClass.cast("[a->1;b->2]", Map.class);
    assertEquals(2, c.size());
    assertEquals("1", c.get("a"));
    assertEquals("2", c.get("b"));

    Map<String, String> d = MetaClass.cast("\n\na->\n1\n\n\nb->2", Map.class);
    assertEquals(2, d.size());
    assertEquals("1", d.get("a"));
    assertEquals("2", d.get("b"));
  }

  @Test
  public void testCastRegression() {
    // Generics ordering (integer should go relatively early)
    Pointer<Integer> x1 = MetaClass.cast("1", Pointer.class);
    assertEquals(1, x1.value.intValue());

  }

  private static class FromStringable {
    public final String myContents;
    private FromStringable(String contents) { myContents = contents; }
    public static FromStringable fromString(String str) {
      return new FromStringable(str);
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FromStringable)) return false;
      FromStringable that = (FromStringable) o;
      if (myContents != null ? !myContents.equals(that.myContents) : that.myContents != null) return false;
      return true;
    }
    @Override
    public int hashCode() {
      return myContents != null ? myContents.hashCode() : 0;
    }
  }

  @Test
  public void testCastFromString() {
    assertEquals(new FromStringable("foo"), MetaClass.cast("foo", FromStringable.class));
    assertEquals(new FromStringable("bar"), MetaClass.cast("bar", FromStringable.class));
  }

  @Test
  public void testCastStream() {
    assertEquals(System.out, MetaClass.cast("stdout", OutputStream.class));
    assertEquals(System.out, MetaClass.cast("out", OutputStream.class));
    assertEquals(System.err, MetaClass.cast("stderr", OutputStream.class));
    assertEquals(System.err, MetaClass.cast("err", OutputStream.class));
    assertEquals(ObjectOutputStream.class, MetaClass.cast("err", ObjectOutputStream.class).getClass());
  }

//	TODO(gabor) this would be kind of cool to implement
/*
  @Test
  @Ignore
  public void testVariableArgConstructor(){
    VarArgs a = MetaClass.create(CLASS+"$VarArgs").createInstance(1,2,3);
    assertEquals(3, a.a.length);
    assertTrue(a.a[0] == 1);
    assertTrue(a.a[1] == 2);
    assertTrue(a.a[2] == 3);
  }
*/

}
