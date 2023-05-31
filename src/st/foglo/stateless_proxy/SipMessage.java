package st.foglo.stateless_proxy;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;


public class SipMessage implements Cloneable {
    
    final Type type;

    boolean isBlacklisted = false;

    /**
     * A string; no CR LF at the end
     */
    final String firstLine;
    
    /**
     * Keys in this map is canonical header names like Call-ID, Via, etc
     * 
     * Values are lists of as-received strings, no CR LF at end
     */
    private Map<String, LinkedList<String>> headers =
        new HashMap<String, LinkedList<String>>();

    // public Map<String, LinkedList<String>> getHeaders() {
    //     return headers;
    // }
    
    /**
     * One long string, with embedded CR LF sequences
     */
    final String body;
    
    enum Type {
        REQUEST,
        RESPONSE
    }

    public enum Method {
        REGISTER,
        INVITE,
        ACK,
        BYE,
        CANCEL,
        SUBSCRIBE,
        NOTIFY
    }
    
    enum State {
        SCAN_FIRST_LINE,
        SCAN_FIRST_LINE_CR_SEEN,
        SCAN_HEADERS,
        SCAN_HEADERS_CR_SEEN,
        SCAN_BODY}
    
    
    public SipMessage(byte[] buffer, int size) {
        
        String firstLine = null;
        State state = State.SCAN_FIRST_LINE;
        StringBuilder sb = new StringBuilder();
        byte b;
        for (int k = 0; k < size; k++) {
            b = buffer[k];
            
            if (state == State.SCAN_FIRST_LINE || state == State.SCAN_FIRST_LINE_CR_SEEN) {
                if (b == 13) {
                    state = State.SCAN_FIRST_LINE_CR_SEEN;
                }
                else if (b == 10 && state == State.SCAN_FIRST_LINE_CR_SEEN) {
                    firstLine = new String(sb);
                    state = State.SCAN_HEADERS;
                    sb = new StringBuilder();
                }
                else {
                    sb.append((char)b);
                }
            }
            else if (state == State.SCAN_HEADERS || state == State.SCAN_HEADERS_CR_SEEN) {
                if (b == 13) {
                    state = State.SCAN_HEADERS_CR_SEEN;
                }
                else if (b == 10 && state == State.SCAN_HEADERS_CR_SEEN) {
                    String headerLine = new String(sb);
                    sb = new StringBuilder();
                    
                    if (headerLine.isEmpty()) {
                        state = State.SCAN_BODY;
                    }
                    else if (!headerLine.isEmpty()) {
                        collectHeaderLine(headerLine);
                        state = State.SCAN_HEADERS;
                    }
                }
                else {
                    sb.append((char)b);
                }
            }
            else if (state == State.SCAN_BODY) {
                sb.append((char)b);
            }
        }
        
        if (state == State.SCAN_BODY) {
            this.body = new String(sb);
        }
        else if (state == State.SCAN_HEADERS) {
            this.body = null;
        }
        else {
            // internal error
            this.body = null;
        }

        this.firstLine = firstLine;
        
        this.type = firstLine.startsWith("SIP/2.0") ? Type.RESPONSE : Type.REQUEST;

    }

    //////////////////////////

    public Object clone() throws CloneNotSupportedException {
        final Object result = (SipMessage) super.clone();
        ((SipMessage) result).headers = copyMap(((SipMessage) result).headers);
        return result;
    }

    private static Map<String, LinkedList<String>> copyMap(Map<String, LinkedList<String>> map) {
        Map<String, LinkedList<String>> result = new HashMap<String,LinkedList<String>>();
        for (Entry<String, LinkedList<String>> e : map.entrySet()) {
            result.put(e.getKey(), copyList(e.getValue()));
        }
        return result;
    }

    private static LinkedList<String> copyList(LinkedList<String> list) {
        LinkedList<String> result = new LinkedList<String>();
        for (String s : list) {
            result.add(s);
        }
        return result;
    }

    
    ////////////////////////
    
