package st.foglo.stateless_proxy;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.genserver.Keyword;

/**
 * The port listener.
 */
public class PlCb implements CallBack {
	
	public class PortListenerState {
		final Atom side;
		final GenServer proxy;
		
		Object peerAddr = null;              // TODO, instantiate
		Object peerPort = null;
		Object listenerPort = null;
		
		public PortListenerState(Atom side, GenServer aa) {
			super();
			this.side = side;
			this.proxy = aa;
		}
	}
	
	///////////////////////////////////

	@Override
	public CallResult init(Object args) {
		Object[] aa = (Object[])args;
		return new CallResult(Keyword.ok, null, new PortListenerState((Atom)aa[0], (GenServer)aa[1]), 3000);
				
	}

	@Override
	public CallResult handleCast(Object message, Object state) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CallResult handleCall(Object message, Object state) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {
		final PortListenerState plState = (PortListenerState)state;
		System.out.println(String.format("timed out: %s", plState.side));
		return new CallResult(Keyword.noreply, state);
	}

	@Override
	public void handleTerminate(Object state) {
		// TODO Auto-generated method stub

	}

}
