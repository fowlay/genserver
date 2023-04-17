package st.foglo.stateless_proxy;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Keyword;

/**
 * PortSender
 */
public final class PsCb implements CallBack {

	public class State {
		Atom id = null;
		Object peerAddr = null;
		Object peerPort = null;
		
		public State(Atom id, Object peerAddr, Object peerPort) {
			super();
			this.id = id;
			this.peerAddr = peerAddr;
			this.peerPort = peerPort;
		}
		
		
		
	}
	
	
	
	////////////////////////////////
	
	@Override
	public CallResult init(Object args) {
		
		Object[] aa = (Object[]) args;
		
		
		return new CallResult(Keyword.ok, null, new State((Atom)aa[0], null, null), 2000);
	}

	@Override
	public CallResult handleCast(Object message, Object state) {
		// send a datagram
		return new CallResult(Keyword.noreply, state);
	}

	@Override
	public CallResult handleCall(Object message, Object state) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {
		System.out.println(String.format("portsender timeout: %s", ((State)state).id));
		return new CallResult(Keyword.noreply, state);
	}

	@Override
	public void handleTerminate(Object state) {
		// TODO Auto-generated method stub

	}

}
