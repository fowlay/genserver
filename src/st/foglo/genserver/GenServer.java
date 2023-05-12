package st.foglo.genserver;

import java.util.LinkedList;
import java.util.Queue;

import st.foglo.stateless_proxy.Side;
import st.foglo.stateless_proxy.Util;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Mode;

/**
 * Generic server; the behavior is provided in a class that implements
 * the CallBack interface.
 */
public final class GenServer implements Runnable {

    private final Object[] args;
    private final CallBack cb;

    private final Object monitorMessageQueue = new Object();
    private final Queue<GsMessage> messageQueue = new LinkedList<GsMessage>();

    public final Object monitorTimeout = new Object();
    public volatile long timeout = -1;
    
    private final Object monitorCallResult = new Object();
    private volatile CallResult callResult;
    
    private final Object monitorInitialized = new Object();
    private volatile boolean initialized = false;

    private volatile Thread thread;
    private String threadName = null; // gets assigned when run() is entered

    private volatile boolean isHandlingInfo = false;

    private class GsMessage {
        final Atom keyword;
        final Object object;
        
        GsMessage(Atom keyword, Object object) {
            this.keyword = keyword;
            this.object = object;
        }
    }

    //////////////////////////////////////////////////////////

    public GenServer(CallBack cb, Object[] args) {
        this(cb, args, 0);
    }
    
    public GenServer(CallBack cb, Object[] args, int msgLevelIgnoredForNow) {
        this.cb = cb;
        this.args = args;
    }

