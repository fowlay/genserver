package st.foglo.stateless_proxy;

public final class KeepAliveMessage extends MsgBase {
	
	final Side side;
	final byte[] buffer;
	
	
	public KeepAliveMessage(Side side, byte[] buffer) {
		super();
		this.side = side;
		this.buffer = buffer;
	}

}