    private void collectHeaderLine(String headerLine) {
        
        int indexColon = headerLine.indexOf(':');
        String key = headerLine.substring(0, indexColon);
        
        if (headers.get(key) == null) {
            LinkedList<String> hh = new LinkedList<String>();
            hh.add(headerLine.substring(1+indexColon).trim());
            headers.put(key, hh);
            //Util.trace(Level.verbose, "+++ %s -> %s", key, headerLine);
        }
        else {
            LinkedList<String> hh = headers.get(key);
            hh.add(headerLine.substring(1+indexColon).trim());
            //Util.trace(Level.verbose, "+++ %s -> %s", key, headerLine);
        }
    }

    public String toString() {
        final byte[] crLf = new byte[]{13, 10};
        final StringBuilder sb = new StringBuilder();
        sb.append(firstLine);
        sb.append(Character.valueOf((char) crLf[1]));
        
        for (Entry<String, LinkedList<String>> me : headers.entrySet()) {
            final LinkedList<String> hh = me.getValue();
            final String key = me.getKey();
            for (String s : hh) {
                sb.append(key);
                sb.append(": ");
                sb.append(s);
                sb.append(Character.valueOf((char) crLf[1]));
            }
        }
        if (body != null) {
            sb.append(Character.valueOf((char) crLf[1]));
            sb.append(body);
        }
        return new String(sb);
    }

    public String getUser(String key) {

        final String toHeader = getTopHeaderField(key);
        
        final int posSip = toHeader.indexOf("sip:");
        final int posAtSign = toHeader.indexOf('@');
        
        return toHeader.substring(posSip+4, posAtSign);
    }
    
    public String firstLineNoVersion() {
        final String[] parts = firstLine.split(" ");
        final StringBuilder sb = new StringBuilder();
        for (String u : parts) {
            if (!u.equals("SIP/2.0")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(u);
            }
        }
        return sb.toString();
    }

    public String responseLabel() {
        final int code = getCode();
        final Method method = getMethod();
        final String[] parts = firstLine.split(" ");
        if (parts.length == 2) {
            return String.format("%d %s", code, method.toString());
        }
        else {
            final StringBuilder sb = new StringBuilder();
            for (int j = 2; j < parts.length; j++) {
                sb.append(" ");
                sb.append(parts[j]);
            }
            return String.format("%d %s%s", code, method.toString(), sb.toString());
        }
    }

    public LinkedList<String> getHeaderFields(String key) {
        final LinkedList<String> toHeaderFields = headers.get(key);
        if (toHeaderFields == null) {
            return new LinkedList<String>();
        }
        else {
            return toHeaderFields;
        }
    }


    /**
     * 
     * @param key A key such as "Route"
     * @return The topmost header field value
     */
    public String getTopHeaderField(String key) {
        final LinkedList<String> fields = getHeaderFields(key);
        if (fields.isEmpty()) {
            return null;
        }
        else {
            return fields.peek();
        }
    }

    public Method getMethod() {
        if (type == Type.REQUEST) {
            final int firstSpacePos = firstLine.indexOf(' ');
            final String firstWord = firstLine.substring(0, firstSpacePos);
            for (Method m : Method.values()) {
                if (m.toString().equals(firstWord)) {
                    return m;
                }
            }
            throw new RuntimeException();
        }
        else if (type == Type.RESPONSE) {
            final String[] words = getCseq();
            final String word = words[words.length - 1];
            for (Method m : Method.values()) {
                if (m.toString().equals(word)) {
                    return m;
                }
            }
            throw new RuntimeException();
        }
        else {
            throw new RuntimeException();
        }
    }

    public int getCseqNumber() {
        return Integer.parseInt(getCseqNumberAsString());
    }

    public String getCseqNumberAsString() {
        final String[] cseq = getCseq();
        return cseq[0];
    }

    private String[] getCseq() {
        final LinkedList<String> hh = getHeaderFields("CSeq");
        return hh.peek().split(" ");
    }

    public int getCode() {
        // applicable to responses only
        // SIP/2.0 401 Unauthorized
        String[] parts = firstLine.split(" ");
        return Integer.parseInt(parts[1]);
    }

    

