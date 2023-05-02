package st.foglo.stateless_proxy;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.GenServer;

public final class Main {
	
	public static final int SO_TIMEOUT = 100;
	
	public static final boolean NEVER = System.currentTimeMillis() == 314;
	
	public static final int[] localIpAddress = new int[]{10, 0, 0, 17};
	// public static final int[] localIpAddress = new int[]{10, 10, 69, 179};
	
	public static final Util.Level traceLevel = Util.Level.verbose;
	
	public static final byte[] sipAddrUe = Util.toByteArray(localIpAddress);
	public static final Integer sipPortUe = Integer.valueOf(9060);
	
	public static final byte[] sipAddrSp = Util.toByteArray(localIpAddress);
	public static final Integer sipPortSp = Integer.valueOf(9070);
	
	public static final byte[] outgoingProxyAddrUe = Util.toByteArray(localIpAddress);
	public static final Integer outgoingProxyPortUe = Integer.valueOf(5060);
	
	public static final byte[] outgoingProxyAddrSp = Util.toByteArray(new int[]{193, 105, 226, 106});
	public static final Integer outgoingProxyPortSp = Integer.valueOf(5060);
	

	


	public static void main(String[] args) {

		// 1. create the proxy

		CallBack pxCb = new PxCb(sipAddrUe, sipPortUe, sipAddrSp, sipPortSp);

		GenServer px = GenServer.start(
				pxCb,
				new Object[]{},
				"proxy");

		
		
		// microSIP special support: Let the port listener know about the port sender
		
		// 2b. create the UE port sender
		
		// we are not a registrar, so we use outbound proxy pointing to the UE
		GenServer uePs = GenServer.start(
				new PsCb(Side.UE, px, outgoingProxyAddrUe, outgoingProxyPortUe),
				new Object[]{},
				"UE-sender");
		
		
		

		// 2a. create the UE port listener

		GenServer.start(
				new PlCb(Side.UE, px, sipAddrUe, sipPortUe, uePs),
				new Object[] {},
				"UE-listener");
		

		// 3b. create the SP port sender
		
		GenServer spPs =
				GenServer.start(
				new PsCb(Side.SP, px, outgoingProxyAddrSp, outgoingProxyPortSp),
				new Object[]{},
				"SP-sender");


		// 3a. create the SP port listener
		// actually not needed!

		if (NEVER) {
			GenServer.start(
					new PlCb(Side.SP, px, sipAddrSp, sipPortSp, spPs),
					new Object[] {},
					"SP-listener");
		}
		
		

		

		// 4. update the proxy with references to (2b) and (3b)
		
		px.cast(new PortSendersMsg(
				uePs,
				spPs));

		Util.trace(Util.Level.debug, "Main: done init");
		
		try {
			Thread.sleep(3600000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
