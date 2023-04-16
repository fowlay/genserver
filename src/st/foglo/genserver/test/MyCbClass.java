package st.foglo.genserver.test;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Keyword;

public final class MyCbClass implements CallBack {
	
	public final class MyState {
		// could be int as well
	    public Integer count = 0;
	}

    
    /////////////////////

    @Override
    public CallResult init(Object args) {
        // ignore args for now
        return new CallResult(Keyword.ok, null, new MyState());
    }

    @Override
    public CallResult handleCast(Object message, Object state) {
        Integer u = ((MyState)state).count;
        int uNew = u.intValue() + 1;
        ((MyState)state).count = Integer.valueOf(uNew);
        
        System.out.println(String.format("new value: %d", uNew));
        
        return new CallResult(Keyword.noreply, null, state, -1);
    }

    @Override
    public CallResult handleInfo(Object message, Object state) {
    	return null;
    }

    @Override
    public CallResult handleCall(Object message, Object state) {
        return null;
    }

    @Override
    public void handleTerminate(Object state) {
    }
}
