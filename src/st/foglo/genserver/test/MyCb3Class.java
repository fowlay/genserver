package st.foglo.genserver.test;

import st.foglo.genserver.CallBack;
import st.foglo.genserver.CallResult;
import st.foglo.genserver.Atom;

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
	public CallResult init(Object[] args) {
		return new CallResult(Atom.OK);
	}

	@Override
	public CallResult handleCast(Object message) {
		return new CallResult(Atom.STOP);
	}

	@Override
	public CallResult handleInfo(Object message) {
		return null;
	}

	@Override
	public CallResult handleCall(Object message) {
		
		int x = ((TwoFactors)message).x;
		int y = ((TwoFactors)message).y;
		System.out.println("multiply");
		return new CallResult(Atom.REPLY, new Product(x*y));
	}

	@Override
	public void handleTerminate() {
		System.out.println("terminating server");
	}
}
