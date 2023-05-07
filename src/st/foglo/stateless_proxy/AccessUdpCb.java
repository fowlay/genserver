package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import st.foglo.genserver.Atom;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.SipMessage.TYPE;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Level;

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
	public CallResult init(Object[] args) {

		Util.seq(Level.debug, side, Direction.NONE, "enter init");

		try {
			final SocketAddress sa = UdpCb.createSocketAddress(localAddr, localPort);
			socket = new DatagramSocket(sa);

			socket.setSoTimeout(Main.SO_TIMEOUT);
			Util.seq(Level.debug, side, Direction.NONE, "done init");
			return new CallResult(Atom.OK, CallResult.TIMEOUT_ZERO);

		} catch (UnknownHostException e) {
			e.printStackTrace();
			return new CallResult(Atom.IGNORE);

		} catch (SocketException e) {
			e.printStackTrace();
			return new CallResult(Atom.IGNORE);
		}
	}

	@Override
	public CallResult handleCast(Object message) {
		MsgBase mb = (MsgBase) message;
		if (mb instanceof KeepAliveMessage) {
			// consider dropping! The right thing to do since there may be multiple UEs
			Util.seq(Level.verbose, side, Direction.NONE, "dropping keepAlive");
			return new CallResult(Atom.OK, CallResult.TIMEOUT_ZERO);
		} else if (mb instanceof InternalSipMessage) {

			// The ism has the required remote addressing; same handling for request and
			// response

			final InternalSipMessage ism = (InternalSipMessage) message;
			final SipMessage sm = ism.message;

			if (ism.message.type == TYPE.request) {
				Util.seq(Level.verbose, side, Direction.OUT, sm.firstLine);
			} else if (ism.message.type == TYPE.response) {
				Util.seq(Level.verbose, side, Direction.OUT, sm.firstLine);
			}

			try {
				final SocketAddress sa = UdpCb.createSocketAddress(ism.destAddr, ism.destPort.intValue());

				//Util.trace(Level.verbose, "just created sa: %s", sa.toString());

				socket.connect(sa);

				final byte[] ba = ism.message.toByteArray();

				final DatagramPacket p = new DatagramPacket(ba, ba.length);

				try {
					socket.send(p);
				} catch (IOException e) {
					e.printStackTrace();
				}

				socket.disconnect();

				return new CallResult(Atom.OK, CallResult.TIMEOUT_ZERO);

			} catch (SocketException e) {
				e.printStackTrace();
				return new CallResult(Atom.IGNORE);

			} catch (UnknownHostException e) {
				e.printStackTrace();
				return new CallResult(Atom.IGNORE);
			}

		}

		else {
			throw new RuntimeException();
		}
	}

	@Override
	public CallResult handleInfo(Object message) {

		byte[] buffer = new byte[Main.DATAGRAM_MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			// Util.seq(Level.debug, side, Util.Direction.NONE,
			// String.format("start listening, port: %d", socket.getLocalPort()));

			socket.receive(p);
			final int recLength = p.getLength();

			if (recLength <= Main.KEEPALIVE_MSG_MAX_SIZE) {
				final KeepAliveMessage kam = new KeepAliveMessage(side, buffer, recLength);
				Util.seq(Level.verbose, side, Direction.IN, kam.toString());
				proxy.cast(kam);
			} else {
				StringBuilder sb = new StringBuilder();
				for (int j = 0; j < recLength; j++) {
					sb.append((char) buffer[j]);
				}

				final SipMessage sipMessage = new SipMessage(buffer, recLength);

				final byte[] sourceAddr = p.getAddress().getAddress();
				final Integer sourcePort = Integer.valueOf(p.getPort());

				final InternalSipMessage iMsg = new InternalSipMessage(
						side,
						sipMessage,
						Util.digest(buffer, recLength),
						null,
						null,
						sourceAddr,
						sourcePort);
				Util.seq(Level.verbose, side, Direction.IN, sipMessage.firstLine);
				proxy.cast(iMsg);
			}
		} catch (SocketTimeoutException ignoreException) {
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
}
