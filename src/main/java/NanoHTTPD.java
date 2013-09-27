
import java.io.*;
import java.util.*;
import java.net.*;

/**
 * A simple, tiny, nicely embeddable HTTP 1.0 server in Java
 * <p/>
 * <p> NanoHTTPD version 1.11,
 * Copyright &copy; 2001,2005-2008 Jarno Elonen (elonen@iki.fi, http://iki.fi/elonen/)
 * <p/>
 * <p><b>Features + limitations: </b><ul>
 * <p/>
 * <li> Only one Java file </li>
 * <li> Java 1.1 compatible </li>
 * <li> Released as open source, Modified BSD licence </li>
 * <li> No fixed config files, logging, authorization etc. (Implement yourself if you need them.) </li>
 * <li> Supports parameter parsing of GET and POST methods </li>
 * <li> Supports both dynamic content and file serving </li>
 * <li> Never caches anything </li>
 * <li> Doesn't limit bandwidth, request time or simultaneous connections </li>
 * <li> Default code serves files and shows all HTTP parameters and headers</li>
 * <li> File server supports directory listing, index.html and index.htm </li>
 * <li> File server does the 301 redirection trick for directories without '/'</li>
 * <li> File server supports simple skipping for files (continue download) </li>
 * <li> File server uses current directory as a web root </li>
 * <li> File server serves also very long files without memory overhead </li>
 * <li> Contains a built-in list of most common mime types </li>
 * <li> All header names are converted lowercase so they don't vary between browsers/clients </li>
 * <p/>
 * </ul>
 * <p/>
 * <p><b>Ways to use: </b><ul>
 * <p/>
 * <li> Run as a standalone app, serves files from current directory and shows requests</li>
 * <li> Subclass serve() and embed to your own program </li>
 * <li> Call serveFile() from serve() with your own base directory </li>
 * <p/>
 * </ul>
 * <p/>
 * See the end of the source file for distribution license
 * (Modified BSD licence)
 */
public class NanoHTTPD {
    private final ServerSocket serverSocket;

    // ==================================================
    // API parts
    // ==================================================

    /**
     * HTTP response.
     * Return one of these from serve().
     */
    public class Response {

        /**
         * Basic constructor.
         */
        public Response(String status, String mimeType, InputStream data) {
            this.status = status;
            this.mimeType = mimeType;
            this.data = data;
        }

        /**
         * HTTP status code after processing, e.g. "200 OK", HTTP_OK
         */
        public String status;

        /**
         * MIME type of content, e.g. "text/html"
         */
        public String mimeType;

        /**
         * Data of the response, may be null.
         */
        public InputStream data;

        /**
         * Headers for the HTTP response. Use addHeader()
         * to add lines.
         */
        public Properties header = new Properties();

        public Response() {

        }
    }

    public static final String HTTP_BADREQUEST = "400 Bad Request";
    public static final String HTTP_INTERNALERROR = "500 Internal Server Error";

    /**
     * Common mime types for dynamic content
     */
    public static final String MIME_PLAINTEXT = "text/plain";
    public static final String MIME_HTML = "text/html";

    // ==================================================
    // Socket & server code
    // ==================================================

