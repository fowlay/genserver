package st.foglo.genserver;

public final class CallResult {
    
    final Keyword code;
    
    final Object newState;
    
    final Object reply;
    
    final long timeoutMillis;
    
    //////////////////////////////////
    
    public CallResult(Keyword word, Object newState) {
    	this(word, null, newState);
    }

    public CallResult(Keyword word, Object reply, Object newState) {
    	this(word, reply, newState, -1);
    }
    
    public CallResult(Keyword word, Object reply, Object newState, int timeoutMillis) {
        this.code = word;
        this.newState = newState;
        this.reply = reply;
        this.timeoutMillis = timeoutMillis;
    }
}

