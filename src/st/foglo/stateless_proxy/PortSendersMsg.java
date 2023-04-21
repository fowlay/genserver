package st.foglo.stateless_proxy;

import st.foglo.genserver.GenServer;

/**
 * Purpose: Inform proxy of sender processes and listener sockets
 */
public final class PortSendersMsg extends MsgBase {
	
	final GenServer uePortSender;
	final GenServer spPortSender;
	
	final byte[] ueListenerAddress;
	final Integer ueListenerPort;
	
	final byte[] spListenerAddress;
	final Integer spListenerPort;
	
	public PortSendersMsg(GenServer uePortSender, GenServer spPortSender, byte[] ueListenerAddress,
	        Integer ueListenerPort, byte[] spListenerAddress, Integer spListenerPort) {
		super();
		this.uePortSender = uePortSender;
		this.spPortSender = spPortSender;
		this.ueListenerAddress = ueListenerAddress;
		this.ueListenerPort = ueListenerPort;
		this.spListenerAddress = spListenerAddress;
		this.spListenerPort = spListenerPort;
	}


}
