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
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Level;
import st.foglo.genserver.Atom;

/**
 * The port listener.
 */
public class PlCb implements CallBack {
	
	final Side side;
	final GenServer proxy;
	
	final byte[] host;
	final int port;
	
	final GenServer portSender;
	
	int listenerPort;
	DatagramSocket socket = null;

	
	private boolean hasUpdatedPortSender = false;
	
	final CallResult result = new CallResult(Atom.NOREPLY, CallResult.TIMEOUT_ZERO);

	public PlCb(Side side, GenServer proxy, byte[] host, Integer port, GenServer portSender) {
		this.side = side;
		this.proxy = proxy;
		this.host = host;
		this.port = port.intValue();
		this.portSender = portSender;
	}
	
	///////////////////////////////////

	@Override
	public CallResult init(Object[] args) {
		
		// side = (Side)args[0];
		
		Util.seq(Level.verbose, side, Util.Direction.NONE, "init");
		
		// proxy = (GenServer)args[1];
		
		// host = (byte[])args[2];
		// port = ((Integer)args[3]).intValue();
		
		InetAddress localAddr = null;
		try {
			localAddr = InetAddress.getByAddress(host);
			socket = new DatagramSocket(port, localAddr);
			socket.setSoTimeout(300000); // TODO, hardcoded timeout  // why at all?
		} catch (Exception e) {
			e.printStackTrace();
		}

		Util.trace(Util.Level.debug, "%s listener init returns", toString());
		
	    // portSender = (GenServer)args[4];
		
		return result;
	}

	@Override
	public CallResult handleCast(Object message) {
		Util.trace(Level.debug, "%s unexpected", toString());
		return result;
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
			
			
			// final InetSocketAddress sa = (InetSocketAddress) p.getSocketAddress();
			// so where did we get this from?
//			if (side == Side.UE) {
//				//Util.trace(Level.verbose, "received from UE, host: %s, port: %s", sa.getHostString(), sa.getPort());
//				//Util.seq(Level.verbose, side, Direction.IN, String.format("rec fr UE: %s:%s", sa.getHostString(), sa.getPort()));
//			}
			
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < recLength; j++) {
				sb.append((char) buffer[j]);
			}
			
			
			if (recLength <= 4) {   // TODO, hard code
				// assume a keep-alive message
				
				Util.trace(Level.debug, "%s listener received: %s", toString(), Util.bytesToString(buffer, recLength));
				Util.seq(Level.verbose, side, Direction.IN, "k-a-m");
				
				final KeepAliveMessage kam = new KeepAliveMessage(side, buffer, recLength);
				proxy.cast(kam);
				
			}
			else {
				
				Util.trace(Level.debug, "%s listener received:%n%s", toString(), sb.toString());
				
				SipMessage m =
						new SipMessage(buffer, recLength);
				
				InternalSipMessage iMsg =
						new InternalSipMessage(
								side,
								m,
								Util.digest(buffer, recLength),
								null,
								null);
				
				Util.seq(Level.verbose, side, Direction.IN, iMsg.message.firstLine);
				
				proxy.cast(iMsg);
			}

			
		} catch (SocketTimeoutException e) {
			Util.trace(Level.debug, "%s socket receive timeout", toString());

		}
		catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	@Override
	public void handleTerminate() {
	}
	

	public String toString() {
		return String.format("%s listener:", side);
	}

}
