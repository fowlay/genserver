package st.foglo.genserver.test;

// import static org.junit.Assert.*;

import st.foglo.genserver.CallBack;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util;

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
        
        GenServer gs = GenServer.start(myCb, null, "gs1");

        
        gs.cast(null);
        gs.cast(null);
        gs.cast(null);
        gs.cast(null);
    }
    
    @Test
    public void timeout_test() {
        
        CallBack myCb = new MyCb2Class();
        
        GenServer.start(myCb, null, "my-cb-2");

        try {
			Thread.sleep(800);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
    
    @Test
    public void call_test() {
        
        CallBack myCb = new MyCb3Class();
        
        GenServer gs = GenServer.start(myCb, null, "my-cb");

        
        MyCb3Class.Product p = (MyCb3Class.Product)gs.call(gs, (new MyCb3Class()).new TwoFactors(8, 9));
        
        System.out.println(String.format("result: %d", p.product));
        
        gs.cast(null);

    }

    @Test
    public void merge_test() {
        final String tag1 = "dvionsdvoisnvoidnv5678583g%/&/";
        final String tag2 = "rgrghh............,,,,,,,,,,,,,,,,,,,,,,";

        String m1 = Util.mergeStrings(tag1, tag2);
        String m2 = Util.mergeStrings(tag2, tag1);
        System.out.println(String.format("merged string: %s", m1));
        assertEquals(m1, m2);
    }
}
