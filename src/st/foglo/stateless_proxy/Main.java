package st.foglo.stateless_proxy;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.GenServer;

public final class Main {
	
	public static final Util.Level traceLevel = Util.Level.verbose;
	
	public static final byte[] sipAddrUe = Util.toByteArray(new int[]{10, 0, 0, 17});
	public static final Integer sipPortUe = Integer.valueOf(9060);
	
	public static final byte[] sipAddrSp = Util.toByteArray(new int[]{10, 0, 0, 17});
	public static final Integer sipPortSp = Integer.valueOf(9070);
	
	public static final byte[] outgoingProxyAddrUe = Util.toByteArray(new int[]{10, 0, 0, 17});
	public static final Integer outgoingProxyPortUe = Integer.valueOf(5060);
	
	public static final byte[] outgoingProxyAddrSp = Util.toByteArray(new int[]{193, 105, 226, 106});
	public static final Integer outgoingProxyPortSp = Integer.valueOf(5060);
	

	


	public static void main(String[] args) {

		// 1. create the proxy

		CallBack pxCb = new PxCb();

		GenServer px = GenServer.start(
				pxCb,
				new Object[]{
						sipAddrUe,
						sipPortUe,
						sipAddrSp,
						sipPortSp});

		
		
		// microSIP special support: Let the port listener know about the port sender
		
		// 2b. create the UE port sender
		
		// we are not a registrar, so we use outbound proxy pointing to the UE
		GenServer uePs = GenServer.start(
				new PsCb(),
				new Object[]{
						Side.UE,
						outgoingProxyAddrUe,
						outgoingProxyPortUe,
						px});
		
		
		

		// 2a. create the UE port listener

		GenServer.start(
				new PlCb(),
				new Object[] {
						Side.UE,
						px,
						sipAddrUe,
						sipPortUe,
						uePs});
		

		// 3b. create the SP port sender
		
		GenServer spPs =
				GenServer.start(
				new PsCb(),
				new Object[]{Side.SP,
						outgoingProxyAddrSp,
						outgoingProxyPortSp,
						px});


		// 3a. create the SP port listener

		GenServer.start(
				new PlCb(),
				new Object[] {
						Side.SP,
						px,
						sipAddrSp,
						sipPortSp,
						spPs});
		
		

		

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
