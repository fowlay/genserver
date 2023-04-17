package st.foglo.stateless_proxy;

import st.foglo.genserver.GenServer;

public final class PortSendersMsg extends MsgBase {
	
	final GenServer uePortSender;
	final GenServer spPortSender;

	public PortSendersMsg(GenServer uePortSender, GenServer spPortSender) {
		super(Atom.portSenders);
		
		this.uePortSender = uePortSender;
		this.spPortSender = spPortSender;
	}
	
	

}
