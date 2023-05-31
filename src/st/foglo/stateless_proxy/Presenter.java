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

    private static final long RECLAIM_REG = 3600;     // TODO: How long really?
    private static final long RECLAIM_SIG = 900;      // longer than the longest call call
    private static final long RECLAIM_BLOCKING = 35;  // Longer than the UDP resend period

    private enum MessageMode {
        DEBUG,
        INFO,
        WARNING,
        RECLAIM
    }

    private static final MessageMode[] MESSAGE_MODES =
        new MessageMode[]{
            MessageMode.WARNING,
            MessageMode.INFO,
            MessageMode.RECLAIM
            //MessageMode.DEBUG
        };

    /**
     * REGISTER transactions; key is: From-tag of REGISTER request
     */
    private Map<String, RegTrans> regTrans = new HashMap<String, RegTrans>();

    /**
     * Maps registered user to latest message text; used for suppressing
     * repeated messages. No memory leakage since very few keys occur.
     */
    private Map<String, MessageText> previousRegMessage = new HashMap<String, MessageText>();

    /**
     * Maps blocked from+to to timestamp
     */
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
        long timestamp;

        public Dialog(DialogState state) {
            this.state = state;
            timestamp = System.currentTimeMillis();
        }

        // public Dialog(DialogState state, String newKey) {
        //     this.state = state;
        //     this.callNo = -1;
        //     this.newKey = newKey;
        // }
    }

    class CallNumber {
        final int callNo;
        final long timestamp;
        public CallNumber(int callNo, long timestamp) {
            this.callNo = callNo;
            this.timestamp = timestamp;
        }
    }

    class RegTrans {
        final boolean registration;
        final long timestamp;
        public RegTrans(boolean registration, long timestamp) {
            this.registration = registration;
            this.timestamp = timestamp;
        }
    }

    class MessageText {
        final String text;
        final long timestamp;
        public MessageText(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
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
            return handleRegReq(callId, sm);
        }
        else if (method == Method.REGISTER && type == Type.RESPONSE && sm.isSuccess()) {
            debug("method == Method.REGISTER && type == Type.RESPONSE && sm.isSuccess()");
            return handleRegResp(callId, sm, toUser);
        }
        else if (method == Method.NOTIFY && type == Type.REQUEST && ism.side == Side.SP) {
            debug("method == Method.NOTIFY && type == Type.REQUEST && ism.side == Side.SP");
            return handleTermNotifyReq(sm);
        }
        else if (method == Method.INVITE && type == Type.REQUEST && ism.side == Side.SP && sm.isBlacklisted) {
            debug("method == Method.INVITE && type == Type.REQUEST && ism.side == Side.SP && sm.isBlacklisted");
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

    private CastResult handleRegReq(String callId, SipMessage sm) {
        regTrans.put(
                Util.mergeStrings(callId, sm.getTag("From")),
                new RegTrans(!sm.isDeRegister(), System.currentTimeMillis()));
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
    }

    private CastResult handleRegResp(String callId, SipMessage sm, String toUser) {
        // handle a successful REGISTER response (registration or de-registration)
        final String key = Util.mergeStrings(callId, sm.getTag("From"));
        final RegTrans rt = regTrans.get(key);
        final String text = String.format("%s %s", toUser, rt.registration ? "registered\n" : "de-registered\n");
        // suppress repeated printouts
        final MessageText previousMessageText = previousRegMessage.get(toUser);
        if (!(previousMessageText != null && previousMessageText.text.equals(text))) {
            present(text);
        }
        reclaimRegistration();
        previousRegMessage.put(toUser, new MessageText(text, System.currentTimeMillis()));
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
        final String key = Util.mergeStrings(fromUser, toUser);
        final Long millis = blockingMessage.get(key);
        if (millis == null) {
            present(callId, "blocking incoming call from %s to %s", fromUser, toUser);
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
            dialog.timestamp = System.currentTimeMillis();  // update
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
            dialog.timestamp = System.currentTimeMillis();   // update
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
        }
        else {
            final int seconds = (int) ((System.currentTimeMillis() - dialog.timestamp)/1000);
            present(callId, "call released by %s after %s\n", sm.getUser("From"), toHourMinSec(seconds));
        }
        reclaimSignaling();
        return new CastResult(Atom.NOREPLY, TIMEOUT_NEVER);
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

    private void reclaimMessage(String text) {
        if (member(MessageMode.RECLAIM, MESSAGE_MODES)) {
            System.out.println(String.format("<<< %s", text));
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

    
    private String toHourMinSec(int seconds) {
        final StringBuilder sb = new StringBuilder();
        final int h = seconds/3600;
        final int m = (seconds % 3600)/60;
        final int s = seconds % 60;
        if (h > 0) {
            sb.append(h);
            sb.append("h ");
        }
        if (m > 0 || h > 0) {
            sb.append(m);
            sb.append("m ");
        }
        sb.append(s);
        sb.append("s");
        return sb.toString();
    }

    private void reclaimSignaling() {

        final long now = System.currentTimeMillis();

        final LinkedList<String> keys = new LinkedList<String>();

        for (Map.Entry<String, Long> me : blockingMessage.entrySet()) {
            if (now - me.getValue().longValue() > 1000*RECLAIM_BLOCKING) {
                keys.add(me.getKey());
            }
        }
        for (String u : keys) {
            reclaimMessage("reclaim blocking");
            blockingMessage.remove(u);
        }


        keys.clear();
        for (Map.Entry<String, Dialog> me : dialogs.entrySet()) {
            if (now - me.getValue().timestamp > 1000*RECLAIM_SIG) {
                keys.add(me.getKey());
            }
        }
        for (String u : keys) {
            reclaimMessage("reclaim dialogs");
            dialogs.remove(u);
        }

        keys.clear();
        for (Map.Entry<String, Dialog> me : expectedAcks.entrySet()) {
            if (now - me.getValue().timestamp > 1000*RECLAIM_SIG) {
                keys.add(me.getKey());
            }
        }
        for (String u : keys) {
            reclaimMessage("reclaim expected ACKs");
            expectedAcks.remove(u);
        }
    
        keys.clear();
        for (Map.Entry<String, Long> me : ignoredCalls.entrySet()) {
            if (now - me.getValue().longValue() > 1000*RECLAIM_SIG) {
                keys.add(me.getKey());
            }
        }
        for (String u : keys) {
            reclaimMessage("reclaim ignoredCalls");
            ignoredCalls.remove(u);
        }

        keys.clear();
        for (Map.Entry<String, CallNumber> me : callNumbers.entrySet()) {
            if (now - me.getValue().timestamp > 1000*RECLAIM_SIG) {
                keys.add(me.getKey());
            }
        }
        for (String u : keys) {
            reclaimMessage("reclaim call numbers");
            callNumbers.remove(u);
        }
    }

    private void reclaimRegistration() {

        final long now = System.currentTimeMillis();

        final LinkedList<String> keys = new LinkedList<String>();

        for (Map.Entry<String, RegTrans> e : regTrans.entrySet()) {
            if (now - e.getValue().timestamp > 1000*RECLAIM_REG) {
                keys.add(e.getKey());
            }
        }
        for (String key : keys) {
            regTrans.remove(key);
        }

        keys.clear();
        for (Map.Entry<String, MessageText> e : previousRegMessage.entrySet()) {
            if (now - e.getValue().timestamp > 1000*RECLAIM_REG) {
                keys.add(e.getKey());
            }
        }
        for (String key : keys) {
            previousRegMessage.remove(key);
        }
    }
}
