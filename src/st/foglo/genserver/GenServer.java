package st.foglo.genserver;

import java.util.Queue;

import java.util.LinkedList;

public final class GenServer implements Runnable {

    final private Object args;
    final private CallBack cb;
    
    private Object state;
    private Queue<GsMessage> q = new LinkedList<GsMessage>();
    private Object monitor = new Object();
    private Thread thread;  // maybe not needed
    
    //////////////////////////////////////////////////////////

    public GenServer(CallBack cb, Object args) {
        this.cb = cb;
        this.args = args;
    }

    /**
     * Start an unregistered GenServer process.
     */
    public static GenServer start(CallBack cb, Object args) {

    	final GenServer gs = new GenServer(cb, args);
    	final Thread thread = new Thread(gs);
    	gs.setThread(thread);
    	thread.start();
        return gs;

    }
    
    private void setThread(Thread thread) {
    	this.thread = thread;
    }

    @SuppressWarnings("removal")
    @Override
    public void run() {
    	
        state = cb.init(args);
        
        for (; true; ) {

        	if (q.isEmpty()) {

            	// System.out.println("suspending");
            	Thread.currentThread().suspend();
        		
        	}
        	else {
        		final GsMessage m;
        		synchronized(monitor) {
        			m = q.remove();
        		}
        		
        		if (m.keyword == Keyword.cast) {

        			CallResult cr = cb.cast(m.object, state);
        			
        			// check type of callresult .. exception?
        			
        			state = cr.newState;
        		}
        	}
        }
    }
    
    @SuppressWarnings("removal")
    public void deliverMessage(GsMessage message) {
    	synchronized(monitor) {
    		final boolean wasEmpty = q.isEmpty();
    		q.add(message);
    		if (wasEmpty) {
    			thread.resume();
    		}
    	}
    }
    
    public void cast(Object object) {
    	deliverMessage(new GsMessage(Keyword.cast, object));
    }
}
