package edu.stanford.nlp.trees.ud;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;
import org.junit.Test;

public class RoundTripCoNLLUTest {

  /** A sentence whose Misc columns are not in the writer's order */
  private static final String UNSORTED_MISC = String.join("\n",
    "# sent_id = t1",
    "# text = Hola mundo.",
    "1\tHola\thola\tINTJ\t_\t_\t0\troot\t0:root\tSpaceAfter=No|Gloss=hello",
    "2\tmundo\tmundo\tNOUN\t_\tGender=Masc|Number=Sing\t1\tvocative\t1:vocative\tTranslit=mundo|Gloss=world|SpaceAfter=No",
    "3\t.\t.\tPUNCT\t_\t_\t1\tpunct\t1:punct\t_",
    "", "");

  private static File write(String conllu) throws IOException {
    File file = File.createTempFile("roundtrip", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), conllu.getBytes(StandardCharsets.UTF_8));
    return file;
  }

  /**
   * The Misc column comes back sorted and nothing else changes, and a
   * file written that way comes back the same
   */
  @Test
  public void testRoundTrip() throws IOException {
    File file = write(UNSORTED_MISC);
    String expected = UNSORTED_MISC.replace("SpaceAfter=No|Gloss=hello", "Gloss=hello|SpaceAfter=No")
      .replace("Translit=mundo|Gloss=world|SpaceAfter=No", "Gloss=world|SpaceAfter=No|Translit=mundo");
    assertEquals(expected, RoundTripCoNLLU.roundTrip(file.getPath()));

    // main rewrites the file in place, and a second pass changes nothing
    RoundTripCoNLLU.main(new String[] { file.getPath() });
    assertEquals(expected, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    assertEquals(expected, RoundTripCoNLLU.roundTrip(file.getPath()));
  }
}
