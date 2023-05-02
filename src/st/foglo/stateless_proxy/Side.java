package st.foglo.stateless_proxy;

/**
 * Indicator used in processes and internal messages.
 * 
 * A message gets tagged according to the side where
 * it entered.
 */
public enum Side {
	UE,   // User Equipment side
	SP,   // Service Provider side
	PX    // the proxy

}
