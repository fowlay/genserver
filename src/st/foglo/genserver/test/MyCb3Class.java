package st.foglo.genserver.test;

import st.foglo.genserver.CallBackBase;
import st.foglo.genserver.GenServer.CallResult;
import st.foglo.genserver.GenServer.CastResult;
import st.foglo.genserver.GenServer.InfoResult;
import st.foglo.genserver.GenServer.InitResult;
import st.foglo.genserver.Atom;

public final class MyCb3Class extends CallBackBase {
    
    public class Product {
        public final int product;
        public Product(int product) {
            this.product = product;
        }
    }
    
    public class TwoFactors {
        final int x;
        final int y;
        
        public TwoFactors(int x, int y) {
            super();
            this.x = x;
            this.y = y;
        }
    }
    
    /////////////////////

    @Override
    public InitResult init(Object[] args) {
        return new InitResult(Atom.OK);
    }

    @Override
    public CastResult handleCast(Object message) {
        return new CastResult(Atom.STOP);
    }

    @Override
    public InfoResult handleInfo(Object message) {
        return null;
    }

    @Override
    public CallResult handleCall(Object message) {
        
        int x = ((TwoFactors)message).x;
        int y = ((TwoFactors)message).y;
        System.out.println("multiply");
        return new CallResult(Atom.REPLY, new Product(x*y));
    }

    @Override
    public void handleTerminate() {
        System.out.println("terminating server");
    }
}