    /**
     * Start an anonymous GenServer process.
     */
    public static GenServer start(CallBack cb, Object[] args) {
        try {
            // Using system clock for thread name; ensure uniqueness
            // by waiting briefly; TODO something better
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return start(cb, args, String.format("%d", System.currentTimeMillis()));
    }
    
    public static GenServer start(CallBack cb, Object[] args, String name) {
        return start(cb, args, name, -1);
    }
    
    public static GenServer start(CallBack cb, Object[] args, String name, int trace) {
        final GenServer gs = new GenServer(cb, args, trace);
        final Thread thread = new Thread(gs, name);
        thread.start();
        gs.waitForInit();
        return gs;
    }
    
    private void waitForInit() {
        for (; true; ) {
            synchronized (monitorInitialized) {
                if (initialized) {
                    break;
                }
                else {
                    // how much will this loop spin?
                    Thread.yield();
                }
            }
        }
    }

    public void cast(GenServer gs, Object object) {
        // TODO - eliminate the gs argument

        Util.seq(Mode.GENSERVER, Side.PX, Direction.NONE,
                String.format("gsForward thread name is: %s", gs.getThread().getName()));

        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                String.format("about to insert! - we are using instance: %s %s", gs.threadName,
                        gs.getThread().getName()));
        insertMessage(gs, new GsMessage(Atom.CAST, object));
        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, String.format(
                "about to MAYBE interrupt! - we are using instance: %s %s", gs.threadName, gs.getThread().getName()));
        if (accessIsHandlingInfo(gs, false, false)) {
            Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "about to interrupt!");
            getThread().interrupt();
            Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "done interrupt!");
        }
    }
    
    public Object call(GenServer gs, Object object) {
        // TODO - eliminate the gs argument

        insertMessage(gs, new GsMessage(Atom.CALL, object));
        if (accessIsHandlingInfo(gs, false, false)) {
            getThread().interrupt();
        }

        for (; true; ) {
            synchronized (monitorCallResult) {
                if (callResult != null) {
                    final Object result = callResult.reply;
                    callResult = null;
                    monitorCallResult.notify();
                    return result;
                }
                else {
                    Thread.yield();
                }
            }
        }
    }
    
    @Override
    public void run() {
        
	Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
        String.format("start to run: %s", Thread.currentThread().getName()));

        if (threadName == null) {
            // anything else would be impossible?
            setThread(Thread.currentThread());
            threadName = Thread.currentThread().getName();
        }
        
        CallResult cr = null;

        try {
            cr = cb.init(args);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (cr.code == Atom.IGNORE) {
            return;
        }

        synchronized (monitorTimeout) {
            timeout = cr.timeoutMillis;
        }

        synchronized (monitorInitialized) {
            initialized = true;
        }

        for (; true; ) {

            final boolean isEmptyMessageQueue;
            synchronized (monitorMessageQueue) {
                isEmptyMessageQueue = messageQueue.isEmpty();
            }
            
            if (isEmptyMessageQueue) {

                    try {
                        if (timeout >= 0) {
                            if (timeout > 0) {
                                synchronized (monitorTimeout) {
                                    monitorTimeout.wait(timeout);
                                }
                            }

                            GsMessage message = new GsMessage(Atom.TIMEOUT, null);
                           
                            accessIsHandlingInfo(this, true, true);

                            Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                                String.format("%s isHandlingInfo is: %b", Thread.currentThread().getName(), accessIsHandlingInfo(this, false, false)));
                            
                            CallResult crInfo = null;
                            try {
                                crInfo = cb.handleInfo(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            accessIsHandlingInfo(this, true, false);

			                Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                                String.format("%s isHandlingInfo is: %b", Thread.currentThread().getName(), accessIsHandlingInfo(this, false, false)));
                                
                            if (crInfo.code == Atom.STOP) {
                                try {
                                    cb.handleTerminate();
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }
                                break;
                            }
                            else {
                                timeout = crInfo.timeoutMillis;
                            }

                        }
                        else {
                            synchronized (monitorTimeout) {
                                monitorTimeout.wait();
                            }
                        }
                        
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            
            }
            else {
                final GsMessage m;
                synchronized (monitorMessageQueue) {
                    m = messageQueue.remove();
                }
                
                if (m.keyword == Atom.CAST) {
                    cr = null;
                    try {
                        cr = cb.handleCast(m.object);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    if (cr.timeoutMillis >= 0) {
                        synchronized (monitorTimeout) {
                            timeout = cr.timeoutMillis;
                        }
                    }
                    
                    if (cr.code == Atom.STOP) {
                        try {
                            cb.handleTerminate();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }

                else if (m.keyword == Atom.CALL) {
                    try {
                        cr = cb.handleCall(m.object);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    if (cr.timeoutMillis >= 0) {
                        synchronized (monitorTimeout) {
                            timeout = cr.timeoutMillis;
                        }
                    }
                    
                    synchronized(monitorCallResult) {
                        callResult = cr;
                        try {
                            monitorCallResult.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (cr.code == Atom.STOP) {
                        try {
                            cb.handleTerminate();
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
        }
    }
    
    private void insertMessage(GenServer gs, GsMessage message) {
        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 1");
        final boolean wasEmpty;
        synchronized (gs.monitorMessageQueue) {
            wasEmpty = gs.messageQueue.isEmpty();
            gs.messageQueue.add(message);
        }
        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 2");
        if (wasEmpty) {
            try {
                synchronized (gs.monitorTimeout) {
                    Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 2.1");
                    gs.timeout = -1;
                    Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 2.2");
                    gs.monitorTimeout.notify();
                    Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 2.3");
                }
            } catch (IllegalMonitorStateException x) {
                x.printStackTrace();
            }
        }
        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 3");
    }

    public synchronized boolean accessIsHandlingInfo(GenServer gs, boolean update, boolean value) {
        if (update) {
            final boolean oldValue = gs.isHandlingInfo;
            gs.isHandlingInfo = value;
            Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                String.format("%s isHandlingInfo: %b -> %b%n", gs.threadName, oldValue, gs.isHandlingInfo));
            return oldValue;
        }
        else {
	        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                String.format("%s isHandlingInfo: value retrieved: %b", gs.threadName, gs.isHandlingInfo));
            return gs.isHandlingInfo;
        }
    }
    
    public Thread getThread() {
        return thread;
    }



    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public String getThreadName() {
        return threadName;
    }


    
    
    
//    private void trace(String s) {
//    	if (msgLevel >= 0) {
//    		System.err.println("GenServer: " + String.format(s));
//    	}
//    }
    
//    private void trace(String s, int j) {
//    	if (msgLevel >= 1) {
//    		System.err.println("GenServer: " + String.format(s, j));
//    	}
//    }
    
//    private void trace(String s, long j) {
//    	if (msgLevel >= 1) {
//    		System.err.println("GenServer: " + String.format(s, j));
//    	}
//    }
}
