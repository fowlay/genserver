# genserver

This project is a Java implementation of the Erlang/OTP gen_server
pattern. To explore its usefulness, a stateless SIP proxy server is
implemented. The proxy server runs 3 concurrent genserver processes
that handle UDP sending and listening, and the SIP proxy logic.
