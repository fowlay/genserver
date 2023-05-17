package st.foglo.stateless_proxy;

public final class BlackList {

    private static String[] blacklist = new String[]{
        "+46734445555"
    };

    public static String[] blacklist() {
        return blacklist;
    }
}
