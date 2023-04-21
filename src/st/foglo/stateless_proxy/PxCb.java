package st.foglo.stateless_proxy;

import java.util.List;
import java.util.ArrayList;
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
		final Map<Side, byte[]> listenerAddresses = new HashMap<Side, byte[]>();
		final Map<Side, Integer> listenerPorts = new HashMap<Side, Integer>();
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
			
			pxState.listenerAddresses.put(Side.UE, plm.ueListenerAddress);
			pxState.listenerPorts.put(Side.UE, plm.ueListenerPort);
			
			pxState.listenerAddresses.put(Side.SP, plm.spListenerAddress);
			pxState.listenerPorts.put(Side.SP, plm.spListenerPort);
			
			
			return new CallResult(Atom.NOREPLY, state);
		}
		else if (mb instanceof KeepAliveMessage) {
			final KeepAliveMessage kam = (KeepAliveMessage)message;
			
			final Side side = kam.side;
			final Side otherSide = otherSide(side);
			
			System.out.println("proxy received k-a-message");
			
			GenServer gsForward = ((State)state).portSenders.get(otherSide);
			
			gsForward.cast(kam);

			return new CallResult(Atom.NOREPLY, state);
			
		}
		else if (mb instanceof InternalSipMessage) {
			
			try {
				InternalSipMessage ism = (InternalSipMessage)message;
				final SipMessage sm = ism.message;
			
				Side side = ism.side;
				Side otherSide = otherSide(side);
			
				System.out.println("proxy received:");
				System.out.println(String.format("from: %s", ism.side));
				System.out.println(sm.toString());
			
				GenServer gsForward = ((State)state).portSenders.get(otherSide);

				if (isRequest(sm)) {
					
					// modify before passing on
					
					String mf = sm.headers.get("Max-Forwards").get(0);
					int mfValue = Integer.parseInt(mf.substring(mf.indexOf(':')));
					sm.headers.put("Max-Forwards", listOne(String.format("Max-Forwards: %d", mfValue-1)));
					
				
					List<String> vv = sm.headers.get("Via");
					
					String topVia = vv.get(0);

					String topViaBranch = "NOMATCH";
					for (String u : topVia.split(";")) {
						if (u.startsWith("branch=")) {}
						topViaBranch = u.substring(u.indexOf('=')+1);
					}
					
					final byte[] listenerAddress = ((State)state).listenerAddresses.get(otherSide(ism.side));
					final Integer listenerPort = ((State)state).listenerPorts.get(otherSide(ism.side));
					
					String newVia =
							String.format("Via: SIP/2.0/UDP %s:%s;rport;branch=%s",
									toDottedAddress(listenerAddress),
									listenerPort.intValue(),
									topViaBranch + "_h8k4c9ne"
							);

					prepend(newVia, vv);
					
					((State)state).portSenders.get(otherSide(ism.side)).cast(ism);

				}
				else if (isResponse(sm)) {
					// a response .. easy, just drop topmost via and use new top via as destination
					
					List<String> vias = sm.headers.get("Via");
					List<String> newVias = dropFirst(vias);
					
					sm.headers.put("Via", newVias);
					
					String topVia = newVias.get(0);
					
					String leadingPart = topVia.split(";")[0];
					
					String[] subParts = leadingPart.split(" ");
					
					String addrPort = subParts[subParts.length - 1];
					
					String[] addrPortParts = addrPort.split(":");
					
					String destAddr = addrPortParts[0];
					String destPort = addrPortParts.length > 1 ? addrPortParts[1] : "5060";
					
					
					InternalSipMessage newIsm = new InternalSipMessage(
							ism.side,
							ism.message,
							toByteArray(destAddr),
							Integer.valueOf(Integer.parseInt(destPort)));
					
					
					((State)state).portSenders.get(otherSide(ism.side)).cast(newIsm);
					
					
					
					
					
				}
				else {
					throw new RuntimeException("neither request nor response");
				}
			
				gsForward.cast(ism);


				
			}
			catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			return new CallResult(Atom.NOREPLY, state);
			
		}
		else {
			// make some noise
			return new CallResult(Atom.NOREPLY, state);
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

	private Side otherSide(Side side) {
		return side == Side.UE ? Side.SP : Side.UE;
	}

	private boolean isRequest(SipMessage sm) {
		return sm.firstLine.endsWith("SIP/2.0");
	}

	private boolean isResponse(SipMessage sm) {
		return sm.firstLine.startsWith("SIP/2.0");
	}
	
	private static List<String> listOne(String s) {
		final List<String> result = new ArrayList<String>(1);
		result.add(s);
		return result;
	}
	
	private static void prepend(String s, List<String> ss) {
		ss.add(null);
		for (int k = ss.size()-1; k >= 1; k--) {
			final String item = ss.get(k-1);
			ss.set(k, item);
		}
		ss.set(0, s);
		return;
	}
	
	private static List<String> dropFirst(List<String> ss) {
		List <String> result = new ArrayList<String>();
		for (int k = 1; k < ss.size(); k++) {
			result.add(ss.get(k));
		}
		return result;
	}
	

	private static String toDottedAddress(byte[] address) {
		return String.format("%d.%d.%d.%d",
				toInt(address[0]), toInt(address[1]), toInt(address[2]), toInt(address[3]));
	}

	private static int toInt(byte b) {
		return b < 0 ? b + 256 : b;
	}

	private byte[] toByteArray(String ipv4Addr) {

		byte[] result = new byte[4];
		int k = 0;
		for (String u : ipv4Addr.split("[.]")) {
			int a = Integer.parseInt(u);
			result[k++] = (byte) (a >= 128 ? a - 256 : a);
		}
		return result;
	}
	
	
}
