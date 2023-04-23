package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Level;
import st.foglo.genserver.Atom;

/**
 * The port listener.
 */
public class PlCb implements CallBack {
	
	private Side side;
	
	private boolean hasUpdatedPortSender = false;
	
	public class State {
		final Side side;
		final GenServer proxy;
		
		int listenerPort;
		DatagramSocket socket;
		
		final GenServer portSender;

		public State(
				Side side,
				GenServer aa,
				int listenerPort,
				DatagramSocket socket,
				GenServer portSender) {
			super();
			this.side = side;
			this.proxy = aa;
			this.listenerPort = listenerPort;
			this.socket = socket;
			this.portSender = portSender;
		}
	}
	
	///////////////////////////////////

	@Override
	public CallResult init(Object[] args) {
		
		side = (Side)args[0];
		
		final int port = ((Integer)args[3]).intValue();
		
		InetAddress localAddr = null;
		try {
			localAddr = InetAddress.getByAddress((byte[])args[2]);
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
			socket.setSoTimeout(300000); // TODO, hardcoded timeout
		} catch (SocketException e) {
			// not supposed to happen // TODO, exception handling
			e.printStackTrace();
		}
		
		
		Util.trace(Util.Level.debug, "%s listener init returns", toString());
		
		return new CallResult(
				Atom.OK,
				null,
				new State(
						(Side)args[0],
						(GenServer)args[1],
						port,
						socket,
						(GenServer)args[4]),
				
				0);
	}

	@Override
	public CallResult handleCast(Object message, Object state) {
		
		Util.trace(Level.debug, "%s unexpected", toString());

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

		Util.trace(Level.debug,
				"%s port listener timed out, port: %d",
				side.toString(),
				plState.listenerPort);
		
		DatagramSocket socket = ((State)state).socket;
		
		byte[] buffer = new byte[2500];                        // TODO hardcoded length
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			socket.receive(p);
			// Got some data!
			int recLength = p.getLength();
			
			
			// microSIP special handling
			if ((!hasUpdatedPortSender) && side == Side.UE) {
				
				final InetSocketAddress socketAddr = (InetSocketAddress) p.getSocketAddress();

				plState.portSender.cast(
						new UpdatePortSender(
								socketAddr.getAddress().getAddress(),
								Integer.valueOf(socketAddr.getPort())));
				
				hasUpdatedPortSender = true;
			}
			
			
			final InetSocketAddress sa = (InetSocketAddress) p.getSocketAddress();
			// so where did we get this from?
			if (side == Side.UE) {
				Util.trace(Level.verbose, "received from UE, host: %s, port: %s", sa.getHostString(), sa.getPort());
			}
			
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < recLength; j++) {
				sb.append((char) buffer[j]);
			}
			
			
			if (recLength <= 4) {   // TODO, hard code
				// assume a keep-alive message
				
				Util.trace(Level.verbose, "%s listener received: %s", toString(), Util.bytesToString(buffer, recLength));
				
				final KeepAliveMessage kam = new KeepAliveMessage(plState.side, buffer, recLength);
				plState.proxy.cast(kam);
				
			}
			else {
				
				Util.trace(Level.verbose, "%s listener received:%n%s", toString(), sb.toString());
				
				SipMessage m =
						new SipMessage(buffer, recLength);
				
				InternalSipMessage iMsg =
						new InternalSipMessage(
								plState.side,
								m,
								Util.digest(buffer, recLength),
								null,
								null);
				
				((State)state).proxy.cast(iMsg);
			}

			
		} catch (SocketTimeoutException e) {
			Util.trace(Level.debug, "%s socket receive timeout", toString());

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
	

	public String toString() {
		return String.format("%s listener:", side);
	}

}
