package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import st.foglo.genserver.Atom;
import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Level;

/**
 * Purpose: Handle a single message to be sent, destination provided,
 * using a newly created socket.
 */
public final class RespSenderCb implements CallBack {
	
	final Side side;
	
	public RespSenderCb(Side side) {
		this.side = side;
	}
	
	///////////////////

	@Override
	public CallResult init(Object[] args) {
		
		Util.seq(Level.verbose, side, Util.Direction.NONE, "init");
		
		return new CallResult(Atom.OK, null);
	}

	@Override
	public CallResult handleCast(Object message) {
		// handle a message to be sent; address and port also passed in
		// after sending, terminate
		
		MsgBase mb = (MsgBase) message;
		if (mb instanceof InternalSipMessage) {
			
			final InternalSipMessage ism = (InternalSipMessage) mb;
			
			Util.trace(Level.debug, "%s send a forwarded response: %s", side.toString(), ism.message.toString());
			Util.seq(Level.verbose, side, Direction.OUT, ism.message.firstLine);
			
            // assume destination is provided with the message
		
			final byte[] ba = Util.toByteArray(ism.message);
			
			try{
			final DatagramSocket s = new DatagramSocket();

				final InetAddress ia = InetAddress.getByAddress(ism.destAddr);
				final SocketAddress sa = new InetSocketAddress(ia, ism.destPort.intValue());
				s.connect(sa);
				
				final DatagramPacket p = new DatagramPacket(ba, ba.length);
				
				s.send(p);
				
				s.disconnect();
				
				s.close();

			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
		}

		return new CallResult(Atom.STOP);
	}

	@Override
	public CallResult handleCall(Object message) {
		// not used
		return null;
	}

	@Override
	public CallResult handleInfo(Object message) {
		// not used
		return null;
	}

	@Override
	public void handleTerminate() {
		Util.trace(Level.debug, "%s terminating", side.toString());
	}

}
