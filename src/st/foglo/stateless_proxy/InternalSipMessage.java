package st.foglo.stateless_proxy;

public final class InternalSipMessage extends MsgBase {
	

	final Side side;            // the side where this message entered the proxy
	final SipMessage message;
	
	final byte[] destAddr;
	final Integer destPort;
	
	final Integer digest;
	
	final byte[] sourceAddr;       // optional, when receiving from UE side this may be filled in
	final Integer sourcePort;
	
	public InternalSipMessage(
			Side side,
			SipMessage message,
			Integer digest,
			byte[] destAddr,
			Integer destPort) {
		this(side,
				message,
				digest,
				destAddr,
				destPort,
				null,
				null);
	}
	
	
	public InternalSipMessage(
			Side side,
			SipMessage message,
			Integer digest,
			byte[] destAddr,
			Integer destPort,
			byte[] sourceAddr,
			Integer sourcePort) {
		super();
		this.side = side;
		this.message = message;
		this.digest = digest;
		this.destAddr = destAddr;
		this.destPort = destPort;
		this.sourceAddr = sourceAddr;
		this.sourcePort = sourcePort;
	}


	
	/////////////////////////
	
	public InternalSipMessage setDestination(byte[] addr, Integer port) {
		return new InternalSipMessage(side, message, digest, addr, port);
	}

}
