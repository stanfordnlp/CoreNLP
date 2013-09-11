/* MultiGZIPInputStream
*
* Modified by dramage 20070603
*
* Created on July 5, 2004
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

/**
 * Subclass of GZIPInputStream that can handle a stream made of multiple
 * concatenated GZIP members/records.
 * 
 * This class is needed because GZIPInputStream only finds the first GZIP
 * member in the file even if the file is made up of multiple GZIP members.
 * 
 * Originally part of Heretrix, modified by dramage
 * 
 * @author stack (heretrix)
 * @author dramage
 */
public class MultiGZIPInputStream extends GZIPInputStream implements Iterable<InputStream> {
    /**
     * Tail on gzip members (The CRC).
     */
    private static final int GZIP_TRAILER_LENGTH = 8;
    
    /**
     * Buffer size used skipping over gzip members.
     */
    private static final int LINUX_PAGE_SIZE = 4 * 1024;
    
    /** Random access file used for reading */
    private final RandomAccessFile raf;

    /** Initial offset (always 0 in this implementation) */
    private final long initialOffset;
    
    public MultiGZIPInputStream(RandomAccessFile file) throws IOException {
        // Have buffer match linux page size.
        this(file, LINUX_PAGE_SIZE);
    }
    
    public MultiGZIPInputStream(RandomAccessFile file, int size) throws IOException {
    	super(new RandomAccessInputStream(file), size);
    	
    	this.raf = file;
    	this.initialOffset = 0;
    }
    
    /**
     * Exhaust current GZIP member content.
     * Call this method when you think you're on the end of the
     * GZIP member.  It will clean out any dross.
     * @return Count of characters skipped over.
     * @throws IOException
     */
    private long gotoEOR() throws IOException {
        long bytesSkipped = 0;
        if (this.inf.getTotalIn() <= 0) {
            return bytesSkipped;
        }
        while(!this.inf.finished()) {
            bytesSkipped += skip(Long.MAX_VALUE);
        }
        return bytesSkipped;
    }
    
