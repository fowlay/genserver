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
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Level;

public class CoreUdpCb extends UdpCb {
	
	public CoreUdpCb(Side side, GenServer proxy, byte[] outgoingProxyAddr, Integer outgoingProxyPort) {
		super(side, proxy);
		this.outgoingProxyAddr = outgoingProxyAddr;
		this.outgoingProxyPort = outgoingProxyPort.intValue();
	}
	
	@Override
	public CallResult init(Object[] ignored) {
		
		Util.seq(Level.debug, side, Direction.NONE, "enter init");
		
		try {
			socket = new DatagramSocket();
			socket.setSoTimeout(Main.SO_TIMEOUT);
			


			final SocketAddress sa = UdpCb.createSocketAddress(outgoingProxyAddr, outgoingProxyPort);

			socket.connect(sa);
			
			Util.seq(Level.debug, side, Direction.NONE, "done init");
			
			return new CallResult(Atom.OK, CallResult.TIMEOUT_ZERO);
			
		} catch (SocketException e) {
			e.printStackTrace();
			return new CallResult(Atom.IGNORE);
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return new CallResult(Atom.IGNORE);
		}
	}

	@Override
	public CallResult handleCast(Object message) {
		// send a UDP message
		MsgBase mb = (MsgBase) message;
		if (mb instanceof KeepAliveMessage) {
			final KeepAliveMessage kam = (KeepAliveMessage) mb;
			
			// always use outbound proxy
			
			Util.seq(Level.verbose, side, Direction.OUT, kam.toString());
		
			try {
				final DatagramPacket p = new DatagramPacket(kam.buffer, kam.size);
				socket.send(p);

			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		else if (mb instanceof InternalSipMessage) {

			final InternalSipMessage ism = (InternalSipMessage) mb;
			
			//Util.trace(Level.verbose, "%s send a forwarded message: castCount: %d%n%s", side.toString(), castCount, ism.message.toString());
			Util.seq(Level.verbose, side, Direction.OUT, ism.message.firstLine);
			
			
			// if the message comes with destination info, then use it,
			// else use outbound proxy
		
			final byte[] ba = Util.toByteArray(ism.message);
			
			try {
				final DatagramPacket p = new DatagramPacket(ba, ba.length);
				socket.send(p);

			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		else {
			throw new RuntimeException();
		}
		return result;
	}

	@Override
	public CallResult handleInfo(Object message) {

		byte[] buffer = new byte[Main.DATAGRAM_MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			socket.receive(p);
			final int recLength = p.getLength();

			if (recLength <= Main.KEEPALIVE_MSG_MAX_SIZE) {
				final KeepAliveMessage kam = new KeepAliveMessage(side, buffer, recLength);
				Util.seq(Level.verbose, side, Direction.IN, kam.toString());
				proxy.cast(kam);
			}
			else {
				StringBuilder sb = new StringBuilder();
				for (int j = 0; j < recLength; j++) {
					sb.append((char) buffer[j]);
				}

				final SipMessage sipMessage =
						new SipMessage(buffer, recLength);
				final InternalSipMessage iMsg =
						new InternalSipMessage(
								side,
								sipMessage,
								Util.digest(buffer, recLength),
								null,
								null);
				Util.seq(Level.verbose, side, Direction.IN, sipMessage.firstLine);
				proxy.cast(iMsg);
			}
		}
		catch (SocketTimeoutException ignoreException) {
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
}
