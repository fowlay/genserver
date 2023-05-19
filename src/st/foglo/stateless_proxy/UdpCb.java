package st.foglo.stateless_proxy;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;

import st.foglo.genserver.Atom;
import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallBackBase;
import st.foglo.genserver.GenServer.CallResult;
import st.foglo.genserver.GenServer.CastResult;
import st.foglo.genserver.GenServer.InfoResult;
import st.foglo.genserver.GenServer.InitResult;
import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Direction;
import st.foglo.stateless_proxy.Util.Mode;

/**
 * Base class of UDP port processes
 */
public abstract class UdpCb extends CallBackBase {

    public UdpCb(Side side, GenServer proxy) {
        this.side = side;
        this.proxy = proxy;
    }

    protected final InitResult initResult = new InitResult(Atom.OK, CallBack.TIMEOUT_ZERO);
    protected final CastResult castResult = new CastResult(Atom.NOREPLY, CallBack.TIMEOUT_ZERO);
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

        public ChannelWrapper(
                Side side,
                byte[] localAddr,
                int localPort,
                byte[] remoteAddr,
                int remotePort) {
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
                //     Util.seq(Level.verbose, side, Direction.NONE,
                //         String.format("connect to: %s:%d",
                //                       Util.bytesToIpAddress(remoteAddr),
                //                       remotePort));
                //     this.channel.connect(saRemote);
                // } catch (IOException e) {
                //     e.printStackTrace();
                // }
            }
        }
    }

    @Override
    public InitResult init(Object[] args) {
        thread = Thread.currentThread();
        byteBuffer = ByteBuffer.allocate(Main.DATAGRAM_MAX_SIZE);
        return new InitResult(Atom.OK, TIMEOUT_ZERO);
    }

    @Override
    abstract public CastResult handleCast(Object message);


    protected CastResult handleKeepAliveMessage(Side side, KeepAliveMessage mb) {

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
            return castResult;
        } else {
            throw new RuntimeException();
        }
    }


    @Override
    public CallResult handleCall(Object message) {
        MsgBase mb = (MsgBase)message;
        if (mb instanceof GetLocalPortMsg) {
            final int localPort = socket.getLocalPort();
            return new CallResult(Atom.REPLY, Integer.valueOf(localPort), TIMEOUT_ZERO);
        }
        else {
            throw new RuntimeException();
        }
    }

    @Override
    public InfoResult handleInfo(Object message) {
        return null;
    }

    @Override
    public void handleTerminate() {
        socket.close();
    }




    public static SocketAddress createSocketAddress(byte[] addr, int port)
            throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByAddress(addr), port);
    }

    class UdpInfoResult {
        final boolean timedOut; // TODO, misleading name, we get interrupted typically
        final byte[] sourceAddr;
        final Integer sourcePort;
        final int datagramSize;  // TODO, use of -1, is it correct? Use 0?

        public UdpInfoResult(
                boolean timedOut,
                byte[] sourceAddr,
                Integer sourcePort,
                int datagramSize) {
            this.timedOut = timedOut;
            this.sourceAddr = sourceAddr;
            this.sourcePort = sourcePort;
            this.datagramSize = datagramSize;
        }
    }

    protected InitResult udpInit(Side side, byte[] addr, int port) {
        return new InitResult(Atom.OK, TIMEOUT_ZERO);
    }

    protected UdpInfoResult udpInfo(Side side) {
        // reading from UDP

        if (!channelWrapper.isOpen()) {
            // Util.seq(Level.verbose, side, Direction.NONE, "PREPARE AGAIN");
            channelWrapper.prepare();
        } else {
            // Util.seq(Level.verbose, side, Direction.NONE, "no need to prepare");
        }

        SocketAddress remoteSa = null;
        try {
            byteBuffer.clear();

            // YES, the channel is blocking by default
            // Util.seq(Level.verbose, side, Direction.NONE,
            // String.format("channel is blocking: %b",
            // channelWrapper.channel.isBlocking()));

            // be cooperative!
            canBeInterrupted(true);

            remoteSa = channelWrapper.channel.receive(byteBuffer);

            canBeInterrupted(false);

        } catch (ClosedByInterruptException e) {

            canBeInterrupted(false);

            Thread.interrupted(); // essential

            return new UdpInfoResult(true, null, null, 0); // as if we would have timed out

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
    }

    protected void udpCast(Side side, InternalSipMessage ism) {
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
            // Util.seq(Level.verbose, side, Direction.NONE,
            // String.format("ism.destPort is null"));
            // }
            // if (ism.destAddr == null) {
            // Util.seq(Level.verbose, side, Direction.NONE,
            // String.format("ism.destAddr is null"));
            // }

            sa = UdpCb.createSocketAddress(ism.destAddr, ism.destPort.intValue());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        try {
            Util.seq(Mode.DEBUG, side, Direction.NONE, "sending!");
            final int nofBytesSent = channelWrapper.channel.send(byteBuffer, sa);
            Util.seq(Mode.DEBUG, side, Direction.NONE, "bytes sent: %d", nofBytesSent);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
