package st.foglo.stateless_proxy;



/**
 */
public final class UpdatePortSender extends MsgBase {

	
	
	final byte[] host;
	final Integer port;
	
	
	public UpdatePortSender(byte[] host, Integer port) {
		super();
		this.host = host;
		this.port = port;
	}
	
	
	
}
