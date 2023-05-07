package st.foglo.stateless_proxy;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.SipMessage.Method;
import st.foglo.stateless_proxy.SipMessage.TYPE;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Level;
import st.foglo.genserver.Atom;

/**
 * The stateless proxy.
 */
public final class PxCb implements CallBack {

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

		// listenerAddresses.put(Side.UE, (byte[])args[0]);
		// listenerPorts.put(Side.UE, (Integer)args[1]);
		// listenerAddresses.put(Side.SP, (byte[])args[2]);
		// listenerPorts.put(Side.SP, (Integer)args[3]);

		Util.seq(Level.debug, Side.PX, Util.Direction.NONE, "done init");

		return new CallResult(Atom.OK, null);
	}

	@Override
	public CallResult handleCast(Object message) {

		MsgBase mb = (MsgBase) message;
		if (mb instanceof PortSendersMsg) {

			Util.seq(Level.verbose, Side.PX, Util.Direction.NONE, "tell proxy about port senders");

			PortSendersMsg plm = (PortSendersMsg) mb;

			portSenders.put(Side.UE, plm.uePortSender);
			portSenders.put(Side.SP, plm.spPortSender);

			return new CallResult(Atom.NOREPLY);
		} else if (mb instanceof KeepAliveMessage) {
			final KeepAliveMessage kam = (KeepAliveMessage) message;
			final Side side = kam.side;
			final Side otherSide = otherSide(side);
			// Util.trace(Level.verbose, "PX received k-a-m, %s -> %s", side.toString(),
			// otherSide.toString());

			Util.seq(Level.verbose, Side.PX, direction(side, otherSide), "k-a-m");

			GenServer gsForward = portSenders.get(otherSide);
			gsForward.cast(kam);
			return new CallResult(Atom.NOREPLY);
		} else if (mb instanceof InternalSipMessage) {

			InternalSipMessage ism = (InternalSipMessage) message;

			try {

				final SipMessage sm = ism.message;
				final Side side = ism.side;
				final Side otherSide = otherSide(side);
				final Method method = sm.getMethod();

				if (sm.type == TYPE.request) {

					Util.seq(Level.verbose, Side.PX, direction(side, otherSide), sm.firstLine);

					// Max-Forwards
					final String mf = sm.getTopHeaderField("Max-Forwards");
					// Util.trace(Level.verbose, "have field +++ %s", mf);
					final int mfValue = Integer.parseInt(mf);
					sm.setHeaderField("Max-Forwards", String.format("%d", mfValue - 1));

					// Via
					final String topVia = sm.getTopHeaderField("Via");
					String topViaBranch = "NOMATCH";
					for (String u : topVia.split(";")) {
						if (u.startsWith("branch=")) {
							topViaBranch = u.substring(u.indexOf('=') + 1);
						}
					}
					final byte[] listenerAddress = listenerAddresses.get(otherSide);
					final Integer localPort = (Integer) portSenders.get(otherSide(ism.side))
							.call(new GetLocalPortMsg());
					final String newVia = String.format("SIP/2.0/UDP %s:%s;rport;branch=%s",
							toDottedAddress(listenerAddress),
							localPort.intValue(),
							topViaBranch + "_h8k4c9ne");
					sm.prepend("Via", newVia);

					// Collect 'user' from REGISTER request
					// TOXDO: Do this in 200 REGISTER rather
					if (method == Method.REGISTER) {
						final String user = sm.getUser();
						final SocketAddress sa = UdpCb.createSocketAddress(ism.sourceAddr,
								ism.sourcePort.intValue());
						Util.seq(Level.debug, Side.PX, Direction.NONE,
								String.format("registering: %s -> %s", user, sa.toString()));
						registry.put(user, sa);
					}

					// Route header field removal from requests
					if (method == Method.REGISTER ||
							method == Method.ACK ||
							method == Method.BYE ||
							method == Method.INVITE ||
							method == Method.SUBSCRIBE) {
						final List<String> hh = sm.getHeaderFields("Route");
						if (!hh.isEmpty()) {
							final String topRoute = hh.get(0);
							final int indexOfSipColon = topRoute.indexOf("sip:");
							final int indexOfSemi = topRoute.indexOf(';');
							final String addrPort = topRoute.substring(4 + indexOfSipColon, indexOfSemi);
							final String host = addrPort.split(":")[0];
							if (host.equals(toDottedAddress(Main.sipAddrUe))) {
								sm.dropFirst("Route");
							}
						}
					}


					// Record-route insertion, never do it for originating requests
					if (Main.NEVER && ism.side == Side.UE && (method == Method.INVITE || method == Method.SUBSCRIBE)) {
						final List<String> hh = sm.getHeaderFields("Record-Route");
						final String rrHeaderField = String.format("<sip:%s:%s;lr>",
						    toDottedAddress(Main.sipAddrUe),
							Main.sipPortUe.intValue());
						((LinkedList<String>)hh).addFirst(rrHeaderField);
						sm.setHeaderFields("Record-Route", hh);
					}

					// Terminating request
					// this finalizes the route set for the UE
					if (Main.RECORD_ROUTE && ism.side == Side.SP && (method == Method.INVITE || method == Method.SUBSCRIBE)) {
						final List<String> hh = sm.getHeaderFields("Record-Route");
						final String rrHeaderField = String.format("<sip:%s:%s;lr>",
						    toDottedAddress(Main.sipAddrUe),
							Main.sipPortUe.intValue());
						((LinkedList<String>)hh).addFirst(rrHeaderField);
						sm.setHeaderFields("Record-Route", hh);
					}


					if (otherSide == Side.UE) {
						// terminating INVITE e.g.

						final String user = sm.getUser();

						final SocketAddress sa = registry.get(user);
						if (sa == null) {
							throw new RuntimeException();
						} else {
							final byte[] addr = ((InetSocketAddress) sa).getAddress().getAddress();
							final Integer port = Integer.valueOf(((InetSocketAddress) sa).getPort());
							final GenServer gsForward = portSenders.get(otherSide);
							gsForward.cast(ism.setDestination(addr, port));
						}
					} else {
						final GenServer gsForward = portSenders.get(otherSide);
						//Util.trace(Level.verbose, "about to cast: %s", ism.message.toString());
						gsForward.cast(ism);
					}

				} else if (sm.type == TYPE.response) {
					// a response .. easy, just drop topmost via and use new top via as destination
					Util.seq(Level.verbose, Side.PX, direction(side, otherSide), sm.firstLine);

					sm.dropFirst("Via");
					final String topVia = sm.getTopHeaderField("Via");
					//Util.trace(Level.verbose, "topVia: %s", topVia);

					final String leadingPart = topVia.split(";")[0];
					//Util.trace(Level.verbose, "leadingPart: %s", leadingPart);

					final String[] subParts = leadingPart.split(" ");
					final String addrPort = subParts[subParts.length - 1];
					//Util.trace(Level.verbose, "addrPort: %s", addrPort);

					final String[] addrPortParts = addrPort.split(":");
					final String destAddr = addrPortParts[0];
					//Util.trace(Level.verbose, "destAddr: %s", destAddr);

					final String destPort = addrPortParts.length > 1 ? addrPortParts[1] : "5060";
					//Util.trace(Level.verbose, "destPort: %s", destPort);


					// Record-route insertion, for certain responses
					if (Main.RECORD_ROUTE && ism.side == Side.SP &&
							(method == Method.INVITE || method == Method.SUBSCRIBE)) {
						final List<String> hh = sm.getHeaderFields("Record-Route");
						final String rrHeaderField = String.format("<sip:%s:%s;lr>",
								toDottedAddress(Main.sipAddrUe),
								Main.sipPortUe.intValue());
						((LinkedList<String>) hh).addLast(rrHeaderField);
						sm.setHeaderFields("Record-Route", hh);
					}

					// Response to terminating request
					// remove the RR header field that was added for the benefit of the UE
					if (Main.RECORD_ROUTE && ism.side == Side.UE && (method == Method.INVITE || method == Method.SUBSCRIBE)) {
						final String rrHeaderField = String.format("<sip:%s:%s;lr>",
						    toDottedAddress(Main.sipAddrUe),
							Main.sipPortUe.intValue());
						final List<String> hh = sm.getHeaderFields("Record-Route");
						((LinkedList<String>)hh).remove(rrHeaderField);
						sm.setHeaderFields("Record-Route", hh);
					}


					final GenServer gsForward = portSenders.get(otherSide);
					gsForward.cast(
							new InternalSipMessage(
									ism.side, ism.message, Integer.valueOf(0), toByteArray(destAddr),
									Integer.valueOf(Integer.parseInt(destPort))));
				} else {
					throw new RuntimeException("neither request nor response");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			return new CallResult(Atom.NOREPLY);
		} else {
			// make some noise
			return new CallResult(Atom.NOREPLY);
		}
	}

	private Direction direction(Side from, Side to) {
		if (from == Side.UE && to == Side.SP) {
			return Direction.SP;
		} else if (from == Side.SP && to == Side.UE) {
			return Direction.UE;
		} else {
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

	/*
	private static List<String> listOne(String s) {
		final List<String> result = new LinkedList<String>();
		result.add(s);
		return result;
	}
	*/


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
