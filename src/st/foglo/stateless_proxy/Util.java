package st.foglo.stateless_proxy;

import java.time.LocalDateTime;

public final class Util {

    public enum Mode {
        START,                // start sequence
        SIP,
        SIPDEBUG,
        KEEP_ALIVE,
        DEBUG,
        IGNORE,
        GENSERVER             // provisional
    }
	
	public enum Direction {
		IN,
		OUT,
		NONE,
		UE,
		SP
	}
	
	private static final String[] white = new String[]{
			"",
			"               ",
			"                              ",
			"                                             ",
			"                                                            ",
			"                                                                           "
	};
	

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
	
	public static void trace(Level level, String format, boolean s) {
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
			final String threadName = Thread.currentThread().getName();
			System.out.println(
			String.format("%s %-15s %s%n", ldt(), threadName, line));
		}
	}
	






	public static void seq(Mode mode, Side where, Direction towards, String text) {
		
        if (!isMember(mode, Main.TRACE_MODES)) {
            return;
        }
		
		final String arrowLeft = "<--";
		final String arrowRight = "-->";
		
		final String arrow;
		if (towards == Direction.NONE) {
			arrow = "";
		}
		else if (towards == Direction.IN && where == Side.UE) {
			arrow = arrowRight;
		}
		else if (towards == Direction.IN && where == Side.SP) {
			arrow = arrowLeft;
			
		}
		else if (towards == Direction.OUT && where == Side.UE) {
			arrow = arrowLeft;
		}
		else if (towards == Direction.OUT && where == Side.SP) {
			arrow = arrowRight;
		}
		else if (towards == Direction.UE && where == Side.PX) {
			arrow = arrowLeft;
		}
		else if (towards == Direction.SP && where == Side.PX) {
			arrow = arrowRight;
		}
		else {
			throw new RuntimeException();
		}
		
		String whiteSpace =
            where == Side.UE ? white[1] :
                where == Side.PX ? white[3] :
                    where == Side.SP ? white[5] :
                        where == Side.GS ? white[0] :
                            "internal error";
		

        final Level[] levels = Level.values();
		display(levels[0], String.format("%s%s %s", whiteSpace, arrow, text));
	}


    /**
     * Deprecated - use seq(Mode ...)
     * @param level
     * @param where
     * @param towards
     * @param text
     */
	public static void seq(Level level, Side where, Direction towards, String text) {
		
		//System.out.println(String.format("%s|%s|%s|%s", level, where, towards, text));
		
		final String arrowLeft = "<--";
		final String arrowRight = "-->";
		
		final String arrow;
		if (towards == Direction.NONE) {
			arrow = "";
		}
		else if (towards == Direction.IN && where == Side.UE) {
			arrow = arrowRight;
		}
		else if (towards == Direction.IN && where == Side.SP) {
			arrow = arrowLeft;
			
		}
		else if (towards == Direction.OUT && where == Side.UE) {
			arrow = arrowLeft;
		}
		else if (towards == Direction.OUT && where == Side.SP) {
			arrow = arrowRight;
		}
		else if (towards == Direction.UE && where == Side.PX) {
			arrow = arrowLeft;
		}
		else if (towards == Direction.SP && where == Side.PX) {
			arrow = arrowRight;
		}
		else {
			throw new RuntimeException();
			
		}
		
		String whiteSpace =
				where == Side.UE ? white[0] :
					where == Side.PX ? white[2] :
						where == Side.SP ? white[4] :
							"internal error"; 
		
		display(level, String.format("%s%s %s", whiteSpace, arrow, text));
	}

	public static String ldt() {
		final String ldt = LocalDateTime.now().toString();
		final String date = ldt.substring(0, 10);
		final String time = ldt.substring(11);
		if (time.length() > 12) {
			return String.format("%s %s", date, time.substring(0, 12));
		}
		else {
			return String.format("%s %s", date, time);
		}
	}
	


	public static String bytesToString(byte[] buffer, int recLength) {
		StringBuilder sb = new StringBuilder();
		for (int k = 0; k < recLength; k++) {
			sb.append(String.format("%d ", (int)buffer[k]));
		}
		return sb.toString();
	}

	public static String bytesToIpAddress(byte[] buffer) {
		final StringBuilder sb = new StringBuilder();
		for (int j = 0; j < buffer.length; j++) {
			final int code = buffer[j] < 0 ? buffer[j]+256 : buffer[j];
			if (j == 0) {
				sb.append(String.format("%d", code));
			}
			else {
				sb.append(String.format(".%d", code));
			}
		}
		return sb.toString();
	}

    private static boolean isMember(Mode m, Mode[] mm) {

        for (Mode e : mm) {
            if (m == e) {
                return true;
            }
        }
        return false;
    }

    /**
     * Merge two tag strings; the result is independent of argument order.
     * @param a
     * @param b
     * @return
     */
    public static String mergeStrings(String a, String b) {

        if (b.length() > a.length()) {
            return mergeStrings(b, a);
        } else {
            // a is longer than b, or equal length
            final char[] aa = a.toCharArray();
            final char[] bb = b.toCharArray();
            final StringBuilder sb = new StringBuilder();
            for (int j = 0; j < bb.length; j++) {
                final int c = 33 + (((int) aa[j]) + ((int) bb[j])) % 94;
                sb.append((char) c);
            }
            for (int k = bb.length; k < aa.length; k++) {
                sb.append(aa[k]);
            }
            return sb.toString();
        }
    }
}

