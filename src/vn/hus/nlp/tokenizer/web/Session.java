package vn.hus.nlp.tokenizer.web;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Vector;

import vn.hus.nlp.tokenizer.VietTokenizer;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Jul 8, 2009, 11:41:32 AM
 * <p>
 * A tokenizer session.
 */
public class Session extends Thread {
	
	private VietTokenizer tokenizer;
	private Socket incoming;
	
	/**
	 * @param tokenizer
	 */
	public Session(VietTokenizer tokenizer){		
		this.tokenizer = tokenizer;
	}
		 	    
	/**
	 * @param s
	 */
	public synchronized void setSocket(Socket s){
		this.incoming = s;
		notify();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public synchronized void run(){	
		while (true){
			try {
				if (incoming == null) {
		            wait();	            
		        }
				
				System.out.println("Socket opening ...");
				BufferedReader in = new BufferedReader(new InputStreamReader(
						incoming.getInputStream(), "UTF-8"));				
				//PrintStream out = (PrintStream) incoming.getOutputStream();
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
						incoming.getOutputStream(), "UTF-8"));
				
				String content = "";
				
				while (true){				
					int ch = in.read();
					if (ch == 0) //end of string
						break;
					
					content += (char) ch;
				}
				// tokenize the content
				//
				StringBuffer result = new StringBuffer(1024);
				String[] sentences = tokenizer.tokenize(content);
				for (String s : sentences) {
					result.append(s);
				}
				
				// write out the result
				//
				out.write(result.toString().trim());
				out.write((char)0);
				out.flush();
			}
			catch (InterruptedIOException e){
				System.out.println("The connection is interrupted");	
			}
			catch (Exception e){
				System.out.println(e);
				e.printStackTrace();
			}
			
			//update pool
			//go back in wait queue if there is fewer than max
			this.setSocket(null);
			Vector<Session> pool = TokenizerService.pool;
			synchronized (pool) {
				if (pool.size() >= IConstants.MAX_NUMBER_SESSIONS){
					/* too many threads, exit this one*/
					return;
				}
				else {				
					pool.addElement(this);
				}
			}
		}
	}
}
