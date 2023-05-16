package st.foglo.stateless_proxy;

public final class InternalSipMessage extends MsgBase {
	

	public final Side side;            // the side where this message entered the proxy
	final SipMessage message;
	
	final byte[] destAddr;
	final Integer destPort;
	
	final byte[] sourceAddr;       // optional, when receiving from UE side this may be filled in
	final Integer sourcePort;

    /**
     * May be changed by the proxy
     */
    public volatile boolean blocked = false;
	

    public InternalSipMessage(
			Side side,
			SipMessage message,
			byte[] destAddr,
			Integer destPort) {
		this(side,
				message,
				destAddr,
				destPort,
				null,
				null);
	}
	
	
	public InternalSipMessage(
			Side side,
			SipMessage message,
			byte[] destAddr,
			Integer destPort,
			byte[] sourceAddr,
			Integer sourcePort) {
		super();
		this.side = side;
		this.message = message;
		this.destAddr = destAddr;
		this.destPort = destPort;
		this.sourceAddr = sourceAddr;
		this.sourcePort = sourcePort;
	}


	
	/////////////////////////
	
	public InternalSipMessage setDestination(byte[] addr, Integer port) {
		return new InternalSipMessage(side, message, addr, port);
	}

    public synchronized boolean isBlocked() {
        return blocked;
    }
    
    public synchronized void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }
}
