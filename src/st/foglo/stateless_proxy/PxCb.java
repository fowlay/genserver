package st.foglo.stateless_proxy;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import st.foglo.genserver.CallBackBase;
import st.foglo.genserver.GenServer;
import st.foglo.genserver.GenServer.CallResult;
import st.foglo.genserver.GenServer.CastResult;
import st.foglo.genserver.GenServer.InfoResult;
import st.foglo.genserver.GenServer.InitResult;
import st.foglo.stateless_proxy.SipMessage.Method;
import st.foglo.stateless_proxy.SipMessage.TYPE;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Mode;
import st.foglo.genserver.Atom;

/**
 * The stateless proxy.
 */
public final class PxCb extends CallBackBase {

	private Map<String, SocketAddress> registry = new HashMap<String, SocketAddress>();

    /**
     * Map of incoming branch parameter to True/False, for registrations/de-registrations
     * to be performed on successful response
     */
    private Map<String, Boolean> pendingRegistrations = new HashMap<String, Boolean>();

	final Map<Side, GenServer> portSenders = new HashMap<Side, GenServer>();
	final Map<Side, byte[]> listenerAddresses = new HashMap<Side, byte[]>();
	final Map<Side, Integer> listenerPorts = new HashMap<Side, Integer>();

    final GenServer presenter;

    final BlackList blackList;



    private GenServer getPortsender(Side side) {
        for (; true; ) {

            final GenServer gs = portSenders.get(side);
            if (gs != null) {
                return gs;
            }
            else {
                final String key = side == Side.UE ? "access-UDP" : side == Side.SP ? "core-UDP" : "badkey";
                final GenServer newGs = GenServer.registry.get(key);

                if (newGs != null) {
                    portSenders.put(side, newGs);
                    return newGs;
                }
                else {
                    Thread.yield();
                }
            }
        }
    }


	public PxCb(byte[] sipAddrUe, Integer sipPortUe,
                byte[] sipAddrSp, Integer sipPortSp,
                GenServer presenter,
                BlackList blackList) {

		listenerAddresses.put(Side.UE, sipAddrUe);
		listenerPorts.put(Side.UE, sipPortUe);
		listenerAddresses.put(Side.SP, sipAddrSp);
		listenerPorts.put(Side.SP, sipPortSp);

        this.presenter = presenter;
        this.blackList = blackList;
	}

	///////////////////////////////////////////

	@Override
	public InitResult init(Object[] args) {

		Util.seq(Mode.START, Side.PX, Util.Direction.NONE, "enter init");

		// listenerAddresses.put(Side.UE, (byte[])args[0]);
		// listenerPorts.put(Side.UE, (Integer)args[1]);
		// listenerAddresses.put(Side.SP, (byte[])args[2]);
		// listenerPorts.put(Side.SP, (Integer)args[3]);

		Util.seq(Mode.START, Side.PX, Util.Direction.NONE, "done init");

		return new InitResult(Atom.OK);
	}

