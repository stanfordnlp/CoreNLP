package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.trees.TreebankLanguagePack;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ArcStandardTest {

    private ArcStandard arcStandard;

    @Before
    public void setUp() {
        TreebankLanguagePack mockedTreebank = mock(TreebankLanguagePack.class);
        List<String> labels = new ArrayList<>();
        labels.add("label_0");
        boolean verbose = false;
        this.arcStandard = new ArcStandard(mockedTreebank, labels, verbose);
    }

    @Test
    public void testIsTerminal() {
        Configuration mockedConfig = mock(Configuration.class);
        when(mockedConfig.getStackSize()).thenReturn(1);
        when(mockedConfig.getBufferSize()).thenReturn(0);

        boolean isTerminal = this.arcStandard.isTerminal(mockedConfig);

        Assert.assertTrue(isTerminal);
    }

    @Test
    public void testIsNotTerminal() {
        Configuration mockedConfig = mock(Configuration.class);
        when(mockedConfig.getStackSize()).thenReturn(-1);
        when(mockedConfig.getBufferSize()).thenReturn(0);

        boolean isTerminal = this.arcStandard.isTerminal(mockedConfig);

        Assert.assertFalse(isTerminal);
    }

    @Test
    public void canNotApply() {
        Configuration mockedConfig = mock(Configuration.class);

        Assert.assertFalse(this.arcStandard.canApply(mockedConfig, "test"));
    }
}