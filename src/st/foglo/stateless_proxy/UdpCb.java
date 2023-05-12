package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;

import st.foglo.genserver.Atom;
import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Mode;

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

	protected final byte[] recBuffer = new byte[Main.DATAGRAM_MAX_SIZE];


	protected DatagramChannel channel;

	protected ChannelWrapper channelWrapper;

	protected volatile Thread thread;

	protected ByteBuffer byteBuffer;

	class ChannelWrapper {

		final Side side;
		
		final byte[] localAddr;
		final int localPort;

		final byte[] remoteAddr;
		final int remotePort;

		DatagramChannel channel;

		public ChannelWrapper(Side side, byte[] localAddr, int localPort, byte[] remoteAddr, int remotePort) {
			this.side = side;
			this.localAddr = localAddr;
			this.localPort = localPort;
			this.remoteAddr = remoteAddr;
			this.remotePort = remotePort;
		}

		boolean isOpen() {
			return this.channel != null && this.channel.isOpen();
		}

		void prepare() {
			// side effect on 'channel'

			try {
				this.channel = DatagramChannel.open();
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (side == Side.UE) {
				SocketAddress sa = null;
				try {
					sa = createSocketAddress(localAddr, localPort);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}

				try {
					this.channel.bind(sa);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else if (side == Side.SP) {
				//SocketAddress saRemote = null;
				SocketAddress saLocal = null;
				try {
					// TOXDO - Main.sipAddrSp should be passed as an argument
					//saRemote = UdpCb.createSocketAddress(remoteAddr, remotePort);
					saLocal = UdpCb.createSocketAddress(localAddr, localPort);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}

				try {
					this.channel.bind(saLocal);
				} catch (IOException e) {
					e.printStackTrace();
				}
				// try {
				// 	Util.seq(Level.verbose, side, Direction.NONE,
				// 	    String.format("connect to: %s:%d", Util.bytesToIpAddress(remoteAddr), remotePort));
				// 	this.channel.connect(saRemote);
				// } catch (IOException e) {
				// 	e.printStackTrace();
				// }
			}
		}
	}

	class Interrupter implements Runnable {

		final Thread targetThread;

		Interrupter(Thread thread) {
			this.targetThread = thread;
		}

		@Override
		public void run() {

			try {
				Thread.sleep(Main.SO_TIMEOUT);
			} catch (InterruptedException e) {
				// not expected to happen
				e.printStackTrace();
			}
			targetThread.interrupt();

			
		}
	}





	@Override
	public CallResult init(Object[] args) {
		thread = Thread.currentThread();
		byteBuffer = ByteBuffer.allocate(Main.DATAGRAM_MAX_SIZE);
		return null;
	}

	@Override
	abstract public CallResult handleCast(Object message);


	protected CallResult handleKeepAliveMessage(Side side, KeepAliveMessage mb) {

		if (Main.NIO) {
			if (!channelWrapper.isOpen()) {
				channelWrapper.prepare();
			}
			if (side == Side.SP) {
				// always use outbound proxy
				Util.seq(Mode.KEEP_ALIVE, side, Direction.OUT, mb.toString());

				byteBuffer.clear();
				byteBuffer.put(mb.buffer);
				
				SocketAddress sa = null;
				try {
					sa = UdpCb.createSocketAddress(outgoingProxyAddr, outgoingProxyPort);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				try {
					channelWrapper.channel.send(byteBuffer, sa);
				} catch (IOException e) {
					e.printStackTrace();
				}
				return result;
			}
			else {
				throw new RuntimeException();
			}


		}
		else {
			// legacy implementation

			if (side == Side.UE) {
				Util.seq(Mode.KEEP_ALIVE, side, Direction.NONE, "dropping keepAlive");
				return result;
			}
			else if (side == Side.SP) {
				// always use outbound proxy
				Util.seq(Mode.KEEP_ALIVE, side, Direction.OUT, mb.toString());
				try {
					final DatagramPacket p = new DatagramPacket(mb.buffer, mb.size);
					socket.send(p);
				} catch (IOException e) {
					e.printStackTrace();
				}
				return result;
			}
			else {
				throw new RuntimeException();
			}

		}




	
	}


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
		return null;
	}

	@Override
	public void handleTerminate() {
		socket.close();
	}




    public static SocketAddress createSocketAddress(byte[] addr, int port) throws UnknownHostException {
		return new InetSocketAddress(InetAddress.getByAddress(addr), port);
    }

	class UdpInfoResult {
		final boolean timedOut;
		final byte[] sourceAddr;
		final Integer sourcePort;
		final int datagramSize;
		public UdpInfoResult(boolean timedOut, byte[] sourceAddr, Integer sourcePort, int datagramSize) {
			this.timedOut = timedOut;
			this.sourceAddr = sourceAddr;
			this.sourcePort = sourcePort;
			this.datagramSize = datagramSize;
		}


	}

	protected CallResult udpInit(Side side, byte[] addr, int port) {

		if (Main.NIO) {
			// nio implementation

			// try {
			// 	channel = DatagramChannel.open();
			// } catch (IOException e) {
			// 	e.printStackTrace();
			// }

			// if (side == Side.UE) {
			// 	SocketAddress sa = null;
			// 	try {
			// 		sa = createSocketAddress(addr, port);
			// 	} catch (UnknownHostException e) {
			// 		e.printStackTrace();
			// 	}
			// 	try {
			// 		channel.bind(sa);
			// 	} catch (IOException e) {
			// 		e.printStackTrace();
			// 	}
			// }
			// else if (side == Side.SP) {
			// 	SocketAddress saRemote = null;
			// 	SocketAddress saLocal = null;
			// 	try {
			// 		// TOXDO - Main.sipAddrSp should be passed as an argument
			// 		saRemote = UdpCb.createSocketAddress(addr, port);
			// 		saLocal = UdpCb.createSocketAddress(Main.sipAddrSp, 0);
			// 	} catch (UnknownHostException e) {
			// 		e.printStackTrace();
			// 	}

			// 	try {
			// 		channel.bind(saLocal);
			// 	} catch (IOException e) {
			// 		e.printStackTrace();
			// 	}
			// 	try {
			// 		channel.connect(saRemote);
			// 	} catch (IOException e) {
			// 		e.printStackTrace();
			// 	}
			// }
			return result;
			
		} else {
			// legacy implementation

			try {
				final SocketAddress sa = UdpCb.createSocketAddress(addr, port);
				if (side == Side.UE) {
					socket = new DatagramSocket(sa);
					socket.setSoTimeout(Main.SO_TIMEOUT);
				}
				else if (side == Side.SP) {
					socket = new DatagramSocket();
					socket.setSoTimeout(Main.SO_TIMEOUT);
					socket.connect(sa);
				}
				return result;

			} catch (SocketException e) {
				e.printStackTrace();
				return new CallResult(Atom.IGNORE);

			} catch (UnknownHostException e) {
				e.printStackTrace();
				return new CallResult(Atom.IGNORE);
			}
		}
	}

	protected UdpInfoResult udpInfo(Side side) {
		// reading from UDP
		if (Main.NIO) {
			// nio implementation

			if (!channelWrapper.isOpen()) {
				//Util.seq(Level.verbose, side, Direction.NONE, "PREPARE AGAIN");
				channelWrapper.prepare();
			}
			else {
				//Util.seq(Level.verbose, side, Direction.NONE, "no need to prepare");
			}

			SocketAddress remoteSa = null;
			try {
				byteBuffer.clear();

				// YES, the channel is blocking
				// Util.seq(Level.verbose, side, Direction.NONE,
				// 	String.format("channel is blocking: %b", channelWrapper.channel.isBlocking()));

				remoteSa = channelWrapper.channel.receive(byteBuffer);

			} catch (ClosedByInterruptException e) {
				// Util.seq(Level.verbose, side, Direction.NONE, "interrupted!"); // seen
				Thread.interrupted(); // essential

				// try {
				// 	channelWrapper.channel.close();  // ?
				// } catch (IOException e1) {
				// 	e1.printStackTrace();
				// }

				return new UdpInfoResult(true, null, null, -1); // means we timed out
			} catch (IOException e) {
				e.printStackTrace();
			}

			final int nofBytes = byteBuffer.position();
			byteBuffer.rewind();
			Util.seq(Mode.DEBUG, side, Direction.NONE, String.format("extract %d bytes", nofBytes));
			byteBuffer.get(recBuffer, 0, nofBytes);

			return new UdpInfoResult(
					false,
					((InetSocketAddress) remoteSa).getAddress().getAddress(),
					((InetSocketAddress) remoteSa).getPort(),
					nofBytes);

		} else {
			// legacy implementation
			if (side == Side.UE || side == Side.SP) {
				// consider not having the 'side' argument
				// get data into recBuffer
				final DatagramPacket p = new DatagramPacket(recBuffer, recBuffer.length);
				try {
					socket.receive(p);
				} catch (SocketTimeoutException e) {
					return new UdpInfoResult(true, null, null, -1);
				} catch (IOException e) {
					e.printStackTrace();
				}
				return new UdpInfoResult(
						false,
						p.getAddress().getAddress(),
						Integer.valueOf(p.getPort()),
						p.getLength());
			} else {
				throw new RuntimeException();
			}
		}
	}

	protected void udpCast(Side side, InternalSipMessage ism) {
		if (Main.NIO) {
			// nio implementation

			if (!channelWrapper.isOpen()) {
				channelWrapper.prepare();
			}

			// send, following the legacy pattern

			final byte[] ba = ism.message.toByteArray();
			byteBuffer.clear();
			byteBuffer.put(ba, 0, ba.length);

			final int pos = byteBuffer.position();
			byteBuffer.limit(pos);
			byteBuffer.position(0);

			SocketAddress sa = null;
			try {
				// if (ism.destPort == null) {
				// 	Util.seq(Level.verbose, side, Direction.NONE,
				// 			String.format("ism.destPort is null"));
				// }
				// if (ism.destAddr == null) {
				// 	Util.seq(Level.verbose, side, Direction.NONE,
				// 			String.format("ism.destAddr is null"));
				// }
				
				sa = UdpCb.createSocketAddress(ism.destAddr, ism.destPort.intValue());
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}

			try {
				Util.seq(Mode.DEBUG, side, Direction.NONE, "sending!");
				final int nofBytesSent = channelWrapper.channel.send(byteBuffer, sa);
				Util.seq(Mode.DEBUG, side, Direction.NONE, String.format("bytes sent: %d", nofBytesSent));

			} catch (IOException e) {
				e.printStackTrace();
			}

			return;
		} else {
			if (side == Side.UE || side == Side.SP) {
				if (side == Side.UE) {
					try {
						final SocketAddress sa = UdpCb.createSocketAddress(ism.destAddr, ism.destPort.intValue());
						socket.connect(sa);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					} catch (SocketException e) {
						e.printStackTrace();
					}
				}
				final byte[] ba = ism.message.toByteArray();
				final DatagramPacket p = new DatagramPacket(ba, ba.length);
				try {
					socket.send(p);
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (side == Side.UE) {
					socket.disconnect();
				}
				return;
			} else {
				throw new RuntimeException();
			}
		}
	}
}
