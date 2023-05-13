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


    public static abstract class ResultBase {

        final Atom code;
        final long timeoutMillis;

        public ResultBase(Atom code, long timeoutMillis) {
            this.code = code;
            this.timeoutMillis = timeoutMillis;
        }
    }

    public static final class InitResult extends ResultBase {
        public InitResult(Atom code) {
            this(code, CallBack.TIMEOUT_NEVER);
        }
        public InitResult(Atom code, long timeoutMillis) {
            super(code, timeoutMillis);
            if (!(code == Atom.OK || code == Atom.IGNORE)) {
                throw new IllegalArgumentException();
            }
        }
    }

    public static final class CastResult extends ResultBase {
        public CastResult(Atom code) {
            this(code, CallBack.TIMEOUT_NEVER);
        }
        public CastResult(Atom code, long timeoutMillis) {
            super(code, timeoutMillis);
            if (!(code == Atom.NOREPLY || code == Atom.STOP)) {
                throw new IllegalArgumentException();
            }
        }
    }

    public static final class InfoResult extends ResultBase {
        public InfoResult(Atom code) {
            this(code, CallBack.TIMEOUT_NEVER);
        }
        public InfoResult(Atom code, long timeoutMillis) {
            super(code, timeoutMillis);
            if (!(code == Atom.NOREPLY || code == Atom.STOP)) {
                throw new IllegalArgumentException();
            }
        }
    }

    public static final class CallResult extends ResultBase {
        final Object reply;

        public CallResult(Atom code) {
            this(code, null);
        }

        public CallResult(Atom code, Object reply) {
            this(code, reply, CallBack.TIMEOUT_NEVER);
        }

        public CallResult(Atom code, Object reply, long timeoutMillis) {
            super(code, timeoutMillis);
            this.reply = reply;
            if (!(code == Atom.REPLY || code == Atom.STOP)) {
                throw new IllegalArgumentException();
            }
        }
    }


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

    public void cast(Object object) {

        Util.seq(Mode.GENSERVER, Side.PX, Direction.NONE,
                String.format("gsForward thread name is: %s", getThread().getName()));

        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                String.format("about to insert! - we are using instance: %s %s", threadName,
                        getThread().getName()));
        insertMessage(new GsMessage(Atom.CAST, object));
        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, String.format(
                "about to MAYBE interrupt! - we are using instance: %s %s", threadName, getThread().getName()));
        if (accessIsHandlingInfo(false, false)) {
            Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "about to interrupt!");
            getThread().interrupt();
            Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "done interrupt!");
        }
    }
    
    public Object call(GenServer gs, Object object) {
        // TODO - eliminate the gs argument

        insertMessage(new GsMessage(Atom.CALL, object));
        if (accessIsHandlingInfo(false, false)) {
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
        
        InitResult icr = null;
        CallResult ccr = null;
        CastResult kcr = null; // TODO; improve locality

        try {
            icr = cb.init(args);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (icr.code == Atom.IGNORE) {
            return;
        }

        synchronized (monitorTimeout) {
            timeout = icr.timeoutMillis;
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
                           
                            accessIsHandlingInfo(true, true);

                            Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                                String.format("%s isHandlingInfo is: %b", Thread.currentThread().getName(), accessIsHandlingInfo(false, false)));
                            
                            InfoResult crInfo = null;
                            try {
                                crInfo = cb.handleInfo(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            accessIsHandlingInfo(true, false);

			                Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                                String.format("%s isHandlingInfo is: %b", Thread.currentThread().getName(), accessIsHandlingInfo(false, false)));
                                
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
                    kcr = null;
                    try {
                        kcr = cb.handleCast(m.object);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    if (kcr.timeoutMillis >= 0) {
                        synchronized (monitorTimeout) {
                            timeout = kcr.timeoutMillis;
                        }
                    }
                    
                    if (kcr.code == Atom.STOP) {
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
                        ccr = cb.handleCall(m.object);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    if (ccr.timeoutMillis >= 0) {
                        synchronized (monitorTimeout) {
                            timeout = ccr.timeoutMillis;
                        }
                    }
                    
                    synchronized(monitorCallResult) {
                        callResult = ccr;
                        try {
                            monitorCallResult.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (ccr.code == Atom.STOP) {
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
    
    private void insertMessage(GsMessage message) {
        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 1");
        final boolean wasEmpty;
        synchronized (monitorMessageQueue) {
            wasEmpty = messageQueue.isEmpty();
            messageQueue.add(message);
        }
        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 2");
        if (wasEmpty) {
            try {
                synchronized (monitorTimeout) {
                    Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 2.1");
                    timeout = -1;
                    Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 2.2");
                    monitorTimeout.notify();
                    Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 2.3");
                }
            } catch (IllegalMonitorStateException x) {
                x.printStackTrace();
            }
        }
        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "insertMessage 3");
    }

    public synchronized boolean accessIsHandlingInfo(boolean update, boolean value) {
        if (update) {
            final boolean oldValue = isHandlingInfo;
            isHandlingInfo = value;
            Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                String.format("%s isHandlingInfo: %b -> %b%n", threadName, oldValue, isHandlingInfo));
            return oldValue;
        }
        else {
	        Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
                String.format("%s isHandlingInfo: value retrieved: %b", threadName, isHandlingInfo));
            return isHandlingInfo;
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
