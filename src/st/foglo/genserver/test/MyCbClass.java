package st.foglo.genserver.test;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Keyword;

public final class MyCbClass implements CallBack {
    
    /////////////////////

    @Override
    public Object init(Object args) {
        // ignore args for now
        return new MyState();
    }

    @Override
    public CallResult cast(Object message, Object state) {
        Integer u = ((MyState)state).count;
        int uNew = u.intValue() + 1;
        ((MyState)state).count = Integer.valueOf(uNew);
        
        System.out.println(String.format("new value: %d", uNew));
        
        return new CallResult(Keyword.noreply, state, -1);
    }

    @Override
    public void info(Object state) {
        // TODO Auto-generated method stub

    }

    @Override
    public CallResult call(Object state) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void terminate(Object state) {
        // TODO Auto-generated method stub

    }
}
