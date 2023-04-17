package st.foglo.stateless_proxy;

import java.util.HashMap;
import java.util.Map;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.genserver.Keyword;

/**
 * The stateless proxy.
 */
public final class PxCb implements CallBack {
	
	
	public class ProxyState {
		final Map<Atom, GenServer> portSenders = new HashMap<Atom, GenServer>();
		
		// no constructor yet .. to be added
	}
	
	///////////////////////////////////////////

	@Override
	public CallResult init(Object args) {
		return new CallResult(Keyword.ok, new ProxyState());
	}

	@Override
	public CallResult handleCast(Object message, Object state) {
		MsgBase mb = (MsgBase)message;
		if (mb.atom == Atom.portSenders) {
			
			PortSendersMsg plm = (PortSendersMsg)mb;
			
			ProxyState pxState = (ProxyState)state;
			pxState.portSenders.put(Atom.ue, plm.uePortSender);
			pxState.portSenders.put(Atom.sp, plm.spPortSender);
			
			return new CallResult(Keyword.noreply, state);
		}
		else {
			return null;
		}
	}

	@Override
	public CallResult handleCall(Object message, Object state) {
		return null;
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {
		return null;
	}

	@Override
	public void handleTerminate(Object state) {

	}
}
