package st.foglo.stateless_proxy;

public final class KeepAliveMessage extends MsgBase {
	
	final Side side;
	final byte[] buffer;
	int size;
	
	
	public KeepAliveMessage(Side side, byte[] buffer, int size) {
		super();
		this.side = side;
		this.buffer = buffer;
		this.size = size;
	}
	
	


}
