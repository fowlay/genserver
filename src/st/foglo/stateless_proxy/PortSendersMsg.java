package st.foglo.stateless_proxy;

import st.foglo.genserver.GenServer;

/**
 * Purpose: Inform proxy of sender processes
 */
public final class PortSendersMsg extends MsgBase {
	
	final GenServer uePortSender;
	final GenServer spPortSender;

	public PortSendersMsg(GenServer uePortSender, GenServer spPortSender) {
		this.uePortSender = uePortSender;
		this.spPortSender = spPortSender;
	}
}
