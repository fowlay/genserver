package st.foglo.genserver.test;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Keyword;

public final class MyCb3Class implements CallBack {
	
	public class Product {
		public final int product;
		public Product(int product) {
			this.product = product;
		}
	}
	
	public class TwoFactors {
		final int x;
		final int y;
		
		public TwoFactors(int x, int y) {
			super();
			this.x = x;
			this.y = y;
		}
	}
	
	/////////////////////

	@Override
	public CallResult init(Object args) {
		return new CallResult(Keyword.ok, null, new Object());
	}

	@Override
	public CallResult handleCast(Object message, Object state) {
		return new CallResult(Keyword.stop, null, state);
	}

	@Override
	public CallResult handleInfo(Object message, Object state) {
		return null;
	}

	@Override
	public CallResult handleCall(Object message, Object state) {
		
		int x = ((TwoFactors)message).x;
		int y = ((TwoFactors)message).y;
		System.out.println("multiply");
		return new CallResult(Keyword.reply, new Product(x*y), state);
	}

	@Override
	public void handleTerminate(Object state) {
		System.out.println("terminating server");
	}
}