    /**
     * Starts a HTTP server to given port.<p>
     * Throws an IOException if the socket is already in use
     */
    public NanoHTTPD(int port) throws IOException {
        myTcpPort = port;
        runThread = true;
        serverSocket = new ServerSocket(myTcpPort);

        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    while (runThread) {
                        new HTTPSession(serverSocket.accept());
                    }
                } catch (IOException ioe) {
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private boolean runThread;

    public void stop() throws IOException {
        this.runThread = false;
        serverSocket.close();
    }

    /**
     * Starts as a standalone file server and waits for Enter.
     */
    public static void main(String[] args) {
        System.out.println("NanoHTTPD 1.11 (C) 2001,2005-2008 Jarno Elonen\n" +
                "(Command line options: [port] [--licence])\n");

        // Show licence if requested
        int lopt = -1;
        for (int i = 0; i < args.length; ++i)
            if (args[i].toLowerCase().endsWith("licence")) {
                lopt = i;
                System.out.println(LICENCE + "\n");
            }

        // Change port if requested
        int port = 1024;
        if (args.length > 0 && lopt != 0)
            port = Integer.parseInt(args[0]);

        if (args.length > 1 &&
                args[1].toLowerCase().endsWith("licence"))
            System.out.println(LICENCE + "\n");

        NanoHTTPD nh = null;
        try {
            nh = new NanoHTTPD(port);
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
            System.exit(-1);
        }
        nh.myFileDir = new File("");

        System.out.println("Now serving files in port " + port + " from \"" +
                new File("").getAbsolutePath() + "\"");
        System.out.println("Hit Enter to stop.\n");

        try {
            System.in.read();
        } catch (Throwable t) {
        }
        ;
    }

    /**
     * Handles one session, i.e. parses the HTTP request
     * and returns the response.
     */
    private class HTTPSession implements Runnable {

        public HTTPSession(Socket s) {
            mySocket = s;

            InetAddress localhost;
            try {
                localhost = InetAddress.getByName("127.0.0.1");
            } catch (Exception e) {
                localhost = null;
            }
            if (s.getInetAddress().equals(localhost)) {
                Thread t = new Thread(this);
                t.setDaemon(true);
                t.start();
            } else {
                try {
                    sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: Invalid ip.");
                } catch (Throwable t) {
                }
            }
        }

        public void run() {
            try {
                InputStream is = mySocket.getInputStream();
                if (is == null) return;
                BufferedReader in = new BufferedReader(new InputStreamReader(is));

                // Read the request line
                StringTokenizer st = new StringTokenizer(in.readLine());
                if (!st.hasMoreTokens())
                    sendError(HTTP_BADREQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");

                String method = st.nextToken();

                if (!st.hasMoreTokens())
                    sendError(HTTP_BADREQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");

                String uri = st.nextToken();

                // Decode parameters from the URI
                Properties parms = new Properties();
                int qmi = uri.indexOf('?');
                if (qmi >= 0) {
                    decodeParms(uri.substring(qmi + 1), parms);
                    uri = decodePercent(uri.substring(0, qmi));
                } else uri = decodePercent(uri);


                // If there's another token, it's protocol version,
                // followed by HTTP headers. Ignore version but parse headers.
                // NOTE: this now forces header names uppercase since they are
                // case insensitive and vary by client.
                Properties header = new Properties();
                if (st.hasMoreTokens()) {
                    String line = in.readLine();
                    while (line.trim().length() > 0) {
                        int p = line.indexOf(':');
                        header.put(line.substring(0, p).trim().toLowerCase(), line.substring(p + 1).trim());
                        line = in.readLine();
                    }
                }

                // If the method is POST, there may be parameters
                // in data section, too, read it:
                Response r = new Response();
                if (method.equalsIgnoreCase("POST")) {
                    long size = 0x7FFFFFFFFFFFFFFFl;
                    String contentLength = header.getProperty("content-length");
                    if (contentLength != null) {
                        try {
                            size = Integer.parseInt(contentLength);
                        } catch (NumberFormatException ex) {
                        }
                    }
                    String postLine = "";
                    char buf[] = new char[512];
                    int read = in.read(buf);
                    while (read >= 0 && size > 0 && !postLine.endsWith("\r\n")) {
                        size -= read;
                        postLine += String.valueOf(buf, 0, read);
                        if (size > 0)
                            read = in.read(buf);
                    }
                    postLine = postLine.trim();
                    decodeParms(postLine, parms);
                    r = serveWeb(uri, method, postLine);
                }
                if (method.equalsIgnoreCase("GET")) {
                    r = serveWeb(uri, method);
                }

                if (r == null)
                    sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
                else
                    sendResponse(r.status, r.mimeType, r.header, r.data);

                in.close();
            } catch (IOException ioe) {
                try {
                    sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
                } catch (Throwable t) {
                }
            } catch (InterruptedException ie) {
                // Thrown by sendError, ignore and exit the thread.
            }
        }

        /**
         * Decodes the percent encoding scheme. <br/>
         * For example: "an+example%20string" -> "an example string"
         */
        private String decodePercent(String str) throws InterruptedException {
            try {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < str.length(); i++) {
                    char c = str.charAt(i);
                    switch (c) {
                        case '+':
                            sb.append(' ');
                            break;
                        case '%':
                            sb.append((char) Integer.parseInt(str.substring(i + 1, i + 3), 16));
                            i += 2;
                            break;
                        default:
                            sb.append(c);
                            break;
                    }
                }
                return new String(sb.toString().getBytes());
            } catch (Exception e) {
                sendError(HTTP_BADREQUEST, "BAD REQUEST: Bad percent-encoding.");
                return null;
            }
        }

        /**
         * Decodes parameters in percent-encoded URI-format
         * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" ) and
         * adds them to given Properties.
         */
        private void decodeParms(String parms, Properties p)
                throws InterruptedException {
            if (parms == null)
                return;

            StringTokenizer st = new StringTokenizer(parms, "&");
            while (st.hasMoreTokens()) {
                String e = st.nextToken();
                int sep = e.indexOf('=');
                if (sep >= 0)
                    p.put(decodePercent(e.substring(0, sep)).trim(),
                            decodePercent(e.substring(sep + 1)));
            }
        }

        /**
         * Returns an error message as a HTTP response and
         * throws InterruptedException to stop furhter request processing.
         */
        private void sendError(String status, String msg) throws InterruptedException {
            sendResponse(status, MIME_PLAINTEXT, null, new ByteArrayInputStream(msg.getBytes()));
            throw new InterruptedException();
        }

        /**
         * Sends given response to the socket.
         */
        private void sendResponse(String status, String mime, Properties header, InputStream data) {
            try {
                if (status == null)
                    throw new Error("sendResponse(): Status can't be null.");

                OutputStream out = mySocket.getOutputStream();
                PrintWriter pw = new PrintWriter(out);
                pw.print("HTTP/1.0 " + status + " \r\n");

                if (mime != null)
                    pw.print("Content-Type: " + mime + "\r\n");

                if (header == null || header.getProperty("Date") == null)
                    pw.print("Date: " + gmtFrmt.format(new Date()) + "\r\n");

                if (header != null) {
                    Enumeration e = header.keys();
                    while (e.hasMoreElements()) {
                        String key = (String) e.nextElement();
                        String value = header.getProperty(key);
                        pw.print(key + ": " + value + "\r\n");
                    }
                }

                pw.print("\r\n");
                pw.flush();

                if (data != null) {
                    byte[] buff = new byte[2048];
                    while (true) {
                        int read = data.read(buff, 0, 2048);
                        if (read <= 0)
                            break;
                        out.write(buff, 0, read);
                    }
                }
                out.flush();
                out.close();
                if (data != null)
                    data.close();
            } catch (IOException ioe) {
                // Couldn't write? No can do.
                try {
                    mySocket.close();
                } catch (Throwable t) {
                }
            }
        }

        private Socket mySocket;
    }

    private Response serveWeb(String uri, String method, String postLine) throws IOException {
        URL url = new URL(uri);
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod(method);
        httpURLConnection.setDoOutput(true);
        DataOutputStream wrForProxy = new DataOutputStream(httpURLConnection.getOutputStream());
        wrForProxy.writeBytes(postLine);

        String status = httpURLConnection.getResponseMessage();
        String mimeType = MIME_HTML;
        InputStream inputStream = httpURLConnection.getInputStream();
        return new Response(status, mimeType, inputStream);
    }


    private int myTcpPort;
    File myFileDir;

    public Response serveWeb(String uri, String method) throws IOException {
        URL url = new URL(uri);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod(method);
        String status = urlConnection.getResponseMessage();
        InputStream inputStream = urlConnection.getInputStream();

        String mimeType = MIME_HTML;
        return new Response(status, mimeType, inputStream);
    }

    /**
     * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
     */
    private static Hashtable theMimeTypes = new Hashtable();

    static {
        StringTokenizer st = new StringTokenizer(
                "htm		text/html " +
                        "html		text/html " +
                        "txt		text/plain " +
                        "asc		text/plain " +
                        "gif		image/gif " +
                        "jpg		image/jpeg " +
                        "jpeg		image/jpeg " +
                        "png		image/png " +
                        "mp3		audio/mpeg " +
                        "m3u		audio/mpeg-url " +
                        "pdf		application/pdf " +
                        "doc		application/msword " +
                        "ogg		application/x-ogg " +
                        "zip		application/octet-stream " +
                        "exe		application/octet-stream " +
                        "class		application/octet-stream ");
        while (st.hasMoreTokens())
            theMimeTypes.put(st.nextToken(), st.nextToken());
    }

    /**
     * GMT date formatter
     */
    private static java.text.SimpleDateFormat gmtFrmt;

    static {
        gmtFrmt = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * The distribution licence
     */
    private static final String LICENCE =
            "Copyright (C) 2001,2005-2008 by Jarno Elonen <elonen@iki.fi>\n" +
                    "\n" +
                    "Redistribution and use in source and binary forms, with or without\n" +
                    "modification, are permitted provided that the following conditions\n" +
                    "are met:\n" +
                    "\n" +
                    "Redistributions of source code must retain the above copyright notice,\n" +
                    "this list of conditions and the following disclaimer. Redistributions in\n" +
                    "binary form must reproduce the above copyright notice, this list of\n" +
                    "conditions and the following disclaimer in the documentation and/or other\n" +
                    "materials provided with the distribution. The name of the author may not\n" +
                    "be used to endorse or promote products derived from this software without\n" +
                    "specific prior written permission. \n" +
                    " \n" +
                    "THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR\n" +
                    "IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES\n" +
                    "OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.\n" +
                    "IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,\n" +
                    "INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT\n" +
                    "NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n" +
                    "DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY\n" +
                    "THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n" +
                    "(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE\n" +
                    "OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";
}
