package st.foglo.genserver;

public abstract class CallBackBase implements CallBack {

    private final Object monitorCanBeInterrupted = new Object();
    protected volatile boolean canBeInterrupted;

    protected boolean canBeInterrupted() {
        synchronized (monitorCanBeInterrupted) {
            return canBeInterrupted;
        }
    }

    protected void canBeInterrupted(boolean canBeInterrupted) {
        synchronized (monitorCanBeInterrupted) {
            this.canBeInterrupted = canBeInterrupted;
        }
    }
}
