package edu.stanford.nlp.util;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class ConvertByteArrayTest {

  @Test
  public void testWriteIntToByteArr() {
    byte[] bytes = {0, 0, 0, 0, 0, 0};
    ConvertByteArray.writeIntToByteArr(bytes, 1, 2);
    ConvertByteArray.writeIntToByteArr(bytes, 2, 4);

    Assert.assertArrayEquals(new byte[] {0, 2, 4, 0, 0, 0}, bytes);
  }

  @Test
  public void testWriteLongToByteArr() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.writeLongToByteArr(bytes, 1, 4l);
    ConvertByteArray.writeLongToByteArr(bytes, 2, 8l);

    Assert.assertArrayEquals(new byte[] {0, 4, 8, 0, 0, 0, 0, 0, 0, 0}, bytes);
  }

  @Test
  public void testWriteFloatToByteArr() {
    byte[] bytes = {0, 0, 0, 0, 0, 0};
    ConvertByteArray.writeFloatToByteArr(bytes, 1, 2);
    ConvertByteArray.writeFloatToByteArr(bytes, 2, 4);

    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0, -128, 64}, bytes);
  }

  @Test
  public void testWriteDoubleToByteArr() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.writeDoubleToByteArr(bytes, 1, 2);
    ConvertByteArray.writeDoubleToByteArr(bytes, 2, 4);

    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 16, 64}, bytes);
  }

  @Test
  public void testWriteBooleanToByteArr() {
    byte[] bytes = {0, 0, 0, 0};
    ConvertByteArray.writeBooleanToByteArr(bytes, 1, true);
    ConvertByteArray.writeBooleanToByteArr(bytes, 2, false);

    Assert.assertArrayEquals(new byte[] {0, 0, 1, 0}, bytes);
  }

  @Test
  public void testWriteCharToByteArr() {
    byte[] bytes = {0, 0, 0, 0};
    ConvertByteArray.writeCharToByteArr(bytes, 0, 'a');

    Assert.assertArrayEquals(new byte[] {0, 97, 0, 0}, bytes);
  }

  @Test
  public void testWriteShortToByteArr() {
    byte[] bytes = {0, 0, 0, 0};
    ConvertByteArray.writeShortToByteArr(bytes, 1, (short)4);
    ConvertByteArray.writeShortToByteArr(bytes, 2, (short)8);

    Assert.assertArrayEquals(new byte[] {0, 4, 8, 0}, bytes);
  }

  @Test
  public void testWriteUStringToByteArr1() {
    byte[] bytes = {0, 0, 0, 0};
    ConvertByteArray.writeUStringToByteArr(bytes, 2, "a");

    Assert.assertArrayEquals(new byte[] {0, 0, 0, 97}, bytes);
  }

  @Test
  public void testWriteUStringToByteArr2() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.writeUStringToByteArr(bytes,  2, "foo", 0, 2);

    Assert.assertArrayEquals(new byte[] {0, 0, 0, 102, 0, 111, 0, 0}, bytes);
  }

  @Test
  public void testWriteAStringToByteArr() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.writeAStringToByteArr(bytes,  0, "fooBar", 2, 3);

    Assert.assertArrayEquals(new byte[] {111, 66, 97, 0, 0, 0, 0, 0}, bytes);
  }

  @Test
  public void testByteArrToInt() {
    Assert.assertEquals(16842752, ConvertByteArray.byteArrToInt(new byte[] {0, 0, 0, 0, 1, 1},  2));
  }

  @Test
  public void testByteArrToShort() {
    Assert.assertEquals((short)513, ConvertByteArray.byteArrToShort(new byte[] {1, 2},  0));
  }

  @Test
  public void testByteArrToFloat() {
    Assert.assertEquals(2.3509887E-38f,
        ConvertByteArray.byteArrToFloat(new byte[] {0, 0, 0, 0, 1, 1},  1), 0.0f);
  }

  @Test
  public void testByteArrToDouble() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0,
        0, 1, 1, 1, 1, 1, 1};

    Assert.assertEquals(7.748604158221286E-304, ConvertByteArray.byteArrToDouble(bytes,  4), 0.0);
  }

  @Test
  public void testByteArrToLong() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0,
        0, 1, 1, 1, 1, 1, 1};

    Assert.assertEquals(72340172838010880L, ConvertByteArray.byteArrToLong(bytes,  5));
  }

  @Test
  public void testByteArrToBoolean() {
    byte[] bytes = {0, 1, 0};
    Assert.assertTrue(ConvertByteArray.byteArrToBoolean(bytes,  0));

    Assert.assertFalse(ConvertByteArray.byteArrToBoolean(bytes,  1));
  }

  @Test
  public void testByteArrToChar() {
    Assert.assertEquals(1032, ConvertByteArray.byteArrToChar(new byte[] {2, 4, 8},  1));
  }

  @Test
  public void testByteArrToUString1() {
    Assert.assertEquals("a", ConvertByteArray.byteArrToUString(new byte[] {0, 97}));
    Assert.assertEquals("", ConvertByteArray.byteArrToUString(new byte[0]));
  }

  @Test
  public void testByteArrToUString2() {
    Assert.assertEquals("b", ConvertByteArray.byteArrToUString(new byte[] {5, 0, 98},  1, 1));
    Assert.assertEquals("", ConvertByteArray.byteArrToUString(new byte[0],  0, 0));
  }

  @Test
  public void testByteArrToAString1() {
    Assert.assertEquals(",", ConvertByteArray.byteArrToAString(new byte[] {44}));
  }

  @Test
  public void testByteArrToAString2() {
    byte[] bytes = {98, 98, 97, 97, 97, 98, 98};

    Assert.assertEquals("baaab", ConvertByteArray.byteArrToAString(bytes,  1, 5));
    Assert.assertEquals("", ConvertByteArray.byteArrToAString(null,  0, 0));
  }

  @Test
  public void testStringAToByteArr1() {
    Assert.assertArrayEquals(new byte[] {0, 51}, ConvertByteArray.stringUToByteArr("3"));
  }

  @Test
  public void testStringAToByteArr2() {
    Assert.assertArrayEquals(new byte[] {49}, ConvertByteArray.stringAToByteArr("1"));
  }

  @Test
  public void testIntArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {2, 0, 0, 0, 4, 0, 0, 0},
        ConvertByteArray.intArrToByteArr(new int[] {2, 4}));
  }

  @Test
  public void testIntArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {4, 0, 0, 0, 8, 0, 0, 0},
        ConvertByteArray.intArrToByteArr(new int[] {2, 4, 8, 16}, 1, 2));
  }

  @Test
  public void testIntArrToByteArr3() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.intArrToByteArr(bytes,  2, new int[] {1, 2, 4, 8, 16}, 1, 2);

    Assert.assertArrayEquals(new byte[] {0, 0, 2, 0, 0, 0, 4, 0, 0, 0}, bytes);
  }

  @Test
  public void testLongArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {64, 0, 0, 0, 0, 0, 0, 0, -128, 0, 0, 0, 0, 0, 0, 0},
        ConvertByteArray.longArrToByteArr(new long[] {64, 128}));
  }

  @Test
  public void testLongArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0},
        ConvertByteArray.longArrToByteArr(new long[] {2, 4, 8, 16}, 1, 2));
  }

  @Test
  public void testLongArrToByteArr3() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.longArrToByteArr(bytes,  2, new long[] {1, 2, 4}, 1, 1);

    Assert.assertArrayEquals(new byte[] {0, 0, 2, 0, 0, 0, 0, 0, 0, 0}, bytes);
  }

  @Test
  public void testBooleanArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {1},
        ConvertByteArray.booleanArrToByteArr(new boolean[] {false}));
    Assert.assertArrayEquals(new byte[] {0},
        ConvertByteArray.booleanArrToByteArr(new boolean[] {true}));
  }

  @Test
  public void testBooleanArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {1},
        ConvertByteArray.booleanArrToByteArr(new boolean[] {false}, 0, 1));
    Assert.assertArrayEquals(new byte[] {0},
        ConvertByteArray.booleanArrToByteArr(new boolean[] {true}, 0, 1));
  }

  @Test
  public void testBooleanArrToByteArr3() {
    byte[] bytes = {0, 1};
    ConvertByteArray.booleanArrToByteArr(bytes, 0, new boolean[] {false, true}, 0, 2);

    Assert.assertArrayEquals(new byte[] {1, 0}, bytes);
  }

  @Test
  public void testCharArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {0, 102, 0, 111},
        ConvertByteArray.charArrToByteArr(new char[] {'f', 'o'}));
  }

  @Test
  public void testCharArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {0, 111, 0, 111},
        ConvertByteArray.charArrToByteArr(new char[] {'f', 'o', 'o'}, 1, 2));
  }

  @Test
  public void testCharArrToByteArr3() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.charArrToByteArr(bytes,  2, new char[] {'f', 'o', 'o', 'B', 'a', 'r'}, 1, 3);

    Assert.assertArrayEquals(new byte[] {0, 0, 0, 111, 0, 111, 0, 66, 0, 0}, bytes);
  }

  @Test
  public void testFloatArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {0, 0, 0, 64, 0, 0, -128, 64},
        ConvertByteArray.floatArrToByteArr(new float[] {2, 4}));
  }

  @Test
  public void testFloatArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {0, 0, -128, 64, 0, 0, 0, 65},
        ConvertByteArray.floatArrToByteArr(new float[] {2, 4, 8, 16}, 1, 2));
  }

  @Test
  public void testFloatArrToByteArr3() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.floatArrToByteArr(bytes,  2, new float[] {1, 2, 4, 8, 16}, 1, 2);

    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 64, 0, 0, -128, 64}, bytes);
  }

  @Test
  public void testDoubleArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 16, 64},
        ConvertByteArray.doubleArrToByteArr(new double[] {2, 4}));
  }

  @Test
  public void testDoubleArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 16, 64, 0, 0, 0, 0, 0, 0, 32, 64},
        ConvertByteArray.doubleArrToByteArr(new double[] {2, 4, 8, 16}, 1, 2));
  }

  @Test
  public void testDoubleArrToByteArr3() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.doubleArrToByteArr(bytes,  2, new double[] {1, 2, 4, 8, 16}, 1, 2);

    Assert.assertArrayEquals(
        new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 16, 64}, bytes);
  }

  @Test
  public void testShortArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {2, 0},
        ConvertByteArray.shortArrToByteArr(new short[] {2}));
  }

  @Test
  public void testShortArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {4, 0},
        ConvertByteArray.shortArrToByteArr(new short[] {2, 4, 8}, 1, 1));
  }

  @Test
  public void testShortArrToByteArr3() {
    byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ConvertByteArray.shortArrToByteArr(bytes,  2, new short[] {1, 2, 4, 8, 16}, 1, 2);

    Assert.assertArrayEquals(new byte[] {0, 0, 2, 0, 0, 0, 4, 0, 0, 0}, bytes);
  }

  @Test
  public void testUStringArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {}, ConvertByteArray.uStringArrToByteArr(new String[0]));
  }

  @Test
  public void testUStringArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0},
        ConvertByteArray.uStringArrToByteArr(new String[] {null}, 0, 1));
    Assert.assertArrayEquals(new byte[] {1, 0, 0, 0, 0, 47},
        ConvertByteArray.uStringArrToByteArr(new String[] {"/"}, 0, 1));
  }

  @Test
  public void testUStringArrToByteArr3() {
    byte[] bytes = {1,  0, 0, 0, 0,39, 0, 0, 0, 39};
    ConvertByteArray.uStringArrToByteArr(bytes,  0, new String[] {"/"}, 0, 1);

    Assert.assertArrayEquals(new byte[] {1, 0, 0, 0, 0, 47, 0, 0, 0, 39}, bytes);
  }

  @Test
  public void testUStringArrToByteArr4() {
    byte[] bytes = {1,  0, 0, 0, 0, 47, 1, 0, 0, 0};
    ConvertByteArray.uStringArrToByteArr(bytes,  0, new String[] {null}, 0, 1);

    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 47, 1, 0, 0, 0}, bytes);
  }

  @Test
  public void testAStringArrToByteArr1() {
    Assert.assertArrayEquals(new byte[] {}, ConvertByteArray.aStringArrToByteArr(new String[0]));
  }

  @Test
  public void testAStringArrToByteArr2() {
    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0},
        ConvertByteArray.aStringArrToByteArr(new String[] {null}, 0, 1));
    Assert.assertArrayEquals(new byte[] {1, 0, 0, 0, 47},
        ConvertByteArray.aStringArrToByteArr(new String[] {"/"}, 0, 1));
  }

  @Test
  public void testAStringArrToByteArr3() {
    byte[] bytes = {1,  0, 0, 0, 0,39, 0, 0, 0, 39};
    ConvertByteArray.aStringArrToByteArr(bytes,  0, new String[] {"/"}, 0, 1);

    Assert.assertArrayEquals(new byte[] {1, 0, 0, 0, 47, 39, 0, 0, 0, 39}, bytes);
  }

  @Test
  public void testAStringArrToByteArr4() {
    byte[] bytes = {1,  0, 0, 0, 0, 47, 1, 0, 0, 0};
    ConvertByteArray.aStringArrToByteArr(bytes,  0, new String[] {null}, 0, 1);

    Assert.assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 47, 1, 0, 0, 0}, bytes);
  }

  @Test
  public void testByteArrToIntArr1() {
    Assert.assertArrayEquals(new int[] {},
        ConvertByteArray.byteArrToIntArr(new byte[0]));
  }

  @Test
  public void testByteArrToIntArr2() {
    Assert.assertArrayEquals(new int[] {2052, 0},
        ConvertByteArray.byteArrToIntArr(new byte[] {2, 4, 8, 0, 0, 0, 0, 0, 0},  1, 2));
  }

  @Test
  public void testByteArrToIntArr3() {
    int[] ints = {2, 4};
    ConvertByteArray.byteArrToIntArr(new byte[] {8, 16, 0, 0, 0, 0, 0, 0},  0, ints, 0, 2);

    Assert.assertArrayEquals(new int[] {4104, 0}, ints);
  }

  @Test
  public void testByteArrToLongArr1() {
    Assert.assertArrayEquals(new long[] {},
        ConvertByteArray.byteArrToLongArr(new byte[0]));
  }

  @Test
  public void testByteArrToLongArr2() {
    Assert.assertArrayEquals(new long[] {2052},
        ConvertByteArray.byteArrToLongArr(new byte[] {2, 4, 8, 0, 0, 0, 0, 0, 0},  1, 1));
  }

  @Test
  public void testByteArrToLongArr3() {
    long[] longs = {2, 4};
    ConvertByteArray.byteArrToLongArr(new byte[] {8, 16, 0, 0, 0, 0, 0, 0},  0, longs, 0, 1);

    Assert.assertArrayEquals(new long[] {4104, 4}, longs);
  }

  @Test
  public void testByteArrToBooleanArr1() {
    Assert.assertArrayEquals(new boolean[] {},
        ConvertByteArray.byteArrToBooleanArr(new byte[0]));
  }

  @Test
  public void testByteArrToBooleanArr2() {
    Assert.assertArrayEquals(new boolean[] {false},
        ConvertByteArray.byteArrToBooleanArr(new byte[] {1, 1},  1, 1));
  }

  @Test
  public void testByteArrToBooleanArr3() {
    boolean[] booleans = {true, false};
    ConvertByteArray.byteArrToBooleanArr(new byte[] {1, 0},  0, booleans, 0, 2);

    Assert.assertArrayEquals(new boolean[] {false, true}, booleans);
  }

  @Test
  public void testByteArrToCharArr1() {
    Assert.assertArrayEquals(new char[] {},
        ConvertByteArray.byteArrToCharArr(new byte[0]));
  }

  @Test
  public void testByteArrToCharArr2() {
    Assert.assertArrayEquals(new char[] {'a'},
        ConvertByteArray.byteArrToCharArr(new byte[] {0, 0, 97},  1, 1));
  }

  @Test
  public void testByteArrToCharArr3() {
    char[] chars = {'f', 'o', 'o'};
    ConvertByteArray.byteArrToCharArr(new byte[] {0, 97, 0, 98},  0, chars, 0, 2);

    Assert.assertArrayEquals(new char[] {'a', 'b', 'o'}, chars);
  }

  @Test
  public void testByteArrToShortArr1() {
    Assert.assertArrayEquals(new short[] {},
        ConvertByteArray.byteArrToShortArr(new byte[0]));
  }

  @Test
  public void testByteArrToShortArr2() {
    Assert.assertArrayEquals(new short[] {2052},
        ConvertByteArray.byteArrToShortArr(new byte[] {2, 4, 8, 0, 0, 0, 0, 0, 0},  1, 1));
  }

  @Test
  public void testByteArrToShortArr3() {
    short[] shorts = {2, 4};
    ConvertByteArray.byteArrToShortArr(new byte[] {8, 16, 0, 0, 0, 0, 0, 0},  0, shorts, 0, 1);

    Assert.assertArrayEquals(new short[] {4104, 4}, shorts);
  }

  @Test
  public void testByteArrToFloatArr1() {
    Assert.assertArrayEquals(new float[] {},
        ConvertByteArray.byteArrToFloatArr(new byte[0]), 0);
  }

  @Test
  public void testByteArrToFloatArr2() {
    Assert.assertArrayEquals(new float[] {2.875E-42f},
        ConvertByteArray.byteArrToFloatArr(new byte[] {2, 4, 8, 0, 0, 0, 0, 0, 0},  1, 1), 0);
  }

  @Test
  public void testByteArrToFloatArr3() {
    float[] floats = {2, 4};
    ConvertByteArray.byteArrToFloatArr(new byte[] {8, 16, 0, 0, 0, 0, 0, 0},  0, floats, 0, 1);

    Assert.assertArrayEquals(new float[] {5.751E-42f, 4}, floats, 0);
  }

  @Test
  public void testByteArrToDoubleArr1() {
    Assert.assertArrayEquals(new double[] {},
        ConvertByteArray.byteArrToDoubleArr(new byte[0]), 0);
  }

  @Test
  public void testByteArrToDoubleArr2() {
    Assert.assertArrayEquals(new double[] {1.014E-320},
        ConvertByteArray.byteArrToDoubleArr(new byte[] {2, 4, 8, 0, 0, 0, 0, 0, 0},  1, 1), 0);
  }

  @Test
  public void testByteArrToDoubleArr3() {
    double[] doubles = {2, 4};
    ConvertByteArray.byteArrToDoubleArr(new byte[] {8, 16, 0, 0, 0, 0, 0, 0},  0, doubles, 0, 1);

    Assert.assertArrayEquals(new double[] {2.0276E-320, 4}, doubles, 0);
  }

  @Test
  public void testByteArrToUStringArr1() {
    Assert.assertArrayEquals(new String[] {""},
        ConvertByteArray.byteArrToUStringArr(new byte[] {0, 0, 0, 0}));
  }

  @Test
  public void testByteArrToUStringArr2() {
    Assert.assertArrayEquals(new String[] {""},
        ConvertByteArray.byteArrToUStringArr(new byte[] {0, 0, 0, 0},  0, 1));
    Assert.assertArrayEquals(new String[] {"\u0001"},
        ConvertByteArray.byteArrToUStringArr(new byte[] {1, 0, 0, 0, 0, 1},  0, 1));
  }

  @Test
  public void testByteArrToUStringArr3() {
    String[] strings = {null};
    ConvertByteArray.byteArrToUStringArr(new byte[] {0, 0, 0, 0, 1, 1},  0, strings, 0, 1);

    Assert.assertArrayEquals(new String[] {""}, strings);
  }

  @Test
  public void testByteArrToUStringArr4() {
    String[] strings = {null};
    ConvertByteArray.byteArrToUStringArr(new byte[] {1, 0, 0, 0, 0, 1},  0, strings, 0, 1);

    Assert.assertArrayEquals(new String[] {"\u0001"}, strings);
  }

  @Test
  public void testByteArrToAStringArr1() {
    Assert.assertArrayEquals(new String[] {""},
        ConvertByteArray.byteArrToAStringArr(new byte[] {0, 0, 0, 0}));
  }

  @Test
  public void testByteArrToAStringArr2() {
    Assert.assertArrayEquals(new String[] {""},
        ConvertByteArray.byteArrToAStringArr(new byte[] {0, 0, 0, 0},  0, 1));
    Assert.assertArrayEquals(new String[] {"\u0000"},
        ConvertByteArray.byteArrToAStringArr(new byte[] {1, 0, 0, 0, 0, 1},  0, 1));
  }

  @Test
  public void testByteArrToAStringArr3() {
    String[] strings = {null};
    ConvertByteArray.byteArrToAStringArr(new byte[] {0, 0, 0, 0, 1, 1},  0, strings, 0, 1);

    Assert.assertArrayEquals(new String[] {""}, strings);
  }

  @Test
  public void testByteArrToAStringArr4() {
    String[] strings = {null};
    ConvertByteArray.byteArrToAStringArr(new byte[] {1, 0, 0, 0, 0, 1},  0, strings, 0, 1);

    Assert.assertArrayEquals(new String[] {"\u0000"}, strings);
  }
}
