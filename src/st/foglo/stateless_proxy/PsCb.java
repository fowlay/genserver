package st.foglo.stateless_proxy;

import java.util.List;
import java.util.Map;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.genserver.Atom;

/**
 * PortSender
 * 
 * Receives outgoing messages in instance format, and passes it to the associated
 * listener process in ready-to-send format
 */
public final class PsCb implements CallBack {

	public class State {
		Side side = null;
		Object peerAddr = null;
		Object peerPort = null;
		
		final GenServer pl;
		
		public State(Side side, Object peerAddr, Object peerPort, GenServer pl) {
			super();
			this.side = side;
			this.peerAddr = peerAddr;
			this.peerPort = peerPort;
			this.pl = pl;
		}
	}
	
	
	
	////////////////////////////////
	
	@Override
	public CallResult init(Object args) {
		
		Object[] aa = (Object[]) args;

		return new CallResult(Atom.OK, null, new State((Side)aa[0], null, null, (GenServer)aa[1]), 2000);
	}

	@Override
	public CallResult handleCast(Object message, Object state) {
		// send a datagram
		MsgBase mb = (MsgBase) message;
		if (mb instanceof InternalSipMessage) {
			
			InternalSipMessage ism = (InternalSipMessage) mb;
			
			GenServer pl = ((State)state).pl;
			
			byte[] ba = toByteArray(ism.message);
			
			BufferMsg bm = new BufferMsg(ba, null, null);
			
			pl.cast(bm);
			
			return new CallResult(Atom.NOREPLY, state);
		}
		
		
		
		
		return new CallResult(Atom.NOREPLY, state);
	}
	
	
	
	
	

	private byte[] toByteArray(SipMessage message) {
		
		byte[] ba = new byte[10000];   // TODO, ugly
		int k = 0;
		
		String firstLine = message.firstLine;
		
		for (byte b : firstLine.getBytes()) {
			ba[k++] = b;
		}
		ba[k++] = 13;
		ba[k++] = 10;
		
		
		Map<String, List<String>> hh  = message.headers;
		
		for (List<String> ss : hh.values()) {
			for (String s : ss) {
				for (byte b : s.getBytes()) {
					ba[k++] = b;
				}
				ba[k++] = 13;
				ba[k++] = 10;
			}
		}

		if (message.body != null) {
			ba[k++] = 13;
			ba[k++] = 10;
			
			for (byte b : message.body.getBytes()) {
				ba[k++] = b;
			}
		}
		
		byte[] result = new byte[k];
		
		for (int j = 0; j < k; j++) {
			result[j] = ba[j];
		}
		
		return result;
	}

	@Override
	public CallResult handleCall(Object message, Object state) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {
		System.out.println(String.format("portsender timeout: %s", ((State)state).side));
		return new CallResult(Atom.NOREPLY, state);
	}

	@Override
	public void handleTerminate(Object state) {
		// TODO Auto-generated method stub

	}

}
