package st.foglo.genserver;

import st.foglo.genserver.GenServer.CallResult;
import st.foglo.genserver.GenServer.CastResult;
import st.foglo.genserver.GenServer.InfoResult;
import st.foglo.genserver.GenServer.InitResult;

public interface CallBack {

    public static final long TIMEOUT_ZERO = 0;
    public static final long TIMEOUT_NEVER = -1;
    
	/**
	 * This method is called when a GenServer instance is started.
	 * Return as follows:
	 * 
	 * CallResult.code = ok | stop
	 * CallResult.timeoutMillis = -1 | 0..
	 */
    public InitResult init(Object[] args);
    
    /**
     * This method casts a message to the GenServer.
     * Return as follows:
     * 
     * CallResult.code = noreply | stop
     * CallResult.timeoutMillis = -1 | 0..
     */
    public CastResult handleCast(Object message);

    /**
     * This method implements a synchronous call.
     * Return as follows:
     * 
     * CallResult.code = reply | stop
     * CallResult.timeoutMillis = -1 | 0..
     */
    public CallResult handleCall(Object message);
    
    /**
     * This method is called upon timeout.
     * Return as follows:
     * 
     * CallResult.code = noreply | stop
     * CallResult.timeoutMillis = -1 | 0..
     */
    public InfoResult handleInfo(Object message);
    
    /**
     * This method is called when the server is terminating.
     */
    public void handleTerminate();
}
