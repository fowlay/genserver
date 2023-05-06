package st.foglo.stateless_proxy;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import st.foglo.genserver.Atom;
import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;

/**
 * Base class of UDP port processes
 */
public abstract class UdpCb implements CallBack {

	public UdpCb(Side side, GenServer proxy) {
		this.side = side;
		this.proxy = proxy;
	}

	protected final CallResult result = new CallResult(Atom.NOREPLY, CallResult.TIMEOUT_ZERO);
	protected final Side side;
	protected final GenServer proxy;
	protected byte[] outgoingProxyAddr;
	protected int outgoingProxyPort;
	protected DatagramSocket socket;

	@Override
	abstract public CallResult init(Object[] args);

	@Override
	abstract public CallResult handleCast(Object message);

	@Override
	public CallResult handleCall(Object message) {
		MsgBase mb = (MsgBase)message;
		if (mb instanceof GetLocalPortMsg) {
			final int localPort = socket.getLocalPort();
			return new CallResult(Atom.REPLY, Integer.valueOf(localPort));
		}
		else {
			throw new RuntimeException();
		}
	}

	@Override
	public CallResult handleInfo(Object message) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void handleTerminate() {
		socket.close();
	}

    public static SocketAddress createSocketAddress(byte[] addr, int port) throws UnknownHostException {
		return new InetSocketAddress(InetAddress.getByAddress(addr), port);
    }
}
