package st.foglo.stateless_proxy;

public final class BlackList {

    private static String[] blacklist = new String[]{
        "+46734445555",
        "+46738889999"
    };

    public static String[] blacklist() {
        return blacklist;
    }
}
