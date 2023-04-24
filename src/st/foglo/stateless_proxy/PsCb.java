package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Level;
import st.foglo.genserver.Atom;

/**
 * PortSender
 * 
 * Receives outgoing messages in instance format, and passes it to the associated
 * listener process in ready-to-send format
 */
public final class PsCb implements CallBack {
	
	Side side;
	byte[] peerAddr;
	Integer peerPort;
	DatagramSocket socket;
	GenServer proxy;
	
	int handleInfoCount = 0;
	int receiveCount = 0;
	int castCount = 0;

	
	
//	public class State {
//		final Side side;
//		
//		/** outbound proxy address */
//		final byte[] peerAddr;
//		
//		/** outbound proxy port */
//		final Integer peerPort;
//		
//		DatagramSocket socket;   // may be updated, so not final
//		
//		final GenServer proxy;
//		
//		int handleInfoCount = 0;
//		
//		int receiveCount = 0;
//
//		public State(Side side,
//				byte[] peerAddr,
//				Integer peerPort,
//				DatagramSocket socket,
//				GenServer proxy) {
//			super();
//			this.side = side;
//			this.peerAddr = peerAddr;
//			this.peerPort = peerPort;
//			this.socket = socket;
//			this.proxy = proxy;
//		}
//		
//
//	}
	
	
	
	////////////////////////////////
	
	@Override
	public CallResult init(Object[] args) {
		
		// TODO - no need to store 'side' in the state, since an instance variable is used
		
		side = (Side)args[0];
		
		peerAddr = (byte[])args[1];
		peerPort = ((Integer)args[2]).intValue();
		
		proxy = (GenServer)args[3];
		
		socket = null;
		try {
			socket = new DatagramSocket();
			socket.setSoTimeout(400);
			
			final InetAddress ia = InetAddress.getByAddress(peerAddr);
			final SocketAddress sa = new InetSocketAddress(ia, ((Integer)args[2]).intValue());

			socket.connect(sa);

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		return new CallResult(Atom.OK, null, null, Util.TIMEOUT_ZERO);
	}
	

	@Override
	public CallResult handleCast(Object message, Object state) {
		
		castCount++;
		
		// send a datagram
		MsgBase mb = (MsgBase) message;
		if (mb instanceof KeepAliveMessage) {
			final KeepAliveMessage kam = (KeepAliveMessage) mb;
			
			// always use outbound proxy
		
			try {
				final DatagramPacket p = new DatagramPacket(kam.buffer, kam.size);
				socket.send(p);

			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		else if (mb instanceof UpdatePortSender) {
			final UpdatePortSender ups = (UpdatePortSender) mb;
			
			try {
				socket.disconnect();
				socket.close();
				socket = new DatagramSocket();
				socket.setSoTimeout(400);
				final InetAddress ia = InetAddress.getByAddress(ups.host);
				final SocketAddress sa = new InetSocketAddress(ia, ups.port.intValue());
				socket.connect(sa);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			
		}
		else if (mb instanceof InternalSipMessage) {

			final InternalSipMessage ism = (InternalSipMessage) mb;
			
			Util.trace(Level.verbose, "%s send a forwarded message: castCount: %d%n%s", side.toString(), castCount, ism.message.toString());
			
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
		return new CallResult(Atom.NOREPLY, null, state, Util.TIMEOUT_ZERO);
	}

	

	@Override
	public CallResult handleCall(Object message, Object state) {
		MsgBase mb = (MsgBase)message;
		if (mb instanceof GetLocalPortMsg) {
			final int localPort = socket.getLocalPort();
			return new CallResult(Atom.REPLY, Integer.valueOf(localPort), state);
		}
		return new CallResult(Atom.REPLY, Integer.valueOf(-1), state);
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {
		// copypasted from PlCb

		handleInfoCount++;

		byte[] buffer = new byte[2500];                        // TODO hardcoded length
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			// read from the socket, with a timeout that was set in init

			socket.receive(p);
			receiveCount++;
			
			// Got data
			final int recLength = p.getLength();

			if (recLength <= 4) {   // TODO, hard code
				// assume a keep-alive message
				Util.trace(Level.verbose, "%s sender-listener received: %s", toString(), Util.bytesToString(buffer, recLength));
				final KeepAliveMessage kam = new KeepAliveMessage(side, buffer, recLength);
				proxy.cast(kam);
			}
			else {
				StringBuilder sb = new StringBuilder();
				for (int j = 0; j < recLength; j++) {
					sb.append((char) buffer[j]);
				}
				
				Util.trace(Level.verbose, "%s sender-listener received:%n%s", toString(), sb.toString());
				
				final SipMessage sipMessage =
						new SipMessage(buffer, recLength);
				
				final InternalSipMessage iMsg =
						new InternalSipMessage(
								side,
								sipMessage,
								Util.digest(buffer, recLength),
								null,
								null);
				
				proxy.cast(iMsg);
			}


			return new CallResult(Atom.NOREPLY, null, null, Util.TIMEOUT_ZERO);
			
		} catch (SocketTimeoutException e) {
			// Util.trace(Level.debug, "%s socket receive timeout", toString());

			return new CallResult(Atom.NOREPLY, null, null, Util.TIMEOUT_ZERO);

		}
		catch (PortUnreachableException e) {
			Util.trace(Level.verbose, "PUE, listener-sender on side: %s, handleInfo: %d, receive: %d",
					side.toString(), handleInfoCount, receiveCount);

			return new CallResult(Atom.NOREPLY, null, null, Util.TIMEOUT_ZERO);
			
		}
		catch (IOException e) {
			Util.trace(Level.verbose, "%s exception: %s, count: %d", side.toString(), e.getMessage(), handleInfoCount);
			e.printStackTrace();
		}

		return new CallResult(Atom.NOREPLY, null, null, Util.TIMEOUT_ZERO);

	}

	@Override
	public void handleTerminate(Object state) {
	}
	
	public String toString() {
		return String.format("%s listener:", side);
	}

}
