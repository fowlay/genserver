package st.foglo.genserver;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Generic server; the behavior is provided in a class that implements
 * the CallBack interface.
 */
public final class GenServer implements Runnable {

    public static final TraceMode[] TRACE_MODES = new TraceMode[]{
        // TraceMode.DEBUG,
        // TraceMode.VERBOSE,
        // TraceMode.SILENT
    };

    enum TraceMode {
        SILENT,
        VERBOSE,
        DEBUG
    }

    /**
     * Named GenServer processes.
     */
    public static final ConcurrentMap<String, GenServer> registry =
            new ConcurrentHashMap<String, GenServer>();

    private final String name;

    private final Object[] args;
    private final CallBackBase cb;

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

        public String toString() {
            return String.format("%s, timeout: %d", code.toString(), timeoutMillis);
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

    public GenServer(CallBack cb, String name, Object[] args) {
        this(cb, name, args, 0);
    }
    
    public GenServer(CallBack cb, String name, Object[] args, int msgLevelIgnoredForNow) {
        this.cb = (CallBackBase) cb;
        this.name = name;
        this.args = args;
    }


    public static GenServer start(CallBack cb, Object[] args, String name) {
        return start(cb, args, name, false);
    }

    public static GenServer start(CallBack cb, Object[] args, String name, boolean register) {
        return start(cb, args, name, register, -1);
    }
   
    public static GenServer start(CallBack cb, Object[] args, String name, boolean register,
            int trace) {
        final GenServer gs = new GenServer(cb, name, args, trace);
        final Thread thread = new Thread(gs, name);
        thread.start();
        gs.waitForInit();
        if (register) {
            registry.put(name, gs);
        }
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

        // Util.seq(Mode.GENSERVER, Side.PX, Direction.NONE,
        //         String.format());

        trace(TraceMode.DEBUG, "gsForward thread name is: %s", getThread().getName());

        // Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
        //         String.format("about to insert! - we are using instance: %s %s", threadName,
        //                 getThread().getName()));

        trace(TraceMode.DEBUG, "about to insert! - we are using instance: %s %s", threadName, getThread().getName());

        insertMessage(new GsMessage(Atom.CAST, object));

        // Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, String.format(
        //         "about to MAYBE interrupt! - we are using instance: %s %s",
        //         threadName, getThread().getName()));
        trace(TraceMode.DEBUG, "about to MAYBE interrupt! - we are using instance: %s %s",
        threadName, getThread().getName());

        if (cb.canBeInterrupted()) {
        //     Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "about to interrupt!");

            trace(TraceMode.DEBUG, "%s", "about to interrupt!");

            Thread.yield();
            getThread().interrupt();

            // Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE, "done interrupt!");
            trace(TraceMode.DEBUG, "done interrupt!");
        }
    }
    
    public Object call(GenServer gs, Object object) {

        insertMessage(new GsMessage(Atom.CALL, object));

        if (cb.canBeInterrupted()) {
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
        
    // Util.seq(Mode.GENSERVER, Side.GS, Direction.NONE,
    //     String.format("start to run: %s", Thread.currentThread().getName()));
    trace(TraceMode.VERBOSE, "start to run: %s", Thread.currentThread().getName());

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
                            
                            InfoResult crInfo = null;
                            try {
                                crInfo = cb.handleInfo(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                                
                            if (crInfo.code == Atom.STOP) {
                                try {
                                    cb.handleTerminate();
                                    registry.remove(name);
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
                            registry.remove(name);
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
                            registry.remove(name);
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

        final boolean wasEmpty;
        synchronized (monitorMessageQueue) {
            wasEmpty = messageQueue.isEmpty();
            messageQueue.add(message);
        }
        if (wasEmpty) {
            try {
                synchronized (monitorTimeout) {
                    timeout = -1;
                    monitorTimeout.notify();
                }
            } catch (IllegalMonitorStateException x) {
                x.printStackTrace();
            }
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


    static void trace(TraceMode traceMode, String text) {
        if (isMember(traceMode, TRACE_MODES)) {
            traceHelper(text);
        }
    }

    static void trace(TraceMode traceMode, String format, String text) {
        if (isMember(traceMode, TRACE_MODES)) {
            traceHelper(format, text);
        }
    }

    static void trace(TraceMode traceMode, String format, String text1, String text2) {
        if (isMember(traceMode, TRACE_MODES)) {
            traceHelper(format, text1, text2);
        }
    }

    static void traceHelper(String text) {
        System.out.println(String.format("[GenServer] %s", text));
    }

    static void traceHelper(String format, String text) {
        traceHelper(String.format(format, text));
    }

    static void traceHelper(String format, String text1, String text2) {
        traceHelper(String.format(format, text1, text2));
    }

    static boolean isMember(TraceMode m, TraceMode[] mm) {
        for (TraceMode x : mm) {
            if (m == x) {
                return true;
            }
        }
        return false;
    }
}