    public String getTag(String key) {
        final LinkedList<String> headerFields = headers.get(key);
        if (headerFields == null || headerFields.size() != 1) {
            throw new RuntimeException();
        }
        else {
            final String[] parts = headerFields.peek().split(";");
            for (String part : parts) {
                if (part.startsWith("tag=")) {
                    return part.substring(4);
                }
            }
            throw new RuntimeException(String.format("missing %s-tag", key));
        }
    }


    
    public String getTag(String key, String defaultValue) {
        final LinkedList<String> headerFields = headers.get(key);
        if (headerFields == null || headerFields.size() != 1) {
            throw new RuntimeException();
        }
        else {
            final String[] parts = headerFields.peek().split(";");
            for (String part : parts) {
                if (part.startsWith("tag=")) {
                    return part.substring(4);
                }
            }
            return defaultValue;
        }
    }

    /**
     * Returns true if this request has an Expires: 0 header,
     * or if there is a single Contact hedaer with expires=0
     * @return
     */
    public boolean isDeRegister() {
        final LinkedList<String> expiresHeaders = headers.get("Expires");
        if (expiresHeaders != null && expiresHeaders.size() == 1) {
            if (expiresHeaders.peek().equals("0")) {
                return true;
            }
            return false;
        }
        else {
            final LinkedList<String> contactHeaders = headers.get("Contact");
            if (contactHeaders != null && contactHeaders.size() == 1) {
                final String[] parts = contactHeaders.peek().split(";");
                if (isElement("expires=0", parts)) {
                    return true;
                }
            }
            return false;
        }
    }

    public String getTopViaBranch() {
        final String topVia = getTopHeaderField("Via");
        for (String u : topVia.split(";")) {
            if (u.startsWith("branch=")) {
                return u.substring(u.indexOf('=')+1);
            }
        }
        return "BRANCH_PARAMETER_NOT_FOUND";
    }
 
    public boolean isElement(String x, String[] a) {
        for (String u : a) {
            if (u.equals(x)) {
                return true;
            }
        }
        return false;
    }

    public void prepend(String key, String headerFieldValue) {
        LinkedList<String> headerFields = getHeaderFields(key);
        headerFields.addFirst(headerFieldValue);
    }

    

    public void dropFirst(String key) {
        final LinkedList<String> hh = getHeaderFields(key);
        hh.poll();
        if (hh.isEmpty()) {
            headers.remove(key);
        }
        else {
            setHeaderFields(key, hh);
        }
    }

    /**
     * @param key
     * @param headerFields
     */
    public void setHeaderFields(String key, LinkedList<String> headerFields) {
        headers.put(key, headerFields);
    }

    public void setHeaderField(String key, String headerField) {
        final LinkedList<String> headerFields = new LinkedList<String>();
        headerFields.add(headerField);
        setHeaderFields(key, headerFields);
    }


    public byte[] toByteArray() {
        
        byte[] ba = new byte[10000];   // TOXDO, ugly
        int k = 0;
        
        for (byte b : firstLine.getBytes()) {
            ba[k++] = b;
        }
        ba[k++] = 13;
        ba[k++] = 10;

        for (Map.Entry<String, LinkedList<String>> e : headers.entrySet()) {
            final String key = e.getKey();
            for (String h : e.getValue()) {
                for (byte b : key.getBytes()) {
                    ba[k++] = b;
                }
                ba[k++] = ':';
                ba[k++] = ' ';
                for (byte b : h.getBytes()) {
                    ba[k++] = b;
                }
                ba[k++] = 13;
                ba[k++] = 10;
            }
        }

        if (body != null) {
            ba[k++] = 13;
            ba[k++] = 10;
            
            for (byte b : body.getBytes()) {
                ba[k++] = b;
            }
        }
        
        byte[] result = new byte[k];
        
        for (int j = 0; j < k; j++) {
            result[j] = ba[j];
        }
        
        return result;
    }

    public boolean isSuccess() {
        final int code = getCode();
        return code >= 200 && code < 300;
    }

    public boolean isProvisional() {
        final int code = getCode();
        return code > 100 && code < 200;
    }

    public boolean isFinal() {
        final int code = getCode();
        return code >= 200 && code < 300;
    }

    public boolean isBusyHere() {
        final int code = getCode();
        return code == 486;
    }

    public boolean isClientFailure() {
        final int code = getCode();
        return code >= 400 && code < 500;
    }

    public boolean isBlacklisted() {
        return isBlacklisted;
    }
}
