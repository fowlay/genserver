package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.genserver.Atom;

/**
 * The port listener.
 */
public class PlCb implements CallBack {
	
	public class State {
		final Side side;
		final GenServer proxy;
		
		int listenerPort;
		DatagramSocket socket;

		public State(
				Side side,
				GenServer aa,
				int listenerPort,
				DatagramSocket socket) {
			super();
			this.side = side;
			this.proxy = aa;
			this.listenerPort = listenerPort;
			this.socket = socket;
		}
	}
	
	///////////////////////////////////

	@Override
	public CallResult init(Object args) {
		Object[] aa = (Object[])args;
		
		final int port = ((Integer)aa[3]).intValue();
		
		InetAddress localAddr = null;
		try {
			localAddr = InetAddress.getByAddress((byte[])aa[2]);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(port, localAddr);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		try {
			socket.setSoTimeout(1500); // TODO, hardcoded timeout
		} catch (SocketException e) {
			// not supposed to happen // TODO, exception handling
			e.printStackTrace();
		}
		
		
		System.out.println("listener init returns"); // seen
		
		return new CallResult(
				Atom.OK,
				null,
				new State(
						(Side)aa[0],
						(GenServer)aa[1],
						port,
						socket),
				
				0);
	}

	@Override
	public CallResult handleCast(Object message, Object state) {

		MsgBase mb = (MsgBase) message;
		if (mb instanceof BufferMsg) {
			System.out.println("cannot happen"); // TODO, this is dead code
			
		} else {
			try {
				throw new RuntimeException("cannot handle cast");
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);;
			}
		}

		return new CallResult(Atom.NOREPLY, state);
	}

	@Override
	public CallResult handleCall(Object message, Object state) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {
		
		// read from the socket, with a timeout
		
		final State plState = (State)state;
		System.out.println(String.format("timed out: %s, port: %d", plState.side, plState.listenerPort));
		
		DatagramSocket socket = ((State)state).socket;
		
		byte[] buffer = new byte[2500];                        // TODO hardcoded length
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			socket.receive(p);
			// Got a datagram!
			int recLength = p.getLength();
			
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < recLength; j++) {
				sb.append((char) buffer[j]);
			}
			System.out.println("received: "+sb.toString());
			
			if (recLength <= 4) {   // TODO, hard code
				// assume a keep-alive message
				
				final KeepAliveMessage kam = new KeepAliveMessage(plState.side, buffer);
				
				((State)state).proxy.cast(kam);
				
			}
			else {
				SipMessage m =
						new SipMessage(buffer);
				
				InternalSipMessage iMsg =
						new InternalSipMessage(
								plState.side,
								m,
								null,
								null);
				
				((State)state).proxy.cast(iMsg);
			}

			return new CallResult(Atom.NOREPLY, null, state, 0);
			
		} catch (SocketTimeoutException e) {
			System.out.println("socket receive timeout");
			return new CallResult(Atom.NOREPLY, null, state, 0);

		}
		catch (IOException e) {
			e.printStackTrace();
		}

		return new CallResult(Atom.NOREPLY, null, state, 0);
	}

	@Override
	public void handleTerminate(Object state) {
		// TODO Auto-generated method stub

	}

}
