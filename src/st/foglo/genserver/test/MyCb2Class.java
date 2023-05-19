package st.foglo.genserver.test;

import st.foglo.genserver.CallBackBase;
import st.foglo.genserver.GenServer;
import st.foglo.genserver.Atom;

public final class MyCb2Class extends CallBackBase {
    
    private int count = 0;
    

    /////////////////////////////////////////

    @Override
    public GenServer.InitResult init(Object[] args) {
        System.out.println(String.format("in init"));
        return new GenServer.InitResult(Atom.OK, 200);
    }

    @Override
    public GenServer.CastResult handleCast(Object message) {
        return null;
    }

    @Override
    public GenServer.InfoResult handleInfo(Object message) {

        count++;
        
        System.out.println(String.format("new count: %d", count));
        
        if (count == 3) {
            
            return new GenServer.InfoResult(Atom.STOP);
            
        }
        else {
            return new GenServer.InfoResult(Atom.NOREPLY, 2000);
        }
                
        

    }

    @Override
    public GenServer.CallResult  handleCall(Object message) {
//        Keyword kk = ((GsMessage)message).keyword;
//        Object m = ((GsMessage)message).object;
        return null;
    }

    @Override
    public void handleTerminate() {
        System.out.println(
                String.format("terminating, count: %d", count));
    }

}
