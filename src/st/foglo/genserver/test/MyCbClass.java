package st.foglo.genserver.test;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.OldCallResult;
import st.foglo.genserver.GenServer.CallResult;
import st.foglo.genserver.GenServer.CastResult;
import st.foglo.genserver.GenServer.InfoResult;
import st.foglo.genserver.GenServer.InitResult;
import st.foglo.genserver.Atom;

public final class MyCbClass implements CallBack {
	
	private int count = 0;
    
    /////////////////////

    @Override
    public InitResult init(Object[] ignoredArgs) {
        return new InitResult(Atom.OK);
    }

    @Override
    public CastResult handleCast(Object message) {
    	count++;
        System.out.println(String.format("new value: %d", count));
        return new CastResult(Atom.NOREPLY, OldCallResult.TIMEOUT_NEVER);
    }

    @Override
    public InfoResult handleInfo(Object message) {
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
