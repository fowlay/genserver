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
		
		CallBack uePlCb = new PlCb();
		
		GenServer uePl = GenServer.start(uePlCb, new Object[]{Atom.ue, px});
		
		
		// 2b. create the ue port sender
		
		CallBack uePsCb = new PsCb();
		
		GenServer uePs = GenServer.start(uePsCb, new Object[]{Atom.ue});
		


		// 3a. create the sp port listener
		
		CallBack spPlCb = new PlCb();
		
		GenServer spPl = GenServer.start(spPlCb, new Object[]{Atom.sp, px});
		
		
		// 3b. create the ue port sender
		
		CallBack spPsCb = new PsCb();
		
		GenServer spPs = GenServer.start(spPsCb, new Object[]{Atom.sp});
		
		


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

}
