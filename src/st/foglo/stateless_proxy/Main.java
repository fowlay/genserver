package st.foglo.stateless_proxy;

import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Mode;

public final class Main {
	
	public static final Util.Level traceLevel = Util.Level.verbose;

    public static final Mode[] TRACE_MODES = new Mode[]{
        // Mode.DEBUG,
        // Mode.KEEP_ALIVE,
        Mode.START
        //Mode.SIP
    };

	public static final int SO_TIMEOUT = 150;
	public static final int KEEPALIVE_MSG_MAX_SIZE = 4;
	public static final int DATAGRAM_MAX_SIZE = 3000;
	public static final boolean NEVER = System.currentTimeMillis() == 314;
	public static final boolean ALWAYS = !NEVER;
	public static final boolean RECORD_ROUTE = ALWAYS;
	public static final boolean NIO = ALWAYS;
	
    public static final int[] localIpAddress = new int[]{10, 0, 0, 17};
	//public static final int[] localIpAddress = new int[]{10, 10, 69, 179};
	
	// where we listen for UEs
	public static final byte[] sipAddrUe = Util.toByteArray(localIpAddress);
	public static final Integer sipPortUe = Integer.valueOf(9060);
	
	// where we send to and listen for the SP
	public static final byte[] sipAddrSp = Util.toByteArray(localIpAddress);
	public static final Integer sipPortSp = Integer.valueOf(9070);
	
	// outgoing proxy
	public static final byte[] outgoingProxyAddrSp = Util.toByteArray(new int[]{193, 105, 226, 106});
	public static final Integer outgoingProxyPortSp = Integer.valueOf(5060);
	

	/////////////////////////////////

    public static void main(String[] args) {

        final GenServer pr = GenServer.start(
                new Presenter(),
                new Object[] {},
                "presenter",
                true);

        final GenServer px = GenServer.start(
                new PxCb(sipAddrUe, sipPortUe, sipAddrSp, sipPortSp, pr, new BlackList()),
                new Object[] {}, // TODO, just pass null
                "proxy",
                true);

        GenServer.start(
                new AccessUdpCb(Side.UE, px, sipAddrUe, sipPortUe.intValue()),
                new Object[] {}, // TODO, pass null simply
                "access-UDP",
                true);

        Util.trace(Util.Level.verbose, "Started access-UDP");

        GenServer.start(
                new CoreUdpCb(Side.SP, px, sipAddrSp, sipPortSp, outgoingProxyAddrSp, outgoingProxyPortSp),
                new Object[] {},
                "core-UDP",
                true);

        Util.trace(Util.Level.verbose, "Started core-UDP");

        Util.trace(Util.Level.debug, "Main: done init");

        for (; true;) {
            try {
                Thread.sleep(3600000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
