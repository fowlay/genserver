package st.foglo.genserver.test;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Atom;

public final class MyCbClass implements CallBack {
	
	private int count = 0;
    
    /////////////////////

    @Override
    public CallResult init(Object[] ignoredArgs) {
        return new CallResult(Atom.OK);
    }

    @Override
    public CallResult handleCast(Object message) {

    	count++;
        
        System.out.println(String.format("new value: %d", count));
        
        return new CallResult(Atom.NOREPLY, CallResult.TIMEOUT_NEVER);
    }

    @Override
    public CallResult handleInfo(Object message) {
    	return null;
    }

    @Override
    public CallResult handleCall(Object message) {
        return null;
    }

    @Override
    public void handleTerminate() {
    }
}
