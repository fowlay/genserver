package st.foglo.stateless_proxy;

/**
 * Represents a ready-to-send SIP message.
 */
public final class BufferMsg extends MsgBase {
	
	final byte[] destAddr;
	final Integer destPort;
	
	final byte[] buffer;

	public BufferMsg(byte[] buffer, byte[] destAddr, Integer destPort) {
		this.buffer = buffer;
		this.destAddr = destAddr;
		this.destPort = destPort;
	}
}
