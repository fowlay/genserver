package st.foglo.genserver.test;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Atom;

public final class MyCb2Class implements CallBack {
	
	private int count = 0;
	

	/////////////////////////////////////////

	@Override
	public CallResult init(Object[] args) {
		System.out.println(String.format("in init"));
		return new CallResult(Atom.OK, 200);
	}

	@Override
	public CallResult handleCast(Object message) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CallResult handleInfo(Object message) {

		count++;
		
		System.out.println(String.format("new count: %d", count));
		
		if (count == 3) {
			
			return new CallResult(Atom.STOP);
			
		}
		else {
			return new CallResult(Atom.NOREPLY, 2000);
		}
				
		

	}

	@Override
	public CallResult handleCall(Object message) {
//		Keyword kk = ((GsMessage)message).keyword;
//		Object m = ((GsMessage)message).object;
		return null;
	}

	@Override
	public void handleTerminate() {
		System.out.println(
				String.format("terminating, count: %d", count));
	}

}
