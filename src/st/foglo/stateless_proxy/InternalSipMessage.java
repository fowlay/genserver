package st.foglo.stateless_proxy;

public final class InternalSipMessage extends MsgBase {
	

	final Side side;
	final SipMessage message;

	public InternalSipMessage(Side side, SipMessage message) {
		this.side = side;
		this.message = message;
	}
	
	/////////////////////////

}
