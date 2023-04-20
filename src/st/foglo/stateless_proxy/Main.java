package st.foglo.stateless_proxy;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.GenServer;

public final class Main {

	public static void main(String[] args) {




		// 1. create the proxy

		CallBack pxCb = new PxCb();
		
		Object[] pxInitArgs = new Object[]{};

		GenServer px = GenServer.start(pxCb, pxInitArgs);


		// 2a. create the ue port listener

		GenServer uePl = GenServer.start(
				new PlCb(),
		        new Object[] {
		        		Side.UE,
		        		px,
		        		new byte[] {10, 0, 0, 17},
		        		Integer.valueOf(5060),
		        		new byte[] {10, 0, 0, 14},
		        		Integer.valueOf(5060)});
		
		
		// 2b. create the ue port sender
		
		CallBack uePsCb = new PsCb();
		
		GenServer uePs = GenServer.start(uePsCb, new Object[]{Side.UE, uePl});
		


		// 3a. create the sp port listener
		
		GenServer spPl =
				GenServer.start(
						new PlCb(),
						new Object[]{
								Side.SP,
								px,
								new byte[]{10,0,0,17},
								Integer.valueOf(5070),
								new byte[]{toByte(193),105,toByte(226),106},
								Integer.valueOf(5060)});
		
		
		// 3b. create the ue port sender
		
		CallBack spPsCb = new PsCb();
		
		GenServer spPs = GenServer.start(spPsCb, new Object[]{Side.SP, spPl});
		
		


		// 4. update the proxy with references to (2b) and (3b)
		
		px.cast(new PortSendersMsg(uePs, spPs));
		
		
		System.out.println("done init");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static byte toByte(int k) {
		if (k >= 128) {
			return (byte) (k-256);
		}
		else {
			return (byte) k;
		}
	}
}
