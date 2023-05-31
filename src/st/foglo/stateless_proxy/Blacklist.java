package st.foglo.stateless_proxy;

public final class Blacklist {

    private final String[] blacklist = new String[]{
        "+46734445555",
        "+46738889999",
        "+46707953031"
    };

    public String[] blacklist() {
        return blacklist;
    }
}
