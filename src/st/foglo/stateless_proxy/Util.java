package st.foglo.stateless_proxy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class Util {
	


	// TODO, move elsewhere?
	public static byte[] toByteArray(int[] kk) {
		final byte[] result = new byte[kk.length];
		int j = 0;
		for (int k : kk) {
			result[j++] = (byte)k;
		}
		return result;
	}
	
	public enum Level {silent, verbose, debug};
	
	public static void trace(Level level, String s) {
		display(level, String.format("%s", s));
	}
	
	public static void trace(Level level, String format, int j) {
		display(level, String.format(format, j));
	}
	
	public static void trace(Level level, String format, String s) {
		display(level, String.format(format, s));
	}
	
	public static void trace(Level level, String format, String s, int j) {
		display(level, String.format(format, s, j));
	}
	
	public static void trace(Level level, String format, String s, int j, String t) {
		display(level, String.format(format, s, j, t));
	}
	
	public static void trace(Level level, String format, String s, int j, int k) {
		display(level, String.format(format, s, j, k));
	}
	
	public static void trace(Level level, String format, String s, String t) {
		display(level, String.format(format, s, t));
	}
	
	public static void trace(Level level, String format, String s, String t, int j) {
		display(level, String.format(format, s, t, j));
	}
	
	private static void display(Level level, String line) {
		if (Main.traceLevel.ordinal() >= level.ordinal()) {
			System.out.println(ldt() + " " + line);
			System.out.println();
		}
	}

	public static byte[] toByteArray(SipMessage message) {
		
		byte[] ba = new byte[10000];   // TODO, ugly
		int k = 0;
		
		String firstLine = message.firstLine;
		
		for (byte b : firstLine.getBytes()) {
			ba[k++] = b;
		}
		ba[k++] = 13;
		ba[k++] = 10;
		
		
		Map<String, List<String>> hh  = message.headers;
		
		for (List<String> ss : hh.values()) {
			for (String s : ss) {
				for (byte b : s.getBytes()) {
					ba[k++] = b;
				}
				ba[k++] = 13;
				ba[k++] = 10;
			}
		}
	
		if (message.body != null) {
			ba[k++] = 13;
			ba[k++] = 10;
			
			for (byte b : message.body.getBytes()) {
				ba[k++] = b;
			}
		}
		
		byte[] result = new byte[k];
		
		for (int j = 0; j < k; j++) {
			result[j] = ba[j];
		}
		
		return result;
	}
	
	public static Integer digest(byte[] buffer, int recLength) {
		int sum = 0;
		for (int i = 0; i < recLength; i++) {
			sum += (int)buffer[i];
		}
		return Integer.valueOf(sum);
	}
	
	public static String ldt() {
		final LocalDateTime ldt = LocalDateTime.now();
		return ldt.toString();
	}
	


	public static String bytesToString(byte[] buffer, int recLength) {
		StringBuilder sb = new StringBuilder();
		for (int k = 0; k < recLength; k++) {
			sb.append(String.format("%d ", (int)buffer[k]));
		}
		return sb.toString();
	}
}
