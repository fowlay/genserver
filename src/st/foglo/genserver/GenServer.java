package st.foglo.genserver;

import java.util.LinkedList;
import java.util.Queue;
//import java.util.concurrent.ConcurrentLinkedQueue;

import st.foglo.stateless_proxy.Util;
import st.foglo.stateless_proxy.Util.Level;

public final class GenServer implements Runnable {

    private final Object[] args;
    private final CallBack cb;

    private Object monitorMessageQueue = new Object();
    // private final Queue<GsMessage> messageQueue = new ConcurrentLinkedQueue<GsMessage>();
    private final Queue<GsMessage> messageQueue = new LinkedList<GsMessage>();

    private Object monitorTimeout = new Object();
    private volatile long timeout = -1;
    
    private Object monitorCallResult = new Object();
    private volatile CallResult callResult;
    
    private Object monitorInitialized = new Object();
    private volatile boolean initialized = false;
    
    
    private Object state;
    
    private final int msgLevel;

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
    
    public GenServer(CallBack cb, Object[] args, int msgLevel) {
        this.cb = cb;
        this.args = args;
        this.msgLevel = msgLevel;
    }

    /**
     * Start an unregistered GenServer process.
     */
    public static GenServer start(CallBack cb, Object[] args) {
    	return start(cb, args, 0);
    }
    
    public static GenServer start(CallBack cb, Object[] args, int trace) {
    	final GenServer gs = new GenServer(cb, args, trace);
    	final Thread thread = new Thread(gs);
    	thread.start();
    	gs.waitForInit();
        return gs;
    }
    
    
    private void waitForInit() {
    	for (; true; ) {
    		synchronized (monitorInitialized) {
    			if (!initialized) {
    				Thread.yield();
    			}
    			else {
    				break;
    			}
    		}
    	}
    }

	public void cast(Object object) {
    	insertMessage(new GsMessage(Atom.CAST, object));
    }
    
    
    public Object call(Object object) {

    	insertMessage(new GsMessage(Atom.CALL, object));

    	for (; true; ) {

    		synchronized (monitorCallResult) {

    			if (callResult == null) {
    				Thread.yield();
    			}
    			else {
    				final Object result = callResult.reply;
    				callResult = null;
    				monitorCallResult.notify();
    				return result;
    			}
    		}
    	}
    }
    

    @Override
    public void run() {
    	
    	CallResult cr = cb.init(args);
    	synchronized (monitorTimeout) {
    		timeout = cr.timeoutMillis;
		}
    	synchronized (monitorInitialized) {
    		initialized = true;
		}

        state = cr.newState;
        
        for (; true; ) {

        	final boolean isEmptyMessageQueue;
        	synchronized (monitorMessageQueue) {
        		synchronized (messageQueue) {
        			isEmptyMessageQueue = messageQueue.isEmpty();
        		}
			}
        	
        	if (isEmptyMessageQueue) {

        		synchronized (monitorTimeout) {
        			try {
        				if (timeout >= 0) {
        					//trace("wait limited: %d", timeout);
        					if (timeout > 0) {
        						monitorTimeout.wait(timeout);
        					}
//        					if (timeout == -1) {
//        						trace("notified during limited wait");
//        						continue;
//        					}
//        					else {
            					// trace("continue after timeout");
            					GsMessage message = new GsMessage(Atom.TIMEOUT, null);
            					CallResult crInfo = cb.handleInfo(message, state);
            					
            					if (crInfo.code == Atom.STOP) {
            						cb.handleTerminate(crInfo.newState);
            						break;
            					}
            					else {
            						state = crInfo.newState;
            						timeout = crInfo.timeoutMillis;
            					}
//        					}
        				}
        				else {
        					monitorTimeout.wait();
        					trace("notified during infinite wait");
        				}
						
					} catch (InterruptedException e) {
						// should not happen?
						e.printStackTrace();
					}
				}
        	}
        	else {
        		final GsMessage m;
        		synchronized(monitorMessageQueue) {
        			synchronized (messageQueue) {
        				Util.trace(Level.verbose, "about to remove, thread: %s, queue size: %d", Thread.currentThread().toString(), messageQueue.size());
        				m = messageQueue.remove();
        				Util.trace(Level.verbose, "    done remove, thread: %s, queue size: %d", Thread.currentThread().toString(), messageQueue.size());
        				Util.trace(Level.verbose, "    done remove, thread: %s, queue stat: %s", Thread.currentThread().toString(), messageQueue.isEmpty() ? "empty" : "non-empty");
        			}
        		}
        		
        		if (m.keyword == Atom.CAST) {
        			cr = cb.handleCast(m.object, state);
        			state = cr.newState;
        			
        			if (cr.timeoutMillis >= 0) {
        				synchronized (monitorTimeout) {
							timeout = cr.timeoutMillis;
						}
        			}
        			
        			if (cr.code == Atom.STOP) {
        				cb.handleTerminate(state);
        			}
        		}
        		else if (m.keyword == Atom.CALL) {
        			// trace("perform a call");
        			cr = cb.handleCall(m.object, state);
        			// trace("callback executed");
        			state = cr.newState;
        			
        			if (cr.timeoutMillis >= 0) {
        				synchronized (monitorTimeout) {
							timeout = cr.timeoutMillis;
						}
        			}
        			
        			synchronized(monitorCallResult) {
        				// wait for result to get fetched
        				callResult = cr;
        				try {
        					monitorCallResult.wait();
        				} catch (InterruptedException e) {
        					e.printStackTrace();
        				}
        			}
        		}
        	}
        }
    }
    
    private void insertMessage(GsMessage message) {
    	final boolean wasEmpty;
    	synchronized(monitorMessageQueue) {
    		synchronized (messageQueue) {
        		wasEmpty = messageQueue.isEmpty();
        		Util.trace(Level.verbose, "before insert, thread: %s, size: %d", Thread.currentThread().toString(), messageQueue.size());
        		messageQueue.add(message);
        		Util.trace(Level.verbose, " after insert, thread: %s, size: %d", Thread.currentThread().toString(), messageQueue.size());
			}
    	}
    	
		if (wasEmpty) {
			synchronized (monitorTimeout) {
				timeout = -1;
				monitorTimeout.notify();
			}
		}
    }
    

    
    
    
    private void trace(String s) {
    	if (msgLevel >= 1) {
    		System.err.println("GenServer: " + String.format(s));
    	}
    }
    
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
