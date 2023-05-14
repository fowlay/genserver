package st.foglo.genserver.test;

// import static org.junit.Assert.*;

import st.foglo.genserver.CallBack;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import st.foglo.genserver.GenServer;

public class GenServer_test {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test() {
        
        CallBack myCb = new MyCbClass();
        
        GenServer gs = GenServer.start(myCb, null);

        
        gs.cast(null);
        gs.cast(null);
        gs.cast(null);
        gs.cast(null);
    }
    
    @Test
    public void timeout_test() {
        
        CallBack myCb = new MyCb2Class();
        
        GenServer.start(myCb, null, "my-cb-2", 0);

        
        try {
			Thread.sleep(800);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
    
    @Test
    public void call_test() {
        
        CallBack myCb = new MyCb3Class();
        
        GenServer gs = GenServer.start(myCb, null, "my-cb", 1);
        
        
        MyCb3Class.Product p = (MyCb3Class.Product)gs.call(gs, (new MyCb3Class()).new TwoFactors(8, 9));
        
        System.out.println(String.format("result: %d", p.product));
        
        gs.cast(null);

    }
}
