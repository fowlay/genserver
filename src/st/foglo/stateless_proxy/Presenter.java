package st.foglo.stateless_proxy;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import st.foglo.genserver.Atom;
import st.foglo.genserver.CallBackBase;
import st.foglo.genserver.GenServer.CallResult;
import st.foglo.genserver.GenServer.CastResult;
import st.foglo.genserver.GenServer.InfoResult;
import st.foglo.genserver.GenServer.InitResult;
import st.foglo.stateless_proxy.SipMessage.Method;
import st.foglo.stateless_proxy.SipMessage.Type;

public class Presenter extends CallBackBase {

    private Map<String, Boolean> regTrans = new HashMap<String, Boolean>();

    private Map<String, String> previousRegMessage = new HashMap<String, String>();

    private Map<String, Long> blockingMessage = new HashMap<String, Long>();

    @Override
    public InitResult init(Object[] args) {
        return new InitResult(Atom.OK, TIMEOUT_NEVER);
    }

    @Override
    public CastResult handleCast(Object message) {

        // trace("we are in presenter");

        final InternalSipMessage ism = (InternalSipMessage) message;
        final SipMessage sm = ism.message;
        final String toUser = sm.getUser("To");
        final String fromUser = sm.getUser("From");
        final Type type = sm.type;
        final Method method = sm.getMethod();

        if (method == Method.REGISTER && type == Type.REQUEST) {
            // save the From tag, when response comes look it up
            regTrans.put(
                sm.getTag("From"),
                Boolean.valueOf(!sm.isDeRegister())); // true -> REG, false -> de-REG
        }
        else if (method == Method.REGISTER && type == Type.RESPONSE) {
            final int code = sm.getCode();
            if (code >= 200 && code < 300) {
                final String fromTag = sm.getTag("From");
                final Boolean reg = regTrans.get(fromTag);
                if (reg != null) {
                    regTrans.remove(fromTag);
                    if (reg.booleanValue()) {
                        present(Method.REGISTER, toUser, fromUser, false,
                                String.format("+++ %s registered", toUser));
                    }
                    else {
                        present(Method.REGISTER, toUser, fromUser, false,
                        String.format("+++ %s unregistered", toUser));
                    }
                }
            }
        }
        else if (method == Method.NOTIFY && type == Type.REQUEST && ism.side == Side.SP) {
            present(Method.NOTIFY, toUser, fromUser, false, sm.toString());
        }
        else if (sm.isBlacklisted() && ism.side == Side.SP) {
            // terminating INVITE blocked
            present(Method.INVITE, toUser, fromUser, true,
                String.format("blocking call from %s to %s", fromUser, toUser));
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }

        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    @Override
    public CallResult handleCall(Object message) {
        throw new UnsupportedOperationException("Unimplemented method 'handleCall'");
    }

    @Override
    public InfoResult handleInfo(Object message) {
        throw new UnsupportedOperationException("Unimplemented method 'handleInfo'");
    }

    @Override
    public void handleTerminate() {
    }

    
    private void present(Method method, String toUser, String fromUser,
            boolean blocked, String text) {
        if (method == Method.REGISTER) {
            // suppress repeated printouts
            final String previousMessage = previousRegMessage.get(toUser);
            if (previousMessage != null && previousMessage.equals(text)) {
                return;
            } else {
                previousRegMessage.put(toUser, text);
                System.out.println(text);
            }
        }
        else if (method == Method.INVITE && blocked) {
            // trace("######## present blocked INVITE");
        
            // Clean the map from stale entries
            final LinkedList<String> keys = new LinkedList<String>();
            for (Map.Entry<String, Long> me : blockingMessage.entrySet()) {
                if (System.currentTimeMillis() - me.getValue().longValue() > 35000) {
                    keys.add(me.getKey());
                }
            }
            for (String u : keys) {
                blockingMessage.remove(u);
            }

            final String key = Util.mergeStrings(fromUser, toUser);
            final Long millis = blockingMessage.get(key);
            if (millis == null) {
                System.out.println(text);
                blockingMessage.put(key, Long.valueOf(System.currentTimeMillis()));
            }
        }

        else {
            System.out.println(text);
        }
    }

    void trace(String string) {
        System.out.println(String.format("@@@ %s", string));
    }
}
