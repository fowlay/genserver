package st.foglo.stateless_proxy;



/**
 * Purpose: Telling a port sender process to use
 * the address:port in this message.
 */
public final class UpdatePortSender extends MsgBase {

	final byte[] host;
	final Integer port;

	public UpdatePortSender(byte[] host, Integer port) {
		this.host = host;
		this.port = port;
	}
}
