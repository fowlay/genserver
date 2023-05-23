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

    /**
     * This map represents expected ACKs, keyed by Call-ID and CSeq number
     */
    private Map<String, String> expectedAcks = new HashMap<String, String>();

    /**
     * This map represents calls that should be ignored, for example
     * the INVITE with no Authorization header.
     */
    private Map<String, Long> ignoredCalls = new HashMap<String, Long>();

 

    private int numericCallId = 1;
    

    enum DialogState {
        UNCONFIRMED,   // INVITE received
        CANCELED,      // CANCEL request seen
        EARLY,         // 1xx INVITE sent
        REJECTED,      // 486 INVITE sent
        CONFIRMED,     // 200 INVITE sent
        ESTABLISHED    // ACK received
    }

    class Dialog {
        DialogState state;
        final int callNo;
        boolean presented180 = false;
        String newKey = null;    // if assigned, this instance is a forward reference

        public Dialog(DialogState state) {
            this.state = state;
            this.callNo = getAndIncrCallNo();
        }

        public Dialog(DialogState state, String newKey) {
            this.state = state;
            this.callNo = -1;
            this.newKey = newKey;
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
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isBusyHere()) {
            return handleTermInvRespFinBusyHere(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isFinal()) {
            return handleTermInvRespFin(sm, callId);
        }
        else if (method == Method.ACK && type == Type.REQUEST && ism.side == Side.SP) {
            return handleTermAckReq(sm, callId);
        }

        else if (method == Method.CANCEL && type == Type.REQUEST && ism.side == Side.SP) {
            return handleTermCanReq(sm, callId);
        }
        else if (method == Method.CANCEL && type == Type.RESPONSE && ism.side == Side.UE && sm.isFinal()) {
            return handleTermCanRespFin(sm, callId);
        }


        else if (method == Method.INVITE && type == Type.REQUEST && ism.side == Side.UE) {
            return handleOrigInvReq(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isProvisional()) {
            return handleOrigInvRespProv(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isBusyHere()) {
            return handleOrigInvRespFinBusyHere(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isFinal()) {
            return handleOrigInvRespFin(sm, callId);
        }
        else if (method == Method.ACK && type == Type.REQUEST && ism.side == Side.UE) {
            return handleOrigAckReq(sm, callId);
        }

        else if (method == Method.CANCEL && type == Type.REQUEST && ism.side == Side.UE) {
            return handleOrigCanReq(sm, callId);
        }
        else if (method == Method.CANCEL && type == Type.RESPONSE && ism.side == Side.SP && sm.isFinal()) {
            return handleOrigCanRespFin(sm, callId);
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
        final String text = String.format("%s %s", toUser, reg ? "registered\n" : "de-registered\n");
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
            int count = 0;
            for (String line : bodyLines) {
                if (count < bodyLines.length-1) {
                    present(line.trim());
                }
                else {
                    present(line.trim()+"\n");
                }
                count++;
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
            final int callNo = (new Dialog(DialogState.UNCONFIRMED)).callNo;
            present(String.format("[%d] blocking incoming call from %s to %s", callNo, fromUser, toUser));
            blockingMessage.put(key, Long.valueOf(System.currentTimeMillis()));
        }
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleTermInvReq(SipMessage sm, String callId) {
        final String key = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog dialog = new Dialog(DialogState.UNCONFIRMED);
        dialogs.put(key, dialog);
        present(dialog.callNo, "incoming call from: %s", sm.getUser("From"));
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
            dialog.state = DialogState.EARLY;
            final String newKey = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            dialogs.put(key, new Dialog(null, newKey));  // insert a forward reference
            dialogs.put(newKey, dialog);
            if (sm.getCode() == 180) {
                present(dialog.callNo, "ringing here");
            }
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermInvRespFinBusyHere(SipMessage sm, String callId) {
        // we trust that a provisional response has been sent
        final String key = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
        final Dialog dialog = dialogs.get(key);
        if (dialog == null) {
            present("no dialog found when handling 486 response");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            present(dialog.callNo, "busy here\n");
            dialog.state = DialogState.REJECTED;
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermInvRespFin(SipMessage sm, String callId) {
        final String key1 = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog dialog1 = dialogs.get(key1);
        // we expect to get either a dialog (if there was no previous 1xx),
        // or a forward reference

        if (dialog1.newKey == null) {
            // no forward reference
            // 2xx has been sent as the very first response
            final String key2 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            // dialogs.remove(key1);
            dialogs.put(key1, new Dialog(null, key2));  // fwd ref, even if noone needs it 
            dialog1.state = DialogState.CONFIRMED;
            dialogs.put(key2, dialog1);
            present(dialog1.callNo, "accepting incoming call");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            // assume dialog established by a previous 1xx response
            final String key2 = dialog1.newKey;
            final Dialog dialog2 = dialogs.get(key2);
            if (dialog2 == null) {
                present("no dialog found");
                return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
            }
            else {
                dialog2.state = DialogState.CONFIRMED;
                present(dialog2.callNo, "accepting incoming call");
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
        else if (dialog.state == DialogState.CANCELED) {
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else if (dialog.state == DialogState.REJECTED) {
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            dialog.state = DialogState.ESTABLISHED;
            present(dialog.callNo, "call established\n");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermCanReq(SipMessage sm, String callId) {
        final String key1 = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog d1 = dialogs.get(key1);
        if (d1.newKey == null) {
            d1.state = DialogState.CANCELED;
            present(d1.callNo, "call canceled by %s\n", sm.getUser("From"));
        }
        else {
            final String key2 = d1.newKey;
            final Dialog d2 = dialogs.get(key2);
            d2.state = DialogState.CANCELED;
            present(d2.callNo, "call canceled by %s\n", sm.getUser("From"));
        }
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleTermCanRespFin(SipMessage sm, String callId) {
        // don't present anything
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleOrigInvReq(SipMessage sm, String callId) {
        final LinkedList<String> hh = sm.getHeaderFields("Authorization");
        if (hh.isEmpty()) {
            // do not present the INVITE without credentials
            final String key = Util.mergeStrings(callId, sm.getCseqNumberAsString());
            ignoredCalls.put(key, Long.valueOf(System.currentTimeMillis()));
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        } else {
            final String key = Util.mergeStrings(callId, sm.getTag("From"));
            final Dialog dialog = new Dialog(DialogState.UNCONFIRMED);
            dialogs.put(key, dialog);
            present(dialog.callNo, "outgoing call from %s to %s", sm.getUser("From"), sm.getUser("To"));
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }


    private CastResult handleOrigInvRespProv(SipMessage sm, String callId) {
        final String key = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog dialog = dialogs.get(key);
        if (dialog == null) {
            // not expected
            final String newKey = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            final Dialog dialog2 = dialogs.get(newKey);
            if (dialog2 != null) {
                final int code = sm.getCode();
                if (!(code == 180 && dialog2.presented180)) {
                    present(dialog2.callNo, "provsional response, code: %d", code);
                }
            }
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else if (dialog.newKey != null) {
            // we found a forward reference; use it
            final Dialog dialog2 = dialogs.get(dialog.newKey);
            dialog2.state = DialogState.EARLY;
            if (sm.getCode() == 180 && !dialog2.presented180) {
                present(dialog2.callNo, "ringing at %s", sm.getUser("To"));
                dialog2.presented180 = true;
            }
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            // we found the actual dialog
            dialog.state = DialogState.EARLY;
            final String newKey = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            dialogs.put(key, new Dialog(null, newKey));
            dialogs.put(newKey, dialog);
            if (sm.getCode() == 180) {
                present(dialog.callNo, "ringing at %s", sm.getUser("To"));
                dialog.presented180 = true;
            }
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleOrigInvRespFinBusyHere(SipMessage sm, String callId) {
        // we trust that a provisional response has been sent
        final String key = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
        final Dialog dialog = dialogs.get(key);
        if (dialog == null) {
            present("no dialog found when handling 486 response");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        } else {
            present(dialog.callNo, "busy here\n");
            dialog.state = DialogState.REJECTED;
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleOrigInvRespFin(SipMessage sm, String callId) {
        final String key1 = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog dialog1 = dialogs.get(key1);
        if (dialog1 != null) {
            if (dialog1.newKey != null) {
                // a redirect
                final Dialog d2 = dialogs.get(dialog1.newKey);
                d2.state = DialogState.CONFIRMED;
                final String key3 = Util.mergeStrings(callId, sm.getCseqNumberAsString());
                final String key2 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
                expectedAcks.put(key3, key2);
                present(d2.callNo, "outgoing call accepted by %s", sm.getUser("To"));
                return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
            }
            else {
                // not a redirect
                final String key2 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
                dialogs.put(key1, new Dialog(null, key2));
                dialog1.state = DialogState.CONFIRMED;
                dialogs.put(key2, dialog1);
                present(dialog1.callNo, "outgoing call accepted by %s", sm.getUser("To"));
                return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
            }
           
        }
        else {
            // assume dialog established by a previous 1xx response
            final String key2 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            final Dialog dialog2 = dialogs.get(key2);
            if (dialog2 == null) {
                present("no dialog found");
                return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
            }
            else {
                dialog2.state = DialogState.CONFIRMED;
                present(dialog2.callNo, "outgoing call accepted by %s", sm.getUser("To"));
                final String key3 = Util.mergeStrings(callId, sm.getCseqNumberAsString());
                expectedAcks.put(key3, key2);
                return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
            }
        }
    }



    private CastResult handleOrigAckReq(SipMessage sm, String callId) {
        final String key3 = Util.mergeStrings(callId, sm.getCseqNumberAsString());
        if (ignoredCalls.get(key3) != null) {
            ignoredCalls.remove(key3);
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            final String key2 = expectedAcks.get(key3);
            if (key2 == null) {
                final String key4 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
                final Dialog d = dialogs.get(key4);
                if (d.state == DialogState.CANCELED) {
                    return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
                }
                else {
                    present("unexpected ACK request");
                    return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
                }
            }
            else {
                expectedAcks.remove(key3);
                final Dialog dialog = dialogs.get(key2);
                if (dialog == null) {
                    present("no dialog found");
                    return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
                }
                else if (dialog.state == DialogState.REJECTED) {
                    return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
                }
                else {
                    dialog.state = DialogState.ESTABLISHED;
                    present(dialog.callNo, "outgoing call established\n");
                    return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
                }
            }
        }
    }


    



    private CastResult handleOrigCanReq(SipMessage sm, String callId) {
        final String key1 = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog d1 = dialogs.get(key1);
        if (d1.newKey == null) {
            d1.state = DialogState.CANCELED;
            present(d1.callNo, "call canceled by %s\n", sm.getUser("From"));
        }
        else {
            final String key2 = d1.newKey;
            final Dialog d2 = dialogs.get(key2);
            d2.state = DialogState.CANCELED;
            present(d2.callNo, "call canceled by %s\n", sm.getUser("From"));
        }
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleOrigCanRespFin(SipMessage sm, String callId) {
        // don't present anything
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
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
            present(dialog.callNo, "call released by %s\n", sm.getUser("From"));
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
        present(String.format(format, text));
    }

    static void present(int callNo, String text) {
        System.out.println(String.format("[log] [%d] %s", callNo, text));
    }

    static void present(int callNo, String format, String text) {
        present(callNo, String.format(format, text));
    }

    static void present(int callNo, String format, String text1, String text2) {
        present(callNo, String.format(format, text1, text2));
    }

    static void present(int callNo, String format, int number) {
        present(callNo, String.format(format, number));
    }

    private int getAndIncrCallNo() {
        return numericCallId++;
    }
}
