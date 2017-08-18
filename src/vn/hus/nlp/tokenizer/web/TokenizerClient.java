package vn.hus.nlp.tokenizer.web;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Jul 8, 2009, 11:37:55 AM
 * <p>
 */
public class TokenizerClient {
	String host;
	int port;
	
	private BufferedReader in;
	private BufferedWriter out;
	private Socket sock;
	
	/**
	 * Creates a tokenizer client
	 * @param host
	 * @param port
	 */
	public TokenizerClient(String host, int port){
		this.host = host;
		this.port = port;
	}
	
	/**
	 * @return
	 */
	public boolean connect(){
		try {
			sock = new Socket(host, port);
			in = new BufferedReader(new InputStreamReader(
					sock.getInputStream(), "UTF-8"));
			out = new BufferedWriter(new OutputStreamWriter(
					sock.getOutputStream(), "UTF-8"));
			return true;
		}
		catch (Exception e){
			System.out.println(e.getMessage());
			return false;
		}
	}
	
	/**
	 * @param data
	 * @return
	 */
	public String process(String data){
		try {
			out.write(data);
			out.write((char)0);
			out.flush();
			
			//Get data from server
			String result = "";
			while (true){				
				int ch = in.read();
				
				if (ch == 0) break;
				result += (char) ch;			
			}
			return result;
		}
		catch (Exception e){
			System.out.println(e.getMessage());
			return "";
		}
		
	}
	
	/**
	 * Closes the socket.
	 */
	public void close(){
		try {
			this.sock.close();
		}
		catch (Exception e){
			System.out.println(e.getMessage());
		}
	}
	
	public static void main(String [] args){
		if (args.length != 2){
			System.out.println("TokenizerClient [inputfile] [outputfile]");
			return;
		}
		
		try {
			// Create a tagging client, open connection
			TokenizerClient client = new TokenizerClient("localhost", 2929);			
			
			// read data from file
			// process data, save into another file			
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(args[0]), "UTF-8"));
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(args[1]), "UTF-8"));
			
			client.connect();
			String line;
			String input = "";			
			while ((line = reader.readLine()) != null){
				input += line + "\n";					
			}
			
			String result = client.process(input);
			writer.write(result + "\n");
			
			client.close();
			reader.close();
			writer.close();
			
		}
		catch (Exception e){
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
	
}
