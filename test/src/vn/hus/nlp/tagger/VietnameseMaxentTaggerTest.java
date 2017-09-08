package vn.hus.nlp.tagger;

import org.junit.Test;

import java.util.List;

import old.edu.stanford.nlp.ling.WordTag;

import static org.junit.Assert.assertEquals;


public class VietnameseMaxentTaggerTest {
    @Test
    public void success() {
        VietnameseMaxentTagger tagger = new VietnameseMaxentTagger();
        List<WordTag> wordTags = tagger.tagText2("Phân tích cảm xúc trong một văn bản Tiếng Việt");
        assertEquals(6, wordTags.size());
        assertEquals("Phân_tích", wordTags.get(0).word());
        assertEquals("V", wordTags.get(0).tag());
        assertEquals("cảm_xúc", wordTags.get(1).word());
        assertEquals("N", wordTags.get(1).tag());
        assertEquals("trong", wordTags.get(2).word());
        assertEquals("E", wordTags.get(2).tag());
        assertEquals("một", wordTags.get(3).word());
        assertEquals("M", wordTags.get(3).tag());
        assertEquals("văn_bản", wordTags.get(4).word());
        assertEquals("N", wordTags.get(4).tag());
        assertEquals("Tiếng_Việt", wordTags.get(5).word());
        assertEquals("N", wordTags.get(5).tag());
    }
}