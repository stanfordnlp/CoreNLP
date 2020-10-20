package edu.stanford.nlp.trees.international.pennchinese;

import java.io.File;

public class CEDict {
  private static final String defaultPath = "cedict_ts.u8";
  private static final String defaultPath2 = "/u/nlp/data/chinese-english-dictionary/cedict_ts.u8";
  private static final String ENV_VARIABLE = "CEDICT";

  public static String path() {
    File f = new File(defaultPath);
    if (f.canRead()) {
      return defaultPath;
    } else {
      f = new File(defaultPath2);
      if (f.canRead()) {
        return defaultPath2;
      } else {
        String path = System.getenv(ENV_VARIABLE);
        f = new File(path);
        if ( ! f.canRead()) {
          throw new RuntimeException("ChineseEnglishWordMap cannot find dictionary");
        }
        return path;
      }
    }
  }

  private CEDict() {} // static methods only
}
