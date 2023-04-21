package st.foglo.stateless_proxy;

public final class InternalSipMessage extends MsgBase {
	

	final Side side;
	final SipMessage message;
	
	final byte[] destAddr;
	final Integer destPort;
	
	public InternalSipMessage(Side side, SipMessage message, byte[] destAddr, Integer destPort) {
		super();
		this.side = side;
		this.message = message;
		this.destAddr = destAddr;
		this.destPort = destPort;
	}


	
	/////////////////////////

}
