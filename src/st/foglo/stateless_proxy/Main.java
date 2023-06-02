package st.foglo.stateless_proxy;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

import st.foglo.genserver.GenServer;
import st.foglo.stateless_proxy.Util.Mode;

public final class Main {

    public static final Util.Level traceLevel = Util.Level.verbose;

    public static final Mode[] TRACE_MODES = new Mode[]{
        // Mode.DEBUG,
        // Mode.KEEP_ALIVE,
        // Mode.START,
        // Mode.SIP
    };

    public static final java.util.Properties DEFAULTS = new java.util.Properties();

    static {
        DEFAULTS.setProperty("so-timeout", "150");
        DEFAULTS.setProperty("keepalive-msg-max-size", "4");
        DEFAULTS.setProperty("datagram-max-size", "3000");

        DEFAULTS.setProperty("sip-port-ue", "9060");
        DEFAULTS.setProperty("sip-port-sp", "9070");

        DEFAULTS.setProperty("outgoing-proxy-addr-sp", "193.105.226.106");
        DEFAULTS.setProperty("outgoing-proxy-port-sp", "5060");
    }

    public static final Properties PROPERTIES = new Properties(DEFAULTS);

    static {
        try {
            PROPERTIES.load(getReader());
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static final int SO_TIMEOUT = getIntProperty("so-timeout");
    public static final int KEEPALIVE_MSG_MAX_SIZE = getIntProperty("keepalive-msg-max-size");
    public static final int DATAGRAM_MAX_SIZE = getIntProperty("datagram-max-size");

    public static final boolean NEVER = System.currentTimeMillis() == 314;
    public static final boolean ALWAYS = !NEVER;
    public static final boolean RECORD_ROUTE = ALWAYS;

    public static final int[] localIpAddress = getDottedIpAsInts("local-ip-address");

    // where we listen for UEs
    public static final byte[] sipAddrUe = Util.toByteArray(localIpAddress);
    public static final Integer sipPortUe = Integer.valueOf(getIntProperty("sip-port-ue"));

    // where we send to and listen for the SP
    public static final byte[] sipAddrSp = Util.toByteArray(localIpAddress);
    public static final Integer sipPortSp = Integer.valueOf(getIntProperty("sip-port-sp"));

    public static final byte[] outgoingProxyAddrSp = Util.toByteArray(getDottedIpAsInts("outgoing-proxy-addr-sp"));
    public static final Integer outgoingProxyPortSp = Integer.valueOf(5060);

    ////////////////////////////////////////////////////

    /**
     * Gets a reader for .stateless-proxy/config.properties in home directory.
     */
    private static Reader getReader() {
        final String home = System.getProperty("user.home");
        final String separator = System.getProperty("file.separator");
        try {
            return new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(home+separator+".stateless-proxy"+separator+"config.properties")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
            return null;
        }
    }

    private static int getIntProperty(String key) {
        return Integer.parseInt(PROPERTIES.getProperty(key));
    }

    private static int[] getDottedIpAsInts(String dottedIp) {
        return Util.dottedIpToInts(PROPERTIES.getProperty(dottedIp));
    }

    /////////////////////////////////

    public static void main(String[] args) {

        final GenServer pr = GenServer.start(
                new Presenter(),
                new Object[] {},
                "presenter",
                true);

        final GenServer px = GenServer.start(
                new PxCb(sipAddrUe, sipPortUe, sipAddrSp, sipPortSp, pr),
                new Object[] {}, // TODO, just pass null
                "proxy",
                true);

        GenServer.start(
                new AccessUdpCb(Side.UE, px, sipAddrUe, sipPortUe.intValue()),
                new Object[] {}, // TODO, pass null simply
                "access-UDP",
                true);

        Util.trace(Util.Level.verbose, "Started access-UDP");

        GenServer.start(
                new CoreUdpCb(Side.SP, px,
                        sipAddrSp, sipPortSp,
                        outgoingProxyAddrSp, outgoingProxyPortSp),
                new Object[] {},
                "core-UDP",
                true);

        Util.trace(Util.Level.verbose, "Started core-UDP");

        Util.trace(Util.Level.debug, "Main: done init");

        for (; true;) {
            try {
                Thread.sleep(3600000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
