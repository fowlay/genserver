package st.foglo.stateless_proxy;

import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Mode;

public final class Main {
	
	public static final Util.Level traceLevel = Util.Level.verbose;


    public static final Mode[] TRACE_MODES = new Mode[]{
        Mode.SIP,
        //Mode.KEEP_ALIVE,
        Mode.START
    };

	public static final int SO_TIMEOUT = 150;
	public static final int KEEPALIVE_MSG_MAX_SIZE = 4;
	public static final int DATAGRAM_MAX_SIZE = 3000;
	public static final boolean NEVER = System.currentTimeMillis() == 314;
	public static final boolean ALWAYS = !NEVER;
	public static final boolean RECORD_ROUTE = ALWAYS;
	public static final boolean NIO = ALWAYS;
	
    public static final int[] localIpAddress = new int[]{10, 0, 0, 17};
	// public static final int[] localIpAddress = new int[]{10, 10, 69, 179};
	
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

		// Create proxy

		// final String ldt = LocalDateTime.now().toString();

		// Util.trace(Level.verbose, "ldt string: %s", ldt);

		// Util.trace(Level.verbose, "refined string: %s", Util.ldt());

		final GenServer px = GenServer.start(
				new PxCb(sipAddrUe, sipPortUe, sipAddrSp, sipPortSp),
				new Object[]{},  // TODO, just pass null
				"proxy",
                1);

		
		
		// microSIP special support: Let the port listener know about the port sender
		
		// 2b. create the UE port sender
		
		// we are not a registrar, so we use outbound proxy pointing to the UE
//		final GenServer uePs = GenServer.start(
//				new PsCb(Side.UE, px, outgoingProxyAddrUe, outgoingProxyPortUe),
//				new Object[]{},
//				"UE-sender");
		
		
		

		// 2a. create the UE port listener

//		GenServer.start(
//				new PlCb(Side.UE, px, sipAddrUe, sipPortUe, uePs),
//				new Object[] {},
//				"UE-listener");
		

		// 3b. create the SP port sender
		
//		GenServer spPs =
//				GenServer.start(
//				new PsCb(Side.SP, px, outgoingProxyAddrSp, outgoingProxyPortSp),
//				new Object[]{},
//				"SP-sender");
		
		
		final GenServer accessUdp =
				GenServer.start(
				new AccessUdpCb(Side.UE, px, sipAddrUe, sipPortUe.intValue()),
				new Object[]{},                   // TODO, pass null simply
				"access-UDP");

		Util.trace(Util.Level.verbose, "Started access-UDP");
		
		
		final GenServer coreUdp =
				GenServer.start(
				new CoreUdpCb(Side.SP, px, sipAddrSp, sipPortSp, outgoingProxyAddrSp, outgoingProxyPortSp),
				new Object[]{},
				"core-UDP",
                0);

				Util.trace(Util.Level.verbose, "Started core-UDP");


		// 3a. create the SP port listener
		// actually not needed!

//		if (NEVER) {
//			GenServer.start(
//					new PlCb(Side.SP, px, sipAddrSp, sipPortSp, spPs),
//					new Object[] {},
//					"SP-listener");
//		}
		
		

		

		// 4. update the proxy with references to (2b) and (3b)
		
		px.cast(new PortSendersMsg(accessUdp, coreUdp));

		Util.trace(Util.Level.debug, "Main: done init");
		
        for (; true; ) {
            try {
                Thread.sleep(3600000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
	}
}
