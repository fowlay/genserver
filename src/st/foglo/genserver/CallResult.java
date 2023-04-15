package st.foglo.genserver;

public final class CallResult {
    
    final Keyword word;
    
    final Object newState;
    
    final int timeoutMillis;
    
    //////////////////////////////////

    public CallResult(Keyword word, Object newState, int timeoutMillis) {
        this.word = word;
        this.newState = newState;
        this.timeoutMillis = timeoutMillis;
    }
}


