package st.foglo.genserver;

public final class GsMessage {
    
    public final Keyword keyword;
    public final Object object;
    
    public GsMessage(Keyword keyword, Object object) {
        this.keyword = keyword;
        this.object = object;
    }
}
