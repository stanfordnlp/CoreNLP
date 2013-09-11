/* RandomAccessInputStream
*
* Created on May 21, 2004
*
* Copyright (C) 2004 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package edu.stanford.nlp.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;


/**
 * Wraps a RandomAccessFile with an InputStream interface; from Heretrix.
 *
 * @author gojomo (heretrix)
 */
public class RandomAccessInputStream extends InputStream {
    
    /**
     * Reference to the random access file this stream is reading from.
     */
    private RandomAccessFile raf = null;
    
    /**
     * When mark is called, save here the current position so we can go back
     * on reset.
     */
    private long markpos = -1;

    /**
     * True if we are to close the underlying random access file when this
     * stream is closed.
     */
    private boolean sympathyClose;

    /**
     * Constructor.
     * 
     * If using this constructor, caller created the RAF and therefore
     * its assumed wants to control close of the RAF.  The RAF.close
     * is not called if this constructor is used on close of this stream.
     * 
     * @param raf RandomAccessFile to wrap.
     * @throws IOException
     */
    public RandomAccessInputStream(RandomAccessFile raf)
    throws IOException {
        this(raf, false, 0);
    }
    
    /**
     * Constructor.
     * 
     * @param file File to get RAFIS on.  Creates an RAF from passed file.
     * Closes the created RAF when this stream is closed.
     * @throws IOException 
     */
    public RandomAccessInputStream(final File file)
    throws IOException {
        this(new RandomAccessFile(file, "r"), true, 0);
    }
    
    /**
     * Constructor.
     * 
     * @param file File to get RAFIS on.  Creates an RAF from passed file.
     * Closes the created RAF when this stream is closed.
     * @throws IOException 
     */
    public RandomAccessInputStream(final File file, final long offset)
    throws IOException {
        this(new RandomAccessFile(file, "r"), true, offset);
    }
    
    /**
     * @param raf RandomAccessFile to wrap.
     * @param sympathyClose Set to true if we are to close the RAF
     * file when this stream is closed.
     * @throws IOException
     */
    public RandomAccessInputStream(final RandomAccessFile raf,
            final boolean sympathyClose, final long offset)
    throws IOException {
        super();
        this.sympathyClose = sympathyClose;
        this.raf = raf;
        if (offset > 0) {
            this.raf.seek(offset);
        }
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#read()
     */
    @Override
    public int read() throws IOException {
        return this.raf.read();
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#read(byte[], int, int)
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return this.raf.read(b, off, len);
    }
    
    /* (non-Javadoc)
     * @see java.io.InputStream#read(byte[])
     */
    @Override
    public int read(byte[] b) throws IOException {
        return this.raf.read(b);
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#skip(long)
     */
    @Override
    public long skip(long n) throws IOException {
        this.raf.seek(this.raf.getFilePointer() + n);
        return n;
    }

	public long position() throws IOException {
		return this.raf.getFilePointer();
	}

	public void position(long position) throws IOException {
		this.raf.seek(position);
	}
    
	@Override
  public int available() throws IOException {
        long amount = this.raf.length() - this.position();
        return (amount >= Integer.MAX_VALUE)? Integer.MAX_VALUE: (int)amount;
	}
	
    @Override
    public boolean markSupported() {
        return true;
    }
    
    @Override
    public synchronized void mark(int readlimit) {
        try {
            this.markpos = position();
        } catch (IOException e) {
            // Set markpos to -1. Will cause exception reset.
            this.markpos = -1;
        }
    }
    
    @Override
    public synchronized void reset() throws IOException {
        if (this.markpos == -1) {
            throw new IOException("Mark has not been set.");
        }
        position(this.markpos);
    }
    
    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            if (this.sympathyClose) {
                this.raf.close();
            }
        }
    }
}