    /**
     * Returns a GZIP Member Iterator.
     * Has limitations. Can only get one Iterator per instance of this class;
     * you must get new instance if you want to get Iterator again.
     * @return Iterator over GZIP Members.
     */
    public Iterator<InputStream> iterator() {
        final Logger logger = Logger.getLogger(this.getClass().getName());
        
        try {
        	raf.seek(initialOffset);
        	resetInflater();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        return new Iterator<InputStream>() {
            public boolean hasNext() {
                try {
                    gotoEOR();
                } catch (IOException e) {
                    if ((e instanceof ZipException) ||
                        (e.getMessage() != null &&
                         e.getMessage().startsWith("Corrupt GZIP trailer"))) {
                        // Try skipping end of bad record; try moving to next.
                        logger.info("Skipping exception " + e.getMessage());
                    } else {
                        throw new RuntimeException(e);
                    }
                }
                return moveToNextGzipMember();
            }
            
            /**
             * @return An InputStream onto a GZIP Member.
             */
            public InputStream next() {
                try {
                    gzipMemberSeek();
                } catch (IOException e) {
                    throw new RuntimeException("Failed move to EOR or " +
                        "failed header read: " + e.getMessage());
                }
                
                return new InputStream() {
                  @Override
                  public int read() throws IOException {
                    return MultiGZIPInputStream.this.read();
                  }

                  @Override
                  public int read(byte b[]) throws IOException {
                    return MultiGZIPInputStream.this.read(b);
                  }

                  @Override
                  public int read(byte b[], int off, int len) throws IOException {
                    return MultiGZIPInputStream.this.read(b, off, len);
                  }

                  @Override
                  public void close() {
                    // do nothing when close requested
                  }
                };
            }
            
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };   
    }
    
    /**
     * @return True if we found another record in the stream.
     */
    protected boolean moveToNextGzipMember() {
        boolean result = false;
        // Move to the next gzip member, if there is one, positioning
        // ourselves by backing up the stream so we reread any inflater
        // remaining bytes. Then add 8 bytes to get us past the GZIP
        // CRC trailer block that ends all gzip members.
        try {
            // 8 is sizeof gzip CRC block thats on tail of gzipped
            // record. If remaining is < 8 then experience indicates
            // we're seeking past the gzip header -- don't backup the
            // stream.
            if (this.inf.getRemaining() > GZIP_TRAILER_LENGTH) {
                raf.seek(raf.getFilePointer() - this.inf.getRemaining()
                         + GZIP_TRAILER_LENGTH);
                this.resetInflater();
            }
            for (int read = -1, headerRead = 0; true; headerRead = 0) {
                // Give a hint to underlying stream that we're going to want to
                // do some backing up.
                getInputStream().mark(3);
                if ((read = getInputStream().read()) == -1) {
                    break;
                }
                if(compareBytes(read, GZIPInputStream.GZIP_MAGIC)) {
                    headerRead++;
                    if ((read = getInputStream().read()) == -1) {
                    	break;
                    }
                    if(compareBytes(read, GZIPInputStream.GZIP_MAGIC >> 8)) {
                        headerRead++;
                        if ((read = getInputStream().read()) == -1) {
                        	break;
                        }
                        if (compareBytes(read, Deflater.DEFLATED)) {
                            headerRead++;
                            // Found gzip header. Backup the stream the
                            // bytes we just found and set result true.
                            getInputStream().reset();
                            result = true;
                            break;
                        }
                    }
                    // Didn't find gzip header.  Reset stream but one byte
                    // futher on then redo header tests.
                    raf.seek(raf.getFilePointer() - headerRead);
                }
            }
            this.resetInflater();
        } catch (IOException e) {
            throw new RuntimeException("Failed i/o: " + e.getMessage());
        }
        return result;
    }
    
    protected boolean compareBytes(final int a, final int b) {
    	return ((byte)(a & 0xff)) == ((byte)(b & 0xff));
    }
  
    protected InputStream getInputStream() {
        return this.in;
    }
    
    /**
     * Move to next gzip member in the file.
     */
    protected void resetInflater() {
        this.eos = false;
        this.inf.reset();
    }
    
    
    /**
     * Read in the gzip header.
     * @throws IOException
     */
    protected void readHeader() throws IOException {
        new GZIPHeader(this.in);
        // Reset the crc for subsequent reads.
        this.crc.reset();
    }
    
    /**
     * Seek to a gzip member.
     * 
     * Moves stream to new position, resets inflater and reads in the gzip
     * header ready for subsequent calls to read.
     * 
     * @param position Absolute position of a gzip member start.
     * @throws IOException
     */
    public void gzipMemberSeek(long position) throws IOException {
        raf.seek(position);
        resetInflater();
        readHeader();
    }
    
    public void gzipMemberSeek() throws IOException {
    	resetInflater();
    	readHeader();
    }
    
    
    /**
     * Read in the GZIP header.
     * 
     * See RFC1952 for specification on what the header looks like.
     * Assumption is that stream is cued-up with the gzip header as the
     * next thing to be read.
     * 
     * <p>Of <a href="http://jguru.com/faq/view.jsp?EID=13647">Java
     * and unsigned bytes</a>. That is, its always a signed int in
     * java no matter what the qualifier whether byte, char, etc.
     * 
     * <p>Add accessors for optional filename, comment and MTIME.
     * 
     * @author stack
     */
    private static class GZIPHeader {
        /**
         * Length of minimal GZIP header.
         *
         * See RFC1952 for explaination of value of 10.
         */
        public static final int MINIMAL_GZIP_HEADER_LENGTH = 10;
        
        /**
         * Total length of the gzip header.
         */
        protected int length = 0;

        /**
         * The GZIP header FLG byte.
         */
        protected int flg;
        
        /**
         * GZIP header XFL byte.
         */
        private int xfl;
        
        /**
         * GZIP header OS byte.
         */
        private int os;
        
        /**
         * Extra header field content.
         */
        private byte [] fextra = null;
        
        /**
         * GZIP header MTIME field.
         */
        private int mtime;
        
        
        /**
         * Shutdown constructor.
         * 
         * Must pass an input stream.
         */
        public GZIPHeader() {
            super();
        }
        
        /**
         * Constructor.
         * 
         * This constructor advances the stream past any gzip header found.
         * 
         * @param in InputStream to read from.
         * @throws IOException
         */
        public GZIPHeader(InputStream in) throws IOException {
            super();
            readHeader(in);
        }
        
        /**
         * Read in gzip header.
         * 
         * Advances the stream past the gzip header.
         * @param in InputStream.
         * 
         * @throws IOException Throws if does not start with GZIP Header.
         */
        public void readHeader(InputStream in) throws IOException {
            CRC32 crc = new CRC32();
            crc.reset();
            if (!testGzipMagic(in, crc)) {
                throw new NoGzipMagicException();
            }
            this.length += 2;
            if (readByte(in, crc) != Deflater.DEFLATED) {
                throw new IOException("Unknown compression");
            }
            this.length++;
           
            // Get gzip header flag.
            this.flg = readByte(in, crc);
            this.length++;
            
            // Get MTIME.
            this.mtime = readInt(in, crc);
            this.length += 4;
            
            // Read XFL and OS.
            this.xfl = readByte(in, crc);
            this.length++;
            this.os = readByte(in, crc);
            this.length++;
            
            // Skip optional extra field -- stuff w/ alexa stuff in it.
            final int FLG_FEXTRA = 4;
            if ((this.flg & FLG_FEXTRA) == FLG_FEXTRA) {
                int count = readShort(in, crc);
                this.length +=2;
                this.fextra = new byte[count];
                readByte(in, crc, this.fextra, 0, count);
                this.length += count;
            }   
            
            // Skip file name.  It ends in null.
            final int FLG_FNAME  = 8;
            if ((this.flg & FLG_FNAME) == FLG_FNAME) {
                while (readByte(in, crc) != 0) {
                    this.length++;
                }
            }   
            
            // Skip file comment.  It ends in null.
            final int FLG_FCOMMENT = 16;   // File comment
            if ((this.flg & FLG_FCOMMENT) == FLG_FCOMMENT) {
                while (readByte(in, crc) != 0) {
                    this.length++;
                }
            }
            
            // Check optional CRC.
            final int FLG_FHCRC  = 2;
            if ((this.flg & FLG_FHCRC) == FLG_FHCRC) {
                int calcCrc = (int)(crc.getValue() & 0xffff);
                if (readShort(in, crc) != calcCrc) {
                    throw new IOException("Bad header CRC");
                }
                this.length += 2;
            }
        }
        
        /**
         * Test gzip magic is next in the stream.
         * Reads two bytes.  Caller needs to manage resetting stream.
         * @param in InputStream to read.
         * @return true if found gzip magic.  False otherwise
         * or an IOException (including EOFException).
         * @throws IOException
         */
        public boolean testGzipMagic(InputStream in) throws IOException {
            return testGzipMagic(in, null);
        }
        
        /**
         * Test gzip magic is next in the stream.
         * Reads two bytes.  Caller needs to manage resetting stream.
         * @param in InputStream to read.
         * @param crc CRC to update.
         * @return true if found gzip magic.  False otherwise
         * or an IOException (including EOFException).
         * @throws IOException
         */
        public boolean testGzipMagic(InputStream in, CRC32 crc)
                throws IOException {
            return readShort(in, crc) == GZIPInputStream.GZIP_MAGIC;
        }
        
        /**
         * Read an int. 
         * 
         * We do not expect to get a -1 reading.  If we do, we throw exception.
         * Update the crc as we go.
         * 
         * @param in InputStream to read.
         * @param crc CRC to update.
         * @return int read.
         * 
         * @throws IOException
         */
        private int readInt(InputStream in, CRC32 crc) throws IOException {
            int s = readShort(in, crc);
            return ((readShort(in, crc) << 16) & 0xffff0000) | s;
        }
        
        /**
         * Read a short. 
         * 
         * We do not expect to get a -1 reading.  If we do, we throw exception.
         * Update the crc as we go.
         * 
         * @param in InputStream to read.
         * @param crc CRC to update.
         * @return Short read.
         * 
         * @throws IOException
         */
        private int readShort(InputStream in, CRC32 crc) throws IOException {
            int b = readByte(in, crc);
            return ((readByte(in, crc) << 8) & 0x00ff00) | b;
        }
        
        /**
         * Read a byte. 
         * 
         * We do not expect to get a -1 reading.  If we do, we throw exception.
         * Update the crc as we go.
         * 
         * @param in InputStream to read.
         * @return Byte read.
         * 
         * @throws IOException
         */
        protected int readByte(InputStream in) throws IOException {
                return readByte(in, null);
        }
        
        /**
         * Read a byte. 
         * 
         * We do not expect to get a -1 reading.  If we do, we throw exception.
         * Update the crc as we go.
         * 
         * @param in InputStream to read.
         * @param crc CRC to update.
         * @return Byte read.
         * 
         * @throws IOException
         */
        protected int readByte(InputStream in, CRC32 crc) throws IOException {
            int b = in.read();
            if (b == -1) {
                throw new EOFException();
            }
            if (crc != null) {
                crc.update(b);
            }
            return b & 0xff;
        }
        
        /**
         * Read a byte. 
         * 
         * We do not expect to get a -1 reading.  If we do, we throw exception.
         * Update the crc as we go.
         * 
         * @param in InputStream to read.
         * @param crc CRC to update.
         * @param buffer Buffer to read into.
         * @param offset Offset to start filling buffer at.
         * @param length How much to read.
         * @return Bytes read.
         * 
         * @throws IOException
         */
        protected int readByte(InputStream in, CRC32 crc, byte [] buffer,
                    int offset, int length)
                throws IOException {
            for (int i = offset; i < length; i++) {
                buffer[offset + i] = (byte)readByte(in, crc);   
            }
            return length;
        }
        
        /**
         * @return Returns the fextra.
         */
        public byte[] getFextra() {
            return this.fextra;
        }
        
        /**
         * @return Returns the flg.
         */
        public int getFlg() {
            return this.flg;
        }
        
        /**
         * @return Returns the os.
         */
        public int getOs() {
            return this.os;
        }
        
        /**
         * @return Returns the xfl.
         */
        public int getXfl() {
            return this.xfl;
        }
        
        /**
         * @return Returns the mtime.
         */
        public int getMtime() {
            return this.mtime;
        }
        
        /**
         * @return Returns the length.
         */
        public int getLength() {
            return length;
        }
    }

    public static class NoGzipMagicException extends IOException {

        private static final long serialVersionUID = 3084169624430655013L;

        public NoGzipMagicException() {
            super();
        }
    }
}
