package st.foglo.genserver;

public interface CallBack {
    
    public CallResult handleInit(Object args);
    
    public CallResult handleCast(Object message, Object state);
    
    public CallResult handleInfo(Object message, Object state);
    
    public CallResult handleCall(Object message, Object state);
    
    public void handleTerminate(Object state);
}
