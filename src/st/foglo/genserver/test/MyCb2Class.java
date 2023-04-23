package st.foglo.genserver.test;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Atom;

public final class MyCb2Class implements CallBack {
	
	public final class MyState {
	    public int count = 0;
	    
	    
	    public MyState() {
	    	this(0);
	    }

	    public MyState(int count) {
	    	this.count = count;
	    }
	}
	

	
	/////////////////////////////////////////

	@Override
	public CallResult init(Object[] args) {
		System.out.println(String.format("in init"));
		return new CallResult(Atom.OK, null, new MyState(), 200);
	}

	@Override
	public CallResult handleCast(Object message, Object state) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {

		int newCount = 1 + ((MyState)state).count;
		MyState newState = new MyState(newCount);
		
		System.out.println(String.format("newCount: %d", newCount));
		
		if (newCount == 3) {
			
			return new CallResult(Atom.STOP, null, newState);
			
		}
		else {
			return new CallResult(Atom.NOREPLY, null, newState, 2000);
		}
				
		

	}

	@Override
	public CallResult handleCall(Object message, Object state) {
//		Keyword kk = ((GsMessage)message).keyword;
//		Object m = ((GsMessage)message).object;
		return null;
	}

	@Override
	public void handleTerminate(Object state) {
		System.out.println(
				String.format("terminating, count: %d", ((MyState)state).count));
	}

}
