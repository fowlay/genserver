package st.foglo.genserver;

public interface CallBack {
    
	/**
	 * This method is called when a GenServer instance is started.
	 * Return as follows:
	 * 
	 * CallResult.code = ok | stop
	 * CallResult.newState = initial Object
	 * CallResult.timeoutMillis = -1 | 0..
	 */
    public CallResult init(Object args);
    
    /**
     * This method casts a message to the GenServer.
     * Return as follows:
     * 
     * CallResult.code = noreply | stop
     * CallResult.newState = Object
     * CallResult.timeoutMillis = -1 | 0..
     */
    public CallResult handleCast(Object message, Object state);

    /**
     * This method implements a synchronous call.
     * Return as follows:
     * 
     * CallResult.code = reply | stop
     * CallResult.newState = Object
     * CallResult.reply = Object
     * CallResult.timeoutMillis = -1 | 0..
     */
    public CallResult handleCall(Object message, Object state);
    
    /**
     * This method is called upon timeout.
     * Return as follows:
     * 
     * CallResult.code = noreply | stop
     * CallResult.newState = Object
     * CallResult.timeoutMillis = -1 | 0..
     */
    public CallResult handleInfo(Object message, Object state);
    
    /**
     * This method is called when the server is terminating.
     */
    public void handleTerminate(Object state);
}
