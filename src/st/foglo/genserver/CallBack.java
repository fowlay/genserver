package st.foglo.genserver;

public interface CallBack {
    
    public Object init(Object args);
    
    public CallResult cast(Object message, Object state);
    
    public void info(Object state);
    
    public CallResult call(Object state);
    
    public void terminate(Object state);
}
