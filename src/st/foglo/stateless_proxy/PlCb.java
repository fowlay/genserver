package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
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
	GenServer proxy;
	int listenerPort;
	DatagramSocket socket = null;
	GenServer portSender;
	
	private boolean hasUpdatedPortSender = false;
	
	///////////////////////////////////

	@Override
	public CallResult init(Object[] args) {
		
		side = (Side)args[0];
		
		proxy = (GenServer)args[1];
		
		final byte[] host = (byte[])args[2];
		final int port = ((Integer)args[3]).intValue();
		
		InetAddress localAddr = null;
		try {
			localAddr = InetAddress.getByAddress(host);
			socket = new DatagramSocket(port, localAddr);
			socket.setSoTimeout(300000); // TODO, hardcoded timeout
		} catch (Exception e) {
			e.printStackTrace();
		}

		Util.trace(Util.Level.debug, "%s listener init returns", toString());
		
	    portSender = (GenServer)args[4];
		
		return new CallResult(Atom.OK, CallResult.TIMEOUT_ZERO);
	}

	@Override
	public CallResult handleCast(Object message) {
		Util.trace(Level.debug, "%s unexpected", toString());
		return new CallResult(Atom.NOREPLY);
	}

	@Override
	public CallResult handleCall(Object message) {
		throw new UnsupportedOperationException();
	}

	@Override
	public CallResult handleInfo(Object message) {
		
		// read from the socket, with a timeout

		Util.trace(Level.debug,
				"%s port listener timed out, port: %d",
				side.toString(),
				listenerPort);
		
		
		byte[] buffer = new byte[2500];                        // TODO hardcoded length
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			socket.receive(p);
			// Got some data!
			int recLength = p.getLength();
			
			
			// microSIP special handling
			
			// update the port-sender process to use same address and port
			// that the current message was received from
			if ((!hasUpdatedPortSender) && side == Side.UE) {
				
				final InetSocketAddress socketAddr = (InetSocketAddress) p.getSocketAddress();

				portSender.cast(
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
				
				final KeepAliveMessage kam = new KeepAliveMessage(side, buffer, recLength);
				proxy.cast(kam);
				
			}
			else {
				
				Util.trace(Level.verbose, "%s listener received:%n%s", toString(), sb.toString());
				
				SipMessage m =
						new SipMessage(buffer, recLength);
				
				InternalSipMessage iMsg =
						new InternalSipMessage(
								side,
								m,
								Util.digest(buffer, recLength),
								null,
								null);
				
				proxy.cast(iMsg);
			}

			
		} catch (SocketTimeoutException e) {
			Util.trace(Level.debug, "%s socket receive timeout", toString());

		}
		catch (IOException e) {
			e.printStackTrace();
		}

		return new CallResult(Atom.NOREPLY, CallResult.TIMEOUT_ZERO);
	}

	@Override
	public void handleTerminate() {
	}
	

	public String toString() {
		return String.format("%s listener:", side);
	}

}
