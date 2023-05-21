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

    /**
     * REGISTER transactions; key is: From-tag of REGISTER request
     */
    private Map<String, Boolean> regTrans = new HashMap<String, Boolean>();

    private Map<String, String> previousRegMessage = new HashMap<String, String>();

    private Map<String, Long> blockingMessage = new HashMap<String, Long>();

    private Map<String, Dialog> dialogs = new HashMap<String, Dialog>();

    enum DialogState {
        UNCONFIRMED,   // INVITE received
        EARLY,         // 1xx INVITE sent
        CONFIRMED,     // 200 INVITE sent
        ESTABLISHED    // ACK received
    }

    class Dialog {
        DialogState state;

        public Dialog(DialogState state) {
            this.state = state;
        }
    }

    @Override
    public InitResult init(Object[] args) {
        return new InitResult(Atom.OK, TIMEOUT_NEVER);
    }

    @Override
    public CastResult handleCast(Object message) {
        final InternalSipMessage ism = (InternalSipMessage) message;
        final SipMessage sm = ism.message;
        final String toUser = sm.getUser("To");
        final String fromUser = sm.getUser("From");
        final Type type = sm.type;
        final Method method = sm.getMethod();
        final String callId = sm.getTopHeaderField("Call-ID");

        if (method == Method.REGISTER && type == Type.REQUEST) {
            return handleRegReq(sm);
        }
        else if (method == Method.REGISTER && type == Type.RESPONSE && sm.isSuccess()) {
            return handleRegResp(sm, toUser, fromUser);
        }
        else if (method == Method.NOTIFY && type == Type.REQUEST && ism.side == Side.SP) {
            return handleTermNotifyReq(sm);
        }
        else if (method == Method.INVITE && type == Type.REQUEST && ism.side == Side.SP && sm.isBlacklisted(fromUser)) {
            return handleTermInvReqBlacklisted(fromUser, toUser);
        }
        else if (method == Method.INVITE && type == Type.REQUEST && ism.side == Side.SP) {
            return handleTermInvReq(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isProvisional()) {
            return handleTermInvRespProv(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isFinal()) {
            return handleTermInvRespFin(sm, callId);
        }
        else if (method == Method.ACK && type == Type.REQUEST && ism.side == Side.SP) {
            return handleTermAckReq(sm, callId);
        }


        else if (method == Method.BYE && type == Type.RESPONSE && ism.side == Side.SP) {
            // reverse BYE
            return handleTermByeRespReverse(sm, callId);
        }

        else if (method == Method.BYE && type == Type.RESPONSE && ism.side == Side.UE) {
            // regular BYE
            return handleTermByeRespRegular(sm, callId);
        }



        else {
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

  
   


    private CastResult handleRegReq(SipMessage sm) {
        regTrans.put(
                sm.getTag("From"),
                Boolean.valueOf(!sm.isDeRegister()));
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleRegResp(SipMessage sm, String toUser, String fromUser) {
        final String fromTag = sm.getTag("From");
        final Boolean reg = regTrans.get(fromTag);
        if (reg != null) {
            regTrans.remove(fromTag);
        }
        final String text = String.format("+++ %s %s", toUser, reg ? "registered" : "de-registered");
            // suppress repeated printouts
            final String previousMessage = previousRegMessage.get(toUser);
            if (previousMessage != null && previousMessage.equals(text)) {
                return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
            } else {
                previousRegMessage.put(toUser, text);
                present(text);
            }
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleTermNotifyReq(SipMessage sm) {

        final String ct = sm.getTopHeaderField("Content-Type");
        if ("application/simple-message-summary".equals(ct)) {
            final String[] bodyLines = sm.body.split("\n");
            for (String line : bodyLines) {
                present(line.trim());
            }
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        } else {
            present(sm.toString());
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermInvReqBlacklisted(String fromUser, String toUser) {

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
            present(String.format("blocking incoming call from %s to %s", fromUser, toUser));
            blockingMessage.put(key, Long.valueOf(System.currentTimeMillis()));
        }
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleTermInvReq(SipMessage sm, String callId) {
        final String key = Util.mergeStrings(callId, sm.getTag("From"));
        dialogs.put(key, new Dialog(DialogState.UNCONFIRMED));
        present(String.format("incoming call from: %s", sm.getUser("From")));
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleTermInvRespProv(SipMessage sm, String callId) {
        final String key = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog dialog = dialogs.get(key);
        if (dialog == null) {
            present("unexpected INVITE response");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            dialogs.remove(key);
            dialog.state = DialogState.EARLY;
            final String newKey = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            dialogs.put(newKey, dialog);
            if (sm.getCode() == 180) {
                present("ringing");
            }
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermInvRespFin(SipMessage sm, String callId) {
        final String key1 = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog dialog1 = dialogs.get(key1);
        if (dialog1 != null) {
            // 2xx has been sent as the very first response
            final String key2 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            dialogs.put(key2, new Dialog(DialogState.CONFIRMED));
            present("accepting incoming call");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            // dialog established by a previous 1xx response
            final String key2 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            final Dialog dialog2 = dialogs.get(key2);
            if (dialog2 == null) {
                present("no dialog found");
                return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
            }
            else {
                dialog2.state = DialogState.CONFIRMED;
                present("accepting incoming call");
                return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
            }
        }
    }

    private CastResult handleTermAckReq(SipMessage sm, String callId) {
        final String key = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
        final Dialog dialog = dialogs.get(key);
        if (dialog == null) {
            present("no dialog found");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            dialog.state = DialogState.ESTABLISHED;
            present("call established");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    
    private CastResult handleTermByeRespRegular(SipMessage sm, String callId) {
        final String key = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
        final Dialog dialog = dialogs.get(key);
        if (dialog == null) {
            present("no dialog found");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            dialogs.remove(key);
            present("call released by %s", sm.getUser("From"));
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermByeRespReverse(SipMessage sm, String callId) {
        return handleTermByeRespRegular(sm, callId);
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

    static void present(String text) {
        System.out.println(String.format("[log] %s", text));
    }

    static void present(String format, String text) {
        System.out.println(String.format("[log] %s", String.format(format, text)));
    }
}