	@Override
	public CastResult handleCast(Object message) {

		MsgBase mb = (MsgBase) message;

        if (mb instanceof KeepAliveMessage) {

			final KeepAliveMessage kam = (KeepAliveMessage) message;
			final Side side = kam.side;


            if (side == Side.SP) {
                Util.seq(Mode.KEEP_ALIVE, Side.PX, Util.Direction.NONE, "drop keep-alive response");
            }
            else {
                final Side otherSide = otherSide(side);
                Util.seq(Mode.KEEP_ALIVE, Side.PX, direction(side, otherSide), mb.toString());
                GenServer gsForward = getPortsender(otherSide);
                gsForward.cast(kam);
                return new CastResult(Atom.NOREPLY);
            }

			return new CastResult(Atom.NOREPLY);

		} else if (mb instanceof InternalSipMessage) {

			InternalSipMessage ism = (InternalSipMessage) message;

            

			try {
				final SipMessage sm = ism.message;
				final Side side = ism.side;
				final Side otherSide = otherSide(side);
				final Method method = sm.getMethod();
                final TYPE type = sm.type;
                final String toUser = sm.getUser("To");
                final String fromUser = sm.getUser("From");

				if (type == TYPE.request) {

					Util.seq(Mode.SIP, Side.PX, direction(side, otherSide), sm.firstLineNoVersion());

                    // blacklisting
                    if (method == Method.INVITE && SipMessage.isElement(fromUser, blackList.blackList)) {
                        ism.setBlocked(true);
                        Util.seq(Mode.SIP, Side.PX, Direction.NONE, String.format("blacklisted: %s", fromUser));
                        presenter.cast(ism);
                        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
                    }

                    presenter.cast(ism);

					// Max-Forwards
					final String mf = sm.getTopHeaderField("Max-Forwards");
					// Util.trace(Level.verbose, "have field +++ %s", mf);
					final int mfValue = Integer.parseInt(mf);
					sm.setHeaderField("Max-Forwards", String.format("%d", mfValue - 1));

                    // Statefully save incoming branch parameter
                    // TODO: potential memory leak
                    if (method == Method.REGISTER) {
                        pendingRegistrations.put(sm.getTopViaBranch(), Boolean.valueOf(!sm.isDeRegister()));
                    }

					// Add a Via header
                    final String topViaBranch = sm.getTopViaBranch();
					final byte[] listenerAddress = listenerAddresses.get(otherSide);
					final Integer localPort = listenerPorts.get(otherSide);
					final String newVia = String.format("SIP/2.0/UDP %s:%s;rport;branch=%s",
							toDottedAddress(listenerAddress),
							localPort.intValue(),
							topViaBranch + "_h8k4c9ne");

                    sm.prepend("Via", newVia);

					// Route header field removal from requests
					if (method == Method.REGISTER ||
							method == Method.ACK ||
							method == Method.BYE ||
							method == Method.INVITE ||
							method == Method.SUBSCRIBE) {
						final ConcurrentLinkedDeque<String> hh = sm.getHeaderFields("Route");
						if (!hh.isEmpty()) {
							final String topRoute = hh.peek();
							final int indexOfSipColon = topRoute.indexOf("sip:");
							final int indexOfSemi = topRoute.indexOf(';');
							final String addrPort = topRoute.substring(4 + indexOfSipColon, indexOfSemi);
							final String host = addrPort.split(":")[0];
							if (host.equals(toDottedAddress(Main.sipAddrUe))) {
								sm.dropFirst("Route");
							}
						}
					}

					// Terminating request
					// this finalizes the route set for the UE
					if (Main.RECORD_ROUTE && ism.side == Side.SP && (method == Method.INVITE || method == Method.SUBSCRIBE)) {
						final ConcurrentLinkedDeque<String> hh = sm.getHeaderFields("Record-Route");
						final String rrHeaderField = String.format("<sip:%s:%s;lr>",
						    toDottedAddress(Main.sipAddrUe),
							Main.sipPortUe.intValue());
						hh.addFirst(rrHeaderField);
						sm.setHeaderFields("Record-Route", hh);
					}

					if (otherSide == Side.UE) {
                        // terminating INVITE e.g.

                      
                        // Util.seq(Mode.SIP, Side.PX, Direction.NONE, String.format("blacklist? %s", fromUser));

                        

                            final SocketAddress sa = registry.get(toUser);
                            if (sa == null) {
                                Util.seq(Mode.SIP, Side.PX, Direction.NONE, String.format("unknown: %%s", toUser));
                                throw new RuntimeException();
                            } else {
                                final byte[] addr = ((InetSocketAddress) sa).getAddress().getAddress();
                                final Integer port = Integer.valueOf(((InetSocketAddress) sa).getPort());
                                final GenServer gsForward = getPortsender(otherSide);
                                gsForward.cast(ism.setDestination(addr, port));
                            }
                        
                    } else if (otherSide == Side.SP) {

						// TOXDO - these items should be passed in constructor call?
						final byte[] addr = Main.outgoingProxyAddrSp;
						final Integer port = Main.outgoingProxyPortSp;

						final GenServer gsForward = getPortsender(otherSide);
						//Util.trace(Level.verbose, "about to cast: %s", sm.toString());
                        Util.seq(Mode.SIPDEBUG, side, Direction.NONE, String.format("gsForward thread name is: %s", gsForward.getThread().getName()));
						gsForward.cast(ism.setDestination(addr, port));
					}

				} else if (type == TYPE.response) {

                    presenter.cast(ism);

					// a response .. easy, just drop topmost via and use new top via as destination
					Util.seq(Mode.SIP, Side.PX, direction(side, otherSide), sm.responseLabel());

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
                    if (Main.RECORD_ROUTE && ism.side == Side.SP && (method == Method.INVITE || method == Method.SUBSCRIBE)) {
                        final ConcurrentLinkedDeque<String> hh = sm.getHeaderFields("Record-Route");
                        final String rrHeaderField = String.format("<sip:%s:%s;lr>",
                                toDottedAddress(Main.sipAddrUe),
                                Main.sipPortUe.intValue());
                        hh.addLast(rrHeaderField);
                        sm.setHeaderFields("Record-Route", hh);
                    }

                    // Response to terminating request
                    // remove the RR header field that was added for the benefit of the UE
                    if (Main.RECORD_ROUTE && ism.side == Side.UE && (method == Method.INVITE || method == Method.SUBSCRIBE)) {
                        final String rrHeaderField = String.format("<sip:%s:%s;lr>",
                                toDottedAddress(Main.sipAddrUe),
                                Main.sipPortUe.intValue());
                        final ConcurrentLinkedDeque<String> hh = sm.getHeaderFields("Record-Route");
                        hh.remove(rrHeaderField);
                        sm.setHeaderFields("Record-Route", hh);
                    }

                    // prepare internal message ready for sending
                    final InternalSipMessage ismOut = new InternalSipMessage(
                            ism.side, sm, toByteArray(destAddr),
                            Integer.valueOf(Integer.parseInt(destPort)));

                    // finalize REGISTER actions
                    if (sm.getMethod() == Method.REGISTER) {
                        final int code = sm.getCode();
                        if (code >= 200 && code < 300) {
                            final String branch = sm.getTopViaBranch();
                            final Boolean regAttempt = pendingRegistrations.get(branch);
                            pendingRegistrations.remove(branch);
                            if (regAttempt != null && regAttempt.booleanValue()) {
                                // REGISTER
                                registry.put(toUser, UdpCb.createSocketAddress(ismOut.destAddr, ismOut.destPort));
                                Util.seq(Mode.DEBUG, Side.PX, Direction.NONE, String.format("registered: %s -> %s:%d", toUser, Util.bytesToIpAddress(ismOut.destAddr), ismOut.destPort));
                            } else if (regAttempt != null && !regAttempt.booleanValue()) {
                                // de-REGISTER
                                registry.remove(toUser);
                                Util.seq(Mode.DEBUG, Side.PX, Direction.NONE, String.format("de-registered: %s", toUser));
                            }
                        }
                    }

                    final GenServer gsForward = getPortsender(otherSide);

                    gsForward.cast(ismOut);
                } else {
                    throw new RuntimeException("neither request nor response");
                }
			} catch (Exception e) {
				e.printStackTrace();
			}
			return new CastResult(Atom.NOREPLY);
		} else {
			throw new RuntimeException();
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
	public InfoResult handleInfo(Object message) {
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
