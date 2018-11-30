package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.util.Pair;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;

/**
 * Helper iterator to read a file with stored annotations
 *
 * @author <a href="mailto:*angel@eloquent.ai">*Angel Chang</a>
 */
public class AnnotationIterator extends AbstractIterator<Annotation> implements Closeable {

    // State
    String filename;
    BufferedReader br;
    InputStream input;
    Annotation nextDoc;
    AnnotationSerializer serializer;
    JSONAnnotationReader jsonReader = new JSONAnnotationReader();
    String format;
    int docCnt = 0;
    int limit = 0;

    public AnnotationIterator(String filename) throws IOException {
        this.filename = filename;
        if (filename.endsWith(".json")) {
            this.br = IOUtils.readerFromString(filename);
            this.format = "json";
        } else if (filename.endsWith(".jsonl")) {
            this.br = IOUtils.readerFromString(filename);
            this.format = "jsonl";
        } else if (filename.endsWith(".proto")) {
            this.input = IOUtils.getFileInputStream(filename);
            this.serializer = new ProtobufAnnotationSerializer();
            this.format = "proto";
        } else {
            throw new IOException("Unsupported file format: " + filename);
        }
        nextDoc = readNextDocument();
    }

    public AnnotationIterator(String filename, int limit) throws IOException {
        this(filename);
        this.limit = limit;
    }

    @Override
    public boolean hasNext() {
        return nextDoc != null;
    }

    @Override
    public Annotation next() {
        if (nextDoc == null) {
            throw new NoSuchElementException("DocumentIterator exhausted.");
        }
        Annotation curDoc = nextDoc;
        nextDoc = readNextDocument();
        return curDoc;
    }

    public Annotation readJsonDocument(String str) {
        return jsonReader.read(str);
    }

    public Annotation readNextDocument() {
        if (br == null && input == null) {
            return null;
        }
        if (limit > 0 && docCnt >= limit) {
            return null;
        }
        try {
            if (serializer != null) {
                if (input.available() > 0) {
                    Pair<Annotation, InputStream> pair = serializer.read(input);
                    input = pair.second;
                    docCnt++;
                    return pair.first;
                }
            } else if (format.equals("json")) {
                Annotation annotation = readJsonDocument(IOUtils.slurpReader(this.br));
                this.close();
                docCnt++;
                return annotation;
            } else if (format.equals("jsonl")) {
                String line = br.readLine();
                while (line != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        Annotation annotation = readJsonDocument(line);
                        docCnt++;
                        return annotation;
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Error reading from " + this.filename, ex);
        }
        return null;
    }

    public int getDocCnt() {
        return docCnt;
    }

    public void close() {
        if (br != null) {
            IOUtils.closeIgnoringExceptions(br);
            br = null;
        }
        if (input != null) {
            IOUtils.closeIgnoringExceptions(input);
            input = null;
        }
    }
}
