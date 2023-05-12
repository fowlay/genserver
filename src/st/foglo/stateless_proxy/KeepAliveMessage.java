package st.foglo.stateless_proxy;

public final class KeepAliveMessage extends MsgBase {
	
	final Side side;
	final byte[] buffer;
	final int size;
	
	
	public KeepAliveMessage(Side side, byte[] buffer, int size) {
		this.side = side;
		this.buffer = buffer;
		this.size = size;
	}
	
	public String toString() {
		final StringBuilder sb = new StringBuilder("[");
		for (int k = 0; k < size; k++) {
			if (k > 0) {
				sb.append(",");
			}
			sb.append(String.format("%d", buffer[k]));
		}
        sb.append("]");
		return sb.toString();
	}
}
