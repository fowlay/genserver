package st.foglo.stateless_proxy;

import java.util.HashMap;
import java.util.Map;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.genserver.Atom;

/**
 * The stateless proxy.
 */
public final class PxCb implements CallBack {
	
	
	public class State {
		final Map<Side, GenServer> portSenders = new HashMap<Side, GenServer>();
		
		// no constructor yet .. to be added
	}
	
	///////////////////////////////////////////

	@Override
	public CallResult init(Object args) {
		return new CallResult(Atom.OK, new State());
	}

	@Override
	public CallResult handleCast(Object message, Object state) {
		MsgBase mb = (MsgBase)message;
		if (mb instanceof PortSendersMsg) {
			
			PortSendersMsg plm = (PortSendersMsg)mb;
			
			State pxState = (State)state;
			pxState.portSenders.put(Side.UE, plm.uePortSender);
			pxState.portSenders.put(Side.SP, plm.spPortSender);
			
			return new CallResult(Atom.NOREPLY, state);
		}
		else if (mb instanceof InternalSipMessage) {
			
			InternalSipMessage ism = (InternalSipMessage)message;
			SipMessage sm = ism.message;
			
			Side side = ism.side;
			Side otherSide = otherSide(side);
			
			System.out.println("proxy received:");
			System.out.println(String.format("from: %s", ism.side));
			System.out.println(sm.toString());
			
			GenServer gsForward = ((State)state).portSenders.get(otherSide);
			
			
			// do some message processing according to RFC 3261
			
			gsForward.cast(ism);
			
			
			
			

			return new CallResult(Atom.NOREPLY, state);
		}
		else {
			return null;
		}
	}

	private Side otherSide(Side side) {
		return side == Side.UE ? Side.SP : Side.UE;
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
