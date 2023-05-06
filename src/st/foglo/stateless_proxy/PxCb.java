package st.foglo.stateless_proxy;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Level;
import st.foglo.genserver.Atom;

/**
 * The stateless proxy.
 */
public final class PxCb implements CallBack {
	
	private Set<Integer> seen = new HashSet<Integer>();
	
	private Map<String, SocketAddress> registry = new HashMap<String, SocketAddress>();

	
	final Map<Side, GenServer> portSenders = new HashMap<Side, GenServer>();
	final Map<Side, byte[]> listenerAddresses = new HashMap<Side, byte[]>();
	final Map<Side, Integer> listenerPorts = new HashMap<Side, Integer>();
	

	public PxCb(byte[] sipAddrUe, Integer sipPortUe, byte[] sipAddrSp, Integer sipPortSp) {
		
		listenerAddresses.put(Side.UE, sipAddrUe);
		listenerPorts.put(Side.UE, sipPortUe);
		listenerAddresses.put(Side.SP, sipAddrSp);
		listenerPorts.put(Side.SP, sipPortSp);
		
	}
	
	///////////////////////////////////////////

	@Override
	public CallResult init(Object[] args) {
		
		Util.seq(Level.debug, Side.PX, Util.Direction.NONE, "enter init");
		
//		listenerAddresses.put(Side.UE, (byte[])args[0]);
//		listenerPorts.put(Side.UE, (Integer)args[1]);
//		listenerAddresses.put(Side.SP, (byte[])args[2]);
//		listenerPorts.put(Side.SP, (Integer)args[3]);
		
		Util.seq(Level.debug, Side.PX, Util.Direction.NONE, "done init");
		
		return new CallResult(Atom.OK, null);
	}

	@Override
	public CallResult handleCast(Object message) {
		
		MsgBase mb = (MsgBase)message;
		if (mb instanceof PortSendersMsg) {
			
			Util.seq(Level.verbose, Side.PX, Util.Direction.NONE, "tell proxy about port senders");
			
			PortSendersMsg plm = (PortSendersMsg)mb;
			
			portSenders.put(Side.UE, plm.uePortSender);
			portSenders.put(Side.SP, plm.spPortSender);

			return new CallResult(Atom.NOREPLY);
		}
		else if (mb instanceof KeepAliveMessage) {
			final KeepAliveMessage kam = (KeepAliveMessage)message;
			final Side side = kam.side;
			final Side otherSide = otherSide(side);
			//Util.trace(Level.verbose, "PX received k-a-m, %s -> %s", side.toString(), otherSide.toString());
			
			Util.seq(Level.verbose, Side.PX, direction(side, otherSide), "k-a-m");
			
			GenServer gsForward = portSenders.get(otherSide);
			gsForward.cast(kam);
			return new CallResult(Atom.NOREPLY);
		}
		else if (mb instanceof InternalSipMessage) {
			
			InternalSipMessage ism = (InternalSipMessage)message;
			
			if (seen.contains(ism.digest) && Main.NEVER) {
				// ignore this resend
				// Util.trace(Level.verbose, "PX ------------- ignore resend from %s: %d", ism.side.toString(), ism.digest.intValue());
				return new CallResult(Atom.NOREPLY);
			}
			else {
				
				if (Main.NEVER) {
					seen.add(ism.digest);
				}
			
				try {
				
					final SipMessage sm = ism.message;
				
					final Side side = ism.side;
					final Side otherSide = otherSide(side);

					if (sm.isRequest()) {
					
						// modify before passing on
					
						//Util.trace(Level.debug, "PX forwarding a request");
						Util.seq(Level.verbose, Side.PX, direction(side, otherSide), sm.firstLine);

						String mf = sm.headers.get("Max-Forwards").get(0);
						int mfValue = Integer.parseInt(mf.substring(mf.indexOf(':')+1).trim());
						sm.headers.put("Max-Forwards", listOne(String.format("Max-Forwards: %d", mfValue-1)));
					
						List<String> vv = sm.headers.get("Via");

						String topVia = vv.get(0);

						String topViaBranch = "NOMATCH";
						for (String u : topVia.split(";")) {
							if (u.startsWith("branch=")) {
								topViaBranch = u.substring(u.indexOf('=')+1);
							}
						}

						final byte[] listenerAddress = listenerAddresses.get(otherSide);

						final Integer localPort =
								(Integer) portSenders.get(otherSide(ism.side)).call(new GetLocalPortMsg());

						String newVia =
								String.format("Via: SIP/2.0/UDP %s:%s;rport;branch=%s",
										toDottedAddress(listenerAddress),
										localPort.intValue(),
										topViaBranch + "_h8k4c9ne"
										);

						prepend(newVia, vv);
						
						if (sm.isRegisterRequest()) {
							
							final String user = sm.getUser();
		
							final SocketAddress sa = UdpCb.createSocketAddress(ism.sourceAddr,
									ism.sourcePort.intValue());

							Util.seq(Level.debug, Side.PX, Direction.NONE,
									String.format("registering: %s -> %s", user, sa.toString()));

							registry.put(user, sa);
							
						}
						
						if (otherSide == Side.UE) {
							// terminating INVITE e.g.
							
							final String user = sm.getUser();
							
							final SocketAddress sa = registry.get(user);
							if (sa == null) {
								throw new RuntimeException();
							}
							else {
								final byte[] addr = ((InetSocketAddress)sa).getAddress().getAddress();
								final Integer port =  Integer.valueOf(((InetSocketAddress)sa).getPort());
								final GenServer gsForward = portSenders.get(otherSide);
								gsForward.cast(ism.setDestination(addr, port));
							}
						}
						else {
							final GenServer gsForward = portSenders.get(otherSide);
							gsForward.cast(ism);
						}



					}
					else if (isResponse(sm)) {
						// a response .. easy, just drop topmost via and use new top via as destination
						
						Util.seq(Level.verbose, Side.PX, direction(side, otherSide), sm.firstLine);

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

						final GenServer gsForward = portSenders.get(otherSide);
						gsForward.cast(
								new InternalSipMessage(
										ism.side, ism.message, Integer.valueOf(0), toByteArray(destAddr),
										Integer.valueOf(Integer.parseInt(destPort))));
					}
					else {
						throw new RuntimeException("neither request nor response");
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			return new CallResult(Atom.NOREPLY);
		}
		else {
			// make some noise
			return new CallResult(Atom.NOREPLY);
		}
	}

	private Direction direction(Side from, Side to) {
		if (from == Side.UE && to == Side.SP) {
			return Direction.SP;
		}
		else if (from == Side.SP && to == Side.UE) {
			return Direction.UE;
		}
		else {
			throw new RuntimeException();
		}
	}

	@Override
	public CallResult handleCall(Object message) {
		throw new UnsupportedOperationException();
	}

	@Override
	public CallResult handleInfo(Object message) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void handleTerminate() {
	}

	private Side otherSide(Side side) {
		return side == Side.UE ? Side.SP : Side.UE;
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
