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
	
	
	private int castCount = 0;

	public class State {
		final Side side;
		
		/** outbound proxy address */
		final byte[] peerAddr;
		
		/** outbound proxy port */
		final Integer peerPort;
		
		DatagramSocket socket;   // may be updated, so not final
		
		final GenServer proxy;
		
		int handleInfoCount = 0;
		
		int receiveCount = 0;

		public State(Side side, byte[] peerAddr, Integer peerPort, DatagramSocket socket, GenServer proxy) {
			super();
			this.side = side;
			this.peerAddr = peerAddr;
			this.peerPort = peerPort;
			this.socket = socket;
			this.proxy = proxy;
		}
		

	}
	
	
	
	////////////////////////////////
	
	@Override
	public CallResult init(Object[] args) {
		
		// TODO - no need to store 'side' in the state, since an instance variable is used
		
		side = (Side)args[0];
		
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
			socket.setSoTimeout(400);
			
			final InetAddress ia = InetAddress.getByAddress((byte[])args[1]);
			final SocketAddress sa = new InetSocketAddress(ia, ((Integer)args[2]).intValue());
				
			socket.connect(sa);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}

		return new CallResult(Atom.OK,
				null,
				new State(
						(Side)args[0], 
						(byte[])args[1], 
						(Integer)args[2], socket, 
						(GenServer)args[3]),
				0);
	}
	

	@Override
	public CallResult handleCast(Object message, Object state) {
		
		castCount++;
		
		final State psState = (State)state;
		
		// send a datagram
		MsgBase mb = (MsgBase) message;
		if (mb instanceof KeepAliveMessage) {
			final KeepAliveMessage kam = (KeepAliveMessage) mb;
			
			// always use outbound proxy
		
			final DatagramSocket socket = psState.socket;
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
				psState.socket.disconnect();
				psState.socket.close();
				psState.socket = new DatagramSocket();
				psState.socket.setSoTimeout(400);
				final InetAddress ia = InetAddress.getByAddress(ups.host);
				final SocketAddress sa = new InetSocketAddress(ia, ups.port.intValue());
				psState.socket.connect(sa);
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
			
			final DatagramSocket socket = psState.socket;

			try {
				final DatagramPacket p = new DatagramPacket(ba, ba.length);
				socket.send(p);

			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		return new CallResult(Atom.NOREPLY, null, state, 0);
	}
	
	
	
	
	

	@Override
	public CallResult handleCall(Object message, Object state) {
		MsgBase mb = (MsgBase)message;
		if (mb instanceof GetLocalPortMsg) {
			final DatagramSocket s = ((State)state).socket;
			final int localPort = s.getLocalPort();
			return new CallResult(Atom.REPLY, Integer.valueOf(localPort), state);
		}
		return new CallResult(Atom.REPLY, Integer.valueOf(-1), state);
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {
		// copypasted from PlCb

		final State plState = (State)state;
		plState.handleInfoCount++;
		final DatagramSocket socket = plState.socket;

		byte[] buffer = new byte[2500];                        // TODO hardcoded length
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			// read from the socket, with a timeout that was set in init

			socket.receive(p);
			plState.receiveCount++;
			
			// Got data
			final int recLength = p.getLength();

			if (recLength <= 4) {   // TODO, hard code
				// assume a keep-alive message
				Util.trace(Level.verbose, "%s sender-listener received: %s", toString(), Util.bytesToString(buffer, recLength));
				final KeepAliveMessage kam = new KeepAliveMessage(plState.side, buffer, recLength);
				plState.proxy.cast(kam);
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
								plState.side,
								sipMessage,
								Util.digest(buffer, recLength),
								null,
								null);
				
				plState.proxy.cast(iMsg);
			}


			return new CallResult(Atom.NOREPLY, null, plState, 0);
			
		} catch (SocketTimeoutException e) {
			// Util.trace(Level.debug, "%s socket receive timeout", toString());

			return new CallResult(Atom.NOREPLY, null, plState, 0);

		}
		catch (PortUnreachableException e) {
			Util.trace(Level.verbose, "PUE, listener-sender on side: %s, handleInfo: %d, receive: %d",
					plState.side.toString(), plState.handleInfoCount, plState.receiveCount);

			return new CallResult(Atom.NOREPLY, null, plState, 0);
			
		}
		catch (IOException e) {
			Util.trace(Level.verbose, "%s exception: %s, count: %d", plState.side.toString(), e.getMessage(), plState.handleInfoCount);
			e.printStackTrace();
		}

		return new CallResult(Atom.NOREPLY, null, plState, 0);

	}

	@Override
	public void handleTerminate(Object state) {
		// TODO Auto-generated method stub

	}
	
	public String toString() {
		return String.format("%s listener:", side);
	}

}
