package st.foglo.stateless_proxy;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.ArrayList;


public class SipMessage {
	
	final TYPE type;
	
	/**
	 * A string; no CR LF at the end
	 */
	final String firstLine;
	
	/**
	 * Keys in this map is canonical header names like Call-ID, Via, etc
	 * 
	 * Values are lists of as-received strings, no CR LF at end
	 */
	final Map<String, List<String>> headers = new HashMap<String, List<String>>();
	
	/**
	 * One long string, with embedded CR LF sequences
	 */
	final String body;
	
	enum TYPE {
		request,
		response
	}
	
	enum STATE {
		scanFirstLine,
		scanFirstLineCrSeen, scanHeaders, scanHeadersCrSeen
	, scanBody}
	
	
	public SipMessage(byte[] buffer) {
		
		String firstLine = null;
		STATE state = STATE.scanFirstLine;
		StringBuilder sb = new StringBuilder();
		byte b;
		for (int k = 0; k < buffer.length; k++) {
			b = buffer[k];
			
			if (state == STATE.scanFirstLine || state == STATE.scanFirstLineCrSeen) {
				if (b == 13) {
					state = STATE.scanFirstLineCrSeen;
				}
				else if (b == 10 && state == STATE.scanFirstLineCrSeen) {
					firstLine = new String(sb);
					state = STATE.scanHeaders;
					sb = new StringBuilder();
				}
				else {
					sb.append((char)b);
				}
			}
			else if (state == STATE.scanHeaders || state == STATE.scanHeadersCrSeen) {
				if (b == 13) {
					state = STATE.scanHeadersCrSeen;
				}
				else if (b == 10 && state == STATE.scanHeadersCrSeen) {
					String headerLine = new String(sb);
					sb = new StringBuilder();
					
					if (headerLine.isEmpty()) {
						state = STATE.scanBody;
					}
					else if (!headerLine.isEmpty()) {
						collectHeaderLine(headerLine);
						state = STATE.scanHeaders;
					}
				}
				else {
					sb.append((char)b);
				}
			}
			else if (state == STATE.scanBody) {
				sb.append((char)b);
			}
		}
		
		if (state == STATE.scanBody) {
			this.body = new String(sb);
		}
		else if (state == STATE.scanHeaders) {
			this.body = null;
		}
		else {
			// internal error
			this.body = null;
		}

		this.firstLine = firstLine;
		
		this.type = firstLine.startsWith("SIP/2.0") ? TYPE.response : TYPE.request;

	}

	
	////////////////////////
	
	private void collectHeaderLine(String headerLine) {
		
		int indexColon = headerLine.indexOf(':');
		String key = headerLine.substring(0, indexColon);
		
		if (headers.get(key) == null) {
			List<String> hh = new ArrayList<String>();
			hh.add(headerLine);
			headers.put(key, hh);
		}
		else {
			List<String> hh = headers.get(key);
			hh.add(headerLine);
		}
	}
	
	public String toString() {
		final byte[] crLf = new byte[]{13, 10};
		final StringBuilder sb = new StringBuilder();
		sb.append(firstLine);
		sb.append(Character.valueOf((char) crLf[1]));
		
		for (Entry<String, List<String>> me : headers.entrySet()) {
			List<String> hh = me.getValue();
			for (String s : hh) {
				sb.append(s);
				sb.append(Character.valueOf((char) crLf[1]));
			}
		}
		if (body != null) {
			sb.append(Character.valueOf((char) crLf[1]));
			sb.append(body);
		}
		return new String(sb);
	}
}
