package st.foglo.stateless_proxy;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.GenServer;

public final class Main {

	public static void main(String[] args) {




		// 1. create the proxy

		CallBack pxCb = new PxCb();
		
		Object[] pxInitArgs = new Object[]{};

		GenServer px = GenServer.start(pxCb, pxInitArgs);


		// 2a. create the UE port listener

		GenServer.start(
				new PlCb(),
				new Object[] {
						Side.UE,
						px,
						new byte[] {10, 0, 0, 17},
						Integer.valueOf(5060)});
		
		
		// 2b. create the UE port sender
		
		// we are not a registrar, so we use outbound proxy pointing to the UE
		GenServer uePs = GenServer.start(
				new PsCb(),
				new Object[]{Side.UE,
						new byte[]{10, 0, 0, 13},
						Integer.valueOf(5060)});
		


		// 3a. create the SP port listener

		GenServer.start(
				new PlCb(),
				new Object[] {
						Side.SP,
						px,
						new byte[] {10, 0, 0, 17},
						Integer.valueOf(5070)});
		
		
		// 3b. create the SP port sender
		
		GenServer spPs =
				GenServer.start(
				new PsCb(),
				new Object[]{Side.SP, new byte[]{1,2,3,4}, Integer.valueOf(5060)});  // hard code TODO
		

		// 4. update the proxy with references to (2b) and (3b)
		// TODO, duplicate code
		
		px.cast(new PortSendersMsg(
				uePs,
				spPs,
				
				new byte[] {10, 0, 0, 17},
				Integer.valueOf(5060),
				
				new byte[] {10, 0, 0, 17},
				Integer.valueOf(5070)));
		
		
		System.out.println("done init");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static byte toByte(int k) {
		if (k >= 128) {
			return (byte) (k-256);
		}
		else {
			return (byte) k;
		}
	}
}
