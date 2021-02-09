package edu.stanford.nlp.coref.data;

import org.junit.Assert;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class DocumentTest {

    @Test
    public void testEmptyClustersNotIncompatible() {
        Document document = new Document();
        CorefCluster mockedCluster1 = mock(CorefCluster.class);
        CorefCluster mockedCluster2 = mock(CorefCluster.class);

        boolean isIncompatible = document.isIncompatible(mockedCluster1, mockedCluster2);

        Assert.assertFalse(isIncompatible);
    }
}