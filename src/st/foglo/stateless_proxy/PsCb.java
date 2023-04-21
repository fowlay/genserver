package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Map;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Atom;

/**
 * PortSender
 * 
 * Receives outgoing messages in instance format, and passes it to the associated
 * listener process in ready-to-send format
 */
public final class PsCb implements CallBack {

	public class State {
		final Side side;
		
		/** outbound proxy address */
		final byte[] peerAddr;
		
		/** outbound proxy port */
		final Integer peerPort;
		
		final DatagramSocket socket;

		public State(Side side, byte[] peerAddr, Integer peerPort, DatagramSocket socket) {
			super();
			this.side = side;
			this.peerAddr = peerAddr;
			this.peerPort = peerPort;
			this.socket = socket;
		}
		

	}
	
	
	
	////////////////////////////////
	
	@Override
	public CallResult init(Object args) {
		final Object[] aa = (Object[]) args;
		final int timeout = 2000;
		
		DatagramSocket s = null;
		try {
			s = new DatagramSocket();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}

		return new CallResult(Atom.OK,
				null,
				new State((Side)aa[0], (byte[])aa[1], (Integer)aa[2], s),
				timeout);
	}
	

	@Override
	public CallResult handleCast(Object message, Object state) {
		// send a datagram
		MsgBase mb = (MsgBase) message;
		if (mb instanceof KeepAliveMessage) {
			final KeepAliveMessage kam = (KeepAliveMessage) mb;
			
			// always use outbound proxy
		
			final DatagramSocket s = ((State)state).socket;
			
			final byte[] bytes = ((State)state).peerAddr;
			final int port = ((State)state).peerPort.intValue();

			try {
				final InetAddress ia = InetAddress.getByAddress(bytes);
				final SocketAddress sa = new InetSocketAddress(ia, port);
				s.connect(sa);
				final DatagramPacket p = new DatagramPacket(kam.buffer, kam.buffer.length);
				s.send(p);
				s.disconnect();

			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		else if (mb instanceof InternalSipMessage) {
			final InternalSipMessage ism = (InternalSipMessage) mb;
			
			// if the message comes with destination info, then use it,
			// else use outbound proxy
		
			final byte[] ba = toByteArray(ism.message);
			
			final DatagramSocket s = ((State)state).socket;
			
			final byte[] bytes = ism.destAddr == null ? ((State)state).peerAddr : ism.destAddr;
			final int port = ism.destAddr == null ? ((State)state).peerPort.intValue() : ism.destPort.intValue();

			try {
				final InetAddress ia = InetAddress.getByAddress(bytes);
				final SocketAddress sa = new InetSocketAddress(ia, port);
				s.connect(sa);
				
				final DatagramPacket p = new DatagramPacket(ba, ba.length);
				
				s.send(p);
				
				s.disconnect();

			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
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
		System.out.println(String.format("portsender timeout: %s", ((State)state).side));
		return new CallResult(Atom.NOREPLY, state);
	}

	@Override
	public void handleTerminate(Object state) {
		// TODO Auto-generated method stub

	}


	private byte[] toByteArray(SipMessage message) {
		
		byte[] ba = new byte[10000];   // TODO, ugly
		int k = 0;
		
		String firstLine = message.firstLine;
		
		for (byte b : firstLine.getBytes()) {
			ba[k++] = b;
		}
		ba[k++] = 13;
		ba[k++] = 10;
		
		
		Map<String, List<String>> hh  = message.headers;
		
		for (List<String> ss : hh.values()) {
			for (String s : ss) {
				for (byte b : s.getBytes()) {
					ba[k++] = b;
				}
				ba[k++] = 13;
				ba[k++] = 10;
			}
		}
	
		if (message.body != null) {
			ba[k++] = 13;
			ba[k++] = 10;
			
			for (byte b : message.body.getBytes()) {
				ba[k++] = b;
			}
		}
		
		byte[] result = new byte[k];
		
		for (int j = 0; j < k; j++) {
			result[j] = ba[j];
		}
		
		return result;
	}

}
