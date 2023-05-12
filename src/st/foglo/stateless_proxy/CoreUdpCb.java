package st.foglo.stateless_proxy;

import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Mode;

public class CoreUdpCb extends UdpCb {

	final byte[] sipAddrSp;
	final int sipPortSp;
	
	public CoreUdpCb(Side side, GenServer proxy,
				byte[] sipAddrSp, int sipPortSp,
				byte[] outgoingProxyAddr, Integer outgoingProxyPort) {
		super(side, proxy);
		this.sipAddrSp = sipAddrSp;
		this.sipPortSp = sipPortSp;
		this.outgoingProxyAddr = outgoingProxyAddr;
		this.outgoingProxyPort = outgoingProxyPort.intValue();
	}
	
	@Override
	public CallResult init(Object[] ignored) {

		super.init(ignored);

		channelWrapper = new ChannelWrapper(side, sipAddrSp, sipPortSp, outgoingProxyAddr, outgoingProxyPort);

		Util.seq(Mode.START, side, Direction.NONE, "enter init");
		final CallResult result = udpInit(side, outgoingProxyAddr, outgoingProxyPort);
		Util.seq(Mode.START, side, Direction.NONE, String.format("done init, returning: %s", result.toString()));
		return result;
	}

	@Override
	public CallResult handleCast(Object message) {
		MsgBase mb = (MsgBase) message;
		if (mb instanceof KeepAliveMessage) {
			final CallResult result = handleKeepAliveMessage(side, (KeepAliveMessage)mb);
			return result;
		} else if (mb instanceof InternalSipMessage) {

			final InternalSipMessage ism = (InternalSipMessage) mb;
			//Util.trace(Level.verbose, "%s send a forwarded message: castCount: %d%n%s", side.toString(), castCount, ism.message.toString());
			Util.seq(Mode.SIP, side, Direction.OUT, ism.message.firstLine);
			// if the message comes with destination info, then use it,
			// else use outbound proxy

			udpCast(side, ism);

			return result;
		}
		else {
			throw new RuntimeException();
		}
	}



	@Override
	public CallResult handleInfo(Object message) {
		// byte[] buffer = new byte[Main.DATAGRAM_MAX_SIZE];
		// DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		// socket.receive(p);

		final UdpInfoResult udpInfoResult = udpInfo(side);

		if (udpInfoResult.timedOut) {
			return result;
		} else if (udpInfoResult.datagramSize <= Main.KEEPALIVE_MSG_MAX_SIZE) {
			final KeepAliveMessage kam = new KeepAliveMessage(side, recBuffer, udpInfoResult.datagramSize);
			Util.seq(Mode.KEEP_ALIVE, side, Direction.IN, kam.toString());
			proxy.cast(proxy, kam);
		} else {
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < udpInfoResult.datagramSize; j++) {
				sb.append((char) recBuffer[j]);
			}

			final SipMessage sipMessage = new SipMessage(recBuffer, udpInfoResult.datagramSize);
			final InternalSipMessage iMsg = new InternalSipMessage(
					side,
					sipMessage,
					Util.digest(recBuffer, udpInfoResult.datagramSize),
					null,
					null);
			Util.seq(Mode.SIP, side, Direction.IN, sipMessage.firstLine);
			proxy.cast(proxy, iMsg);
		}

		return result;
	}
}
