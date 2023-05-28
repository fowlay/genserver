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

    private enum MessageMode {
        DEBUG,
        INFO,
        WARNING
    }

    private static final MessageMode[] MESSAGE_MODES =
        new MessageMode[]{
            MessageMode.WARNING,
            MessageMode.INFO
            //MessageMode.DEBUG
        };

    /**
     * REGISTER transactions; key is: From-tag of REGISTER request
     */
    private Map<String, Boolean> regTrans = new HashMap<String, Boolean>();

    private Map<String, String> previousRegMessage = new HashMap<String, String>();

    private Map<String, Long> blockingMessage = new HashMap<String, Long>();

    /**
     * Map of Dialog instances, using keys formed from Call-ID, From-tag and
     * (optionally) To-tag. Multiple keys may point to the same Dialog instance.
     */
    private Map<String, Dialog> dialogs = new HashMap<String, Dialog>();

    /**
     * This map is used by expected ACK requests. The key is callId x CSeq number.
     * Values are dialog instances. When garbage collecting based on dialog lifetimes,
     * clean this map before removing any dialogs.
     */
    private Map<String, Dialog> expectedAcks = new HashMap<String, Dialog>();

    /**
     * This map represents calls that should be ignored, for example
     * the INVITE with no Authorization header. The key is callId x CSeq number,
     * and the values are simply timestamps for garbage collection.
     */
    private Map<String, Long> ignoredCalls = new HashMap<String, Long>();


    /**
     * Garbage collected map from Call-ID to call number; only calls that get
     * presented will be numbered.
     */
    private Map<String, CallNumber> callNumbers = new HashMap<String, CallNumber>();

    private int numericCallId = 1;
    

    enum DialogState {
        IGNORE,        // Dialog will not be used
        UNCONFIRMED,   // INVITE received
        CANCELED,      // CANCEL request seen
        EARLY,         // 1xx INVITE sent
        REJECTED,      // 486 INVITE sent
        CONFIRMED,     // 200 INVITE sent
        ESTABLISHED    // ACK received
    }

    class Dialog {
        DialogState state;
        boolean presented180 = false;
        String newKey = null;    // if assigned, this instance is a forward reference

        public Dialog(DialogState state) {
            this.state = state;
        }

        // public Dialog(DialogState state, String newKey) {
        //     this.state = state;
        //     this.callNo = -1;
        //     this.newKey = newKey;
        // }
    }

    class CallNumber {
        final int callNo;
        final long whenCreated;
        public CallNumber(int callNo, long whenCreated) {
            this.callNo = callNo;
            this.whenCreated = whenCreated;
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
            debug("method == Method.REGISTER && type == Type.REQUEST");
            return handleRegReq(sm);
        }
        else if (method == Method.REGISTER && type == Type.RESPONSE && sm.isSuccess()) {
            debug("method == Method.REGISTER && type == Type.RESPONSE && sm.isSuccess()");
            return handleRegResp(sm, toUser, fromUser);
        }
        else if (method == Method.NOTIFY && type == Type.REQUEST && ism.side == Side.SP) {
            debug("method == Method.NOTIFY && type == Type.REQUEST && ism.side == Side.SP");
            return handleTermNotifyReq(sm);
        }
        else if (method == Method.INVITE && type == Type.REQUEST && ism.side == Side.SP && sm.isBlacklisted(fromUser)) {
            debug("method == Method.INVITE && type == Type.REQUEST && ism.side == Side.SP && sm.isBlacklisted(fromUser)");
            return handleTermInvReqBlacklisted(callId, fromUser, toUser);
        }

        else if (method == Method.INVITE && type == Type.REQUEST && ism.side == Side.SP) {
            debug("method == Method.INVITE && type == Type.REQUEST && ism.side == Side.SP");
            return handleTermInvReq(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isProvisional()) {
            debug("");
            return handleTermInvRespProv(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isBusyHere()) {
            debug("method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isBusyHere()");
            return handleTermInvRespFinBusyHere(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isFinal()) {
            debug("method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.UE && sm.isFinal()");
            return handleTermInvRespFin(sm, callId);
        }
        else if (method == Method.ACK && type == Type.REQUEST && ism.side == Side.SP) {
            debug("method == Method.ACK && type == Type.REQUEST && ism.side == Side.SP");
            return handleTermAckReq(sm, callId);
        }

        else if (method == Method.CANCEL && type == Type.REQUEST && ism.side == Side.SP) {
            debug("method == Method.CANCEL && type == Type.REQUEST && ism.side == Side.SP");
            return handleTermCanReq(sm, callId);
        }
        else if (method == Method.CANCEL && type == Type.RESPONSE && ism.side == Side.UE && sm.isFinal()) {
            debug("method == Method.CANCEL && type == Type.RESPONSE && ism.side == Side.UE && sm.isFinal()");
            return handleTermCanRespFin(sm, callId);
        }

        // Originating INVITE

        else if (method == Method.INVITE && type == Type.REQUEST && ism.side == Side.UE) {
            debug("method == Method.INVITE && type == Type.REQUEST && ism.side == Side.UE");
            return handleOrigInvReq(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isProvisional()) {
            debug("method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isProvisional()");
            return handleOrigInvRespProv(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isBusyHere()) {
            debug("method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isBusyHere()");
            return handleOrigInvRespFinBusyHere(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isClientFailure()) {
            debug("method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isClientFailure()");
            return handleOrigInvRespFinClientFailure(sm, callId);
        }
        else if (method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isFinal()) {
            debug("method == Method.INVITE && type == Type.RESPONSE && ism.side == Side.SP && sm.isFinal()");
            return handleOrigInvRespFin(sm, callId);
        }
        else if (method == Method.ACK && type == Type.REQUEST && ism.side == Side.UE) {
            debug("method == Method.ACK && type == Type.REQUEST && ism.side == Side.UE");
            return handleOrigAckReq(sm, callId);
        }

        else if (method == Method.CANCEL && type == Type.REQUEST && ism.side == Side.UE) {
            debug("method == Method.CANCEL && type == Type.REQUEST && ism.side == Side.UE");
            return handleOrigCanReq(sm, callId);
        }
        else if (method == Method.CANCEL && type == Type.RESPONSE && ism.side == Side.SP && sm.isFinal()) {
            debug("method == Method.CANCEL && type == Type.RESPONSE && ism.side == Side.SP && sm.isFinal()");
            return handleOrigCanRespFin(sm, callId);
        }



        else if (method == Method.BYE && type == Type.RESPONSE && ism.side == Side.SP) {
            // reverse BYE
            debug("method == Method.BYE && type == Type.RESPONSE && ism.side == Side.SP");
            return handleTermByeRespReverse(sm, callId);
        }
        else if (method == Method.BYE && type == Type.RESPONSE && ism.side == Side.UE) {
            // regular BYE
            debug("method == Method.BYE && type == Type.RESPONSE && ism.side == Side.UE");
            return handleTermByeRespRegular(sm, callId);
        }



        else {
            debug("no presentation");
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

    private CastResult handleTermInvReqBlacklisted(String callId, String fromUser, String toUser) {

        // Clean the map from stale entries
        // TODO, make this part of garbage collection
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
            present(String.format("[%d] blocking incoming call from %s to %s", callId, fromUser, toUser));
            blockingMessage.put(key, Long.valueOf(System.currentTimeMillis()));
        }
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleTermInvReq(SipMessage sm, String callId) {
        // TODO, handle re-INVITE as well
        final String key = Util.mergeStrings(callId, sm.getTag("From"));
        final Dialog dialog = new Dialog(DialogState.UNCONFIRMED);
        dialogs.put(key, dialog);
        present(callId, "incoming call from: %s", sm.getUser("From"));
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleTermInvRespProv(SipMessage sm, String callId) {
        final Dialog dialog = findDialog(sm, callId);
        if (dialog == null) {
            present("unexpected INVITE response");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            dialog.state = DialogState.EARLY;
            final String newKey = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
            dialogs.put(newKey, dialog);
            if (sm.getCode() == 180) {
                present(callId, "ringing here");
            }
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermInvRespFinBusyHere(SipMessage sm, String callId) {
        // we trust that a provisional response has been sent
        final Dialog dialog = findDialog(sm, callId);
        if (dialog == null) {
            present("no dialog found when handling 486 response");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            present(callId, "busy here\n");
            dialog.state = DialogState.REJECTED;
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleOrigInvRespFinClientFailure(SipMessage sm, String callId) {
        final Dialog dialog = findDialog(sm, callId);
        if (dialog == null) {
            final int code = sm.getCode();
            present("no dialog found when handling %d response", code);
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            dialog.state = DialogState.REJECTED;
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermInvRespFin(SipMessage sm, String callId) {
        final Dialog dialog1 = findDialog(sm, callId);
        dialog1.state = DialogState.CONFIRMED;
        final String key2 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
        dialogs.put(key2, dialog1);
        present(callId, "accepting incoming call");
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);

    }

    private CastResult handleTermAckReq(SipMessage sm, String callId) {
        final Dialog dialog = findDialog(sm, callId);
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
            present(callId, "call established\n");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleTermCanReq(SipMessage sm, String callId) {
        final Dialog d1 = findDialog(sm, callId);
        d1.state = DialogState.CANCELED;
        present(callId, "call canceled by %s\n", sm.getUser("From"));
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
            final String key1 = Util.mergeStrings(callId, sm.getCseqNumberAsString());
            ignoredCalls.put(key1, Long.valueOf(System.currentTimeMillis()));
            final Dialog dialog = new Dialog(DialogState.IGNORE);
            expectedAcks.put(key1, dialog);
            final String key2 = Util.mergeStrings(callId, sm.getTag("From"));
            dialogs.put(key2, dialog);
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        } else {
            final String key = Util.mergeStrings(callId, sm.getTag("From"));
            final Dialog dialog = new Dialog(DialogState.UNCONFIRMED);
            dialogs.put(key, dialog);
            present(callId, "outgoing call from %s to %s", sm.getUser("From"), sm.getUser("To"));
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleOrigInvRespProv(SipMessage sm, String callId) {
        final Dialog dialog = findDialog(sm, callId);
        dialog.state = DialogState.EARLY;
        final String newKey = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
        dialogs.put(newKey, dialog);
        if (sm.getCode() == 180 && !dialog.presented180) {
            present(callId, "ringing at %s", sm.getUser("To"));
            dialog.presented180 = true;
        }
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);

    }

    private CastResult handleOrigInvRespFinBusyHere(SipMessage sm, String callId) {
        final Dialog dialog = findDialog(sm, callId);
        if (dialog == null) {
            present("no dialog found when handling 486 response");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        } else {
            present(callId, "busy here\n");
            dialog.state = DialogState.REJECTED;
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleOrigInvRespFin(SipMessage sm, String callId) {
        final Dialog dialog1 = findDialog(sm, callId);
        dialog1.state = DialogState.CONFIRMED;
        final String key2 = Util.mergeStrings(callId, sm.getTag("From"), sm.getTag("To"));
        dialogs.put(key2, dialog1);
        present(callId, "outgoing call accepted by %s", sm.getUser("To"));
        final String key3 = Util.mergeStrings(callId, sm.getCseqNumberAsString());
        expectedAcks.put(key3, dialog1);
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleOrigAckReq(SipMessage sm, String callId) {
        final Dialog dialog = findDialog(sm, callId);
        if (dialog.state == DialogState.REJECTED) {
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        } else {
            dialog.state = DialogState.ESTABLISHED;
            present(callId, "outgoing call established\n");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
    }

    private CastResult handleOrigCanReq(SipMessage sm, String callId) {
        final Dialog d1 = findDialog(sm, callId);
        d1.state = DialogState.CANCELED;
        present(callId, "call canceled by %s\n", sm.getUser("From"));
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleOrigCanRespFin(SipMessage sm, String callId) {
        // don't present anything
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleTermByeRespRegular(SipMessage sm, String callId) {
        final Dialog dialog = findDialog(sm, callId);
        if (dialog == null) {
            present("no dialog found");
            return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
        }
        else {
            present(callId, "call released by %s\n", sm.getUser("From"));
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

    // static void present(String format, String text) {
    //     present(String.format(format, text));
    // }

    void present(String format, int number) {
        present(String.format(format, number));
    }

    void present(String text) {
        if (member(MessageMode.INFO, MESSAGE_MODES)) {
            System.out.println(String.format("[log] %s", text));
        }
    }

    void present(String callId, String format, String text) {
        present(callId, String.format(format, text));
    }

    void present(String callId, String format, String text1, String text2) {
        present(callId, String.format(format, text1, text2));
    }

    void present(String callId, String format, int number) {
        present(callId, String.format(format, number));
    }

    void present(String callId, String text) {
        if (member(MessageMode.INFO, MESSAGE_MODES)) {
            final int callNo = getCallNo(callId);
            System.out.println(String.format("[log] [%d] %s", callNo, text));
        }
    }

    private int getCallNo(String callId) {
        final CallNumber cn = callNumbers.get(callId);
        if (cn == null)  {
            callNumbers.put(callId,
                new CallNumber(getAndIncrCallNo(), System.currentTimeMillis()));
            return getCallNo(callId);
        }
        else {
            return cn.callNo;
        }
    }

    private int getAndIncrCallNo() {
        return numericCallId++;
    }

    private Dialog findDialog(SipMessage sm, String callId) {
        if (sm.getMethod() == Method.ACK) {
            final String key = Util.mergeStrings(callId, sm.getCseqNumberAsString());
            final Dialog dialog = expectedAcks.get(key);
            if (dialog != null) {
                return dialog;
            }
            else {
                return findDialog(sm, callId, sm.getTag("From", null), sm.getTag("To", null));
            }
        }
        else {
            return findDialog(sm, callId, sm.getTag("From", null), sm.getTag("To", null));
        }
    }

    private void debug(String text) {
        if (member(MessageMode.DEBUG, MESSAGE_MODES)) {
            System.out.println(String.format("+++ %s", text));
        }
    }

    private Dialog findDialog(SipMessage sm, String callId, String fromTag, String toTag) {
        if (toTag == null) {
            return findDialog(sm, callId, fromTag);
        }
        else {
            final String key = Util.mergeStrings(callId, fromTag, toTag);
            final Dialog dialog = dialogs.get(key);
            if (dialog != null) {
                return dialog;
            }
            else {
                return findDialog(sm, callId, fromTag);
            }
        }
    }

    private Dialog findDialog(SipMessage sm, String callId, String fromTag) {
        final String key = Util.mergeStrings(callId, fromTag);
        final Dialog dialog = dialogs.get(key);
        if (dialog != null) {
            return dialog;
        }
        else {
            throw new RuntimeException("no dialog found");
        }
    }


    private static boolean member(MessageMode mode, MessageMode[] messageModes) {
        for (MessageMode m : messageModes) {
            if (mode == m) {
                return true;
            }
        }
        return false;
    }
}
