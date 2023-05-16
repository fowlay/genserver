package st.foglo.stateless_proxy;

import st.foglo.genserver.Atom;
import st.foglo.genserver.GenServer.CastResult;
import st.foglo.genserver.GenServer.InfoResult;
import st.foglo.genserver.GenServer.InitResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.SipMessage.TYPE;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Mode;

public final class AccessUdpCb extends UdpCb {

	final byte[] localAddr;
	final int localPort;

	public AccessUdpCb(Side side, GenServer proxy, byte[] localAddr, int localPort) {
		super(side, proxy);
		this.localAddr = localAddr;
		this.localPort = localPort;
	}

	/////////////////////////////////////////////////

	@Override
	public InitResult init(Object[] args) {
		Util.seq(Mode.START, side, Direction.NONE, "enter init");

		super.init(args);

		channelWrapper = new ChannelWrapper(side, localAddr, localPort, null, -1);

		// side effect on socket
		final InitResult result = udpInit(side, localAddr, localPort);
		Util.seq(Mode.START, side, Direction.NONE, "done init");
		return result;
	}



	@Override
	public CastResult handleCast(Object message) {

		MsgBase mb = (MsgBase) message;
		if (mb instanceof KeepAliveMessage) {
			final CastResult result = handleKeepAliveMessage(side, (KeepAliveMessage)mb);
			return result;

		} else if (mb instanceof InternalSipMessage) {

			final InternalSipMessage ism = (InternalSipMessage) message;
			final SipMessage sm = ism.message;
			if (sm.type == TYPE.request) {
				Util.seq(Mode.SIP, side, Direction.OUT, sm.firstLineNoVersion());
			}
            else if (sm.type == TYPE.response) {
				Util.seq(Mode.SIP, side, Direction.OUT, sm.responseLabel());
			}

			// TOXDO ... maybe return an indication of success/failure?
			udpCast(side, ism);
			return new CastResult(Atom.NOREPLY, TIMEOUT_ZERO);
		} else {
			throw new RuntimeException();
		}
	}

	@Override
	public InfoResult handleInfo(Object message) {

		// byte[] buffer = new byte[Main.DATAGRAM_MAX_SIZE];
		// DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			// Util.seq(Level.debug, side, Util.Direction.NONE,
			// String.format("start listening, port: %d", socket.getLocalPort()));


			
			final UdpInfoResult udpInfoResult = udpInfo(side);

			if (udpInfoResult.timedOut) {
				return new InfoResult(Atom.NOREPLY, TIMEOUT_ZERO);
			} else if (udpInfoResult.datagramSize <= Main.KEEPALIVE_MSG_MAX_SIZE) {
				final KeepAliveMessage kam = new KeepAliveMessage(side, recBuffer, udpInfoResult.datagramSize);
				Util.seq(Mode.KEEP_ALIVE, side, Direction.IN, kam.toString());
				proxy.cast(kam);
			} else {
				StringBuilder sb = new StringBuilder();
				for (int j = 0; j < udpInfoResult.datagramSize; j++) {
					sb.append((char) recBuffer[j]);
				}

				final SipMessage sipMessage = new SipMessage(recBuffer, udpInfoResult.datagramSize);

				final byte[] sourceAddr = udpInfoResult.sourceAddr;
				final Integer sourcePort = udpInfoResult.sourcePort;

				final InternalSipMessage iMsg = new InternalSipMessage(
						side,
						sipMessage,
						null,
						null,
						sourceAddr,
						sourcePort);

                if (sipMessage.type == TYPE.request) {
                    Util.seq(Mode.SIP, side, Direction.IN, sipMessage.firstLineNoVersion());
                } else if (sipMessage.type == TYPE.response) {
                    Util.seq(Mode.SIP, side, Direction.IN, sipMessage.responseLabel());
                }

				proxy.cast(iMsg);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new InfoResult(Atom.NOREPLY, TIMEOUT_ZERO);
	}
}
