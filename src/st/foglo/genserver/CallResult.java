package st.foglo.genserver;

public final class CallResult {
    
    final Keyword word;
    
    final Object newState;
    
    final Object reply;
    
    final long timeoutMillis;
    
    //////////////////////////////////

    public CallResult(Keyword word, Object newState, Object reply) {
    	this(word, newState, reply, -1);
    }
    
    public CallResult(Keyword word, Object newState, Object reply, int timeoutMillis) {
        this.word = word;
        this.newState = newState;
        this.reply = reply;
        this.timeoutMillis = timeoutMillis;
    }
}


