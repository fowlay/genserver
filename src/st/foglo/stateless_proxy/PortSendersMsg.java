package st.foglo.stateless_proxy;

import st.foglo.genserver.GenServer;

/**
 * Purpose: Inform proxy of sender processes and listener sockets
 */
public final class PortSendersMsg extends MsgBase {
	
	final GenServer uePortSender;
	final GenServer spPortSender;
	
	
	public PortSendersMsg(
			GenServer uePortSender,
			GenServer spPortSender) {
		super();
		this.uePortSender = uePortSender;
		this.spPortSender = spPortSender;
	}


}
