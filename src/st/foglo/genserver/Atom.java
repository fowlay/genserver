
package st.foglo.genserver;

public enum Atom {
	// used in GsMessage
	TIMEOUT,
    CAST,
    CALL,
    
    // used in CallResult
    OK,
    REPLY,
    NOREPLY,
    STOP
}
