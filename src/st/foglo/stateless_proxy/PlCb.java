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
	
	public class PortListenerState {
		final Side side;
		final GenServer proxy;
		
		int listenerPort;
		DatagramSocket socket;
		
		byte[] peerAddr;
		Integer peerPort;
		

		
		public PortListenerState(
				Side side,
				GenServer aa,
				int listenerPort,
				DatagramSocket socket,
				byte[] peerAddr,
				Integer peerPort) {
			super();
			this.side = side;
			this.proxy = aa;
			this.listenerPort = listenerPort;
			this.socket = socket;
			
			this.peerAddr = peerAddr;
			this.peerPort = peerPort;
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
		
		byte[] peerAddr = (byte[])aa[4];
		Integer peerPort = (Integer)aa[5];
		
		System.out.println("listener init returns"); // seen
		
		return new CallResult(
				Atom.OK,
				null,
				new PortListenerState(
						(Side)aa[0],
						(GenServer)aa[1],
						port,
						socket,
						peerAddr,
						peerPort),
				
				0);
	}

	@Override
	public CallResult handleCast(Object message, Object state) {

		DatagramSocket socket = ((PortListenerState) state).socket;

		MsgBase mb = (MsgBase) message;
		if (mb instanceof BufferMsg) {
			BufferMsg bm = (BufferMsg) mb;
			DatagramPacket p = new DatagramPacket(bm.buffer, 0);
			try {
				final byte[] remoteAddrB = bm.destAddr == null ? ((PortListenerState) state).peerAddr : bm.destAddr;
				final InetAddress destAddr = InetAddress.getByAddress(remoteAddrB);

				final int destPort = bm.destAddr == null ? ((PortListenerState) state).peerPort.intValue()
				        : bm.destPort.intValue();

				socket.connect(destAddr, destPort);
				socket.send(p);
				socket.disconnect();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			throw new RuntimeException("cannot handle cast");
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
		
		final PortListenerState plState = (PortListenerState)state;
		System.out.println(String.format("timed out: %s, port: %d", plState.side, plState.listenerPort));
		
		DatagramSocket socket = ((PortListenerState)state).socket;
		
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
			
			
			SipMessage m =
					new SipMessage(buffer);
			
			InternalSipMessage iMsg =
					new InternalSipMessage(
							plState.side,
							m);
			
			
			((PortListenerState)state).proxy.cast(iMsg);
			
			
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
