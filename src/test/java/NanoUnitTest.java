import com.github.dreamhead.moco.*;
import com.github.dreamhead.moco.Runnable;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.*;

import static com.github.dreamhead.moco.Moco.*;
import static com.github.dreamhead.moco.Runner.running;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class NanoUnitTest {
    private Proxy proxy;
    private URL url;

    @Before
    public void setUp() throws Exception {
        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1024));
        url = new URL("http://localhost:12306");
    }

    @Test
    public void should_response_as_expected() throws Exception {
        HttpServer server = httpserver(12306);
        server.response("foo");

        running(server, new com.github.dreamhead.moco.Runnable() {
            @Override
            public void run() throws Exception {
                HttpURLConnection urlConnection = (HttpURLConnection) new URL("http://localhost:12306").openConnection();
                int responseCode = urlConnection.getResponseCode();
                assertThat(responseCode, is(200));
            }
        });
    }

    @Test
    public void should_be_response_to_get_request() throws Exception {
        HttpServer server = httpserver(12306);
        server.request(by(method("GET"))).response("get");

        running(server, new Runnable() {
            @Override
            public void run() throws Exception {
                HttpURLConnection directConnection = (HttpURLConnection) url.openConnection();
                directConnection.setRequestMethod("GET");
                String responseForDirectConnection = new BufferedReader(new InputStreamReader(directConnection.getInputStream())).readLine();

                NanoHTTPD nanoHTTPD = new NanoHTTPD(1024);
                HttpURLConnection proxyConnection = (HttpURLConnection) url.openConnection(proxy);
                proxyConnection.setRequestMethod("GET");
                String expected = new BufferedReader(new InputStreamReader(proxyConnection.getInputStream(), "UTF-8")).readLine();
                assertThat(responseForDirectConnection,is(expected));
                nanoHTTPD.stop();
            }
        });
    }

    @Test
    public void should_get_response_from_post_method() throws Exception {
        HttpServer server = httpserver(12306);
        server.post(by("postParameter")).response("postOK");

        running(server, new Runnable() {
            @Override
            public void run() throws Exception {
                HttpURLConnection directConnection = (HttpURLConnection) url.openConnection();
                directConnection.setRequestMethod("POST");
                String urlParameters = "postParameter";
                directConnection.setDoOutput(true);
                DataOutputStream wr = new DataOutputStream(directConnection.getOutputStream());
                wr.writeBytes(urlParameters);
                String expected = new BufferedReader(new InputStreamReader(directConnection.getInputStream())).readLine();

                NanoHTTPD nanoHTTPD = new NanoHTTPD(1024);
                HttpURLConnection proxyConnection = (HttpURLConnection) url.openConnection(proxy);
                proxyConnection.setRequestMethod("POST");
                proxyConnection.setDoOutput(true);
                DataOutputStream wrForProxy = new DataOutputStream(proxyConnection.getOutputStream());
                wrForProxy.writeBytes(urlParameters);
                String actual = new BufferedReader(new InputStreamReader(proxyConnection.getInputStream())).readLine();

                assertThat(expected,is(actual));
                nanoHTTPD.stop();
            }
        });
    }
}
