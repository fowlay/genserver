package st.foglo.genserver;


public final class CallResult {
    
	public static final long TIMEOUT_ZERO = 0;
	public static final long TIMEOUT_NEVER = -1;
	
    final Atom code;
    final Object reply;
    final long timeoutMillis;
    
    //////////////////////////////////
    
    public CallResult(Atom word) {
    	this(word, null, TIMEOUT_NEVER);
    }
    
    public CallResult(Atom word, Object reply) {
    	this(word, reply, TIMEOUT_NEVER);
    }
    
    
    public CallResult(Atom word, long timeoutMillis) {
    	this(word, null, timeoutMillis);
    }

    
    public CallResult(Atom word, Object reply, long timeoutMillis) {
        this.code = word;
        this.reply = reply;
        this.timeoutMillis = timeoutMillis;
    }

    public String toString() {
        return String.format("[%s %s %d]",
        code.toString(), reply == null ? "noreply" : "reply", timeoutMillis);
    }
}


