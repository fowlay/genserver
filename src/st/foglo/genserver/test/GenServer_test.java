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
}
