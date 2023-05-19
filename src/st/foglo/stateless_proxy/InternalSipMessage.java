package st.foglo.stateless_proxy;

public final class InternalSipMessage extends MsgBase implements Cloneable {
    

    public final Side side;            // the side where this message entered the proxy
    public SipMessage message;
    
    final byte[] destAddr;
    final Integer destPort;
    
    final byte[] sourceAddr;       // optional, when receiving from UE side this may be filled in
    final Integer sourcePort;

    public InternalSipMessage(
            Side side,
            SipMessage message,
            byte[] destAddr,
            Integer destPort) {
        this(side,
                message,
                destAddr,
                destPort,
                null,
                null);
    }
    
    
    public InternalSipMessage(
            Side side,
            SipMessage message,
            byte[] destAddr,
            Integer destPort,
            byte[] sourceAddr,
            Integer sourcePort) {
        super();
        this.side = side;
        this.message = message;
        this.destAddr = destAddr;
        this.destPort = destPort;
        this.sourceAddr = sourceAddr;
        this.sourcePort = sourcePort;
    }


    
    /////////////////////////


    public Object clone() throws CloneNotSupportedException {
        final Object result = super.clone();
        ((InternalSipMessage) result).message =
                (SipMessage) ((InternalSipMessage) result).message.clone();
        return result;
    }

    //////////////////////////////
    
    public InternalSipMessage setDestination(byte[] addr, Integer port) {
        return new InternalSipMessage(side, message, addr, port);
    }
}
