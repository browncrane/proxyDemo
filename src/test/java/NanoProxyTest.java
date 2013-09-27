import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class NanoProxyTest {

    private URL url;
    private Proxy proxy;

    @Before
    public void setUp() throws Exception {
        url = new URL("http://selfsolve.apple.com/wcResults.do");
        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1024));
    }

    @Test
    public void testGetMethod() throws Exception {
        HttpURLConnection directConnection = (HttpURLConnection) url.openConnection();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(directConnection.getInputStream(), "UTF-8"));
        String expected = bufferedReader.readLine().substring(0, 20);

        NanoProxy nanoProxy = new NanoProxy(1024);
        HttpURLConnection proxyConnection = (HttpURLConnection) url.openConnection(proxy);
        BufferedReader in = new BufferedReader(new InputStreamReader(proxyConnection.getInputStream(), "UTF-8"));
        String string = in.readLine().substring(0, 20);

        assertThat(string, is(expected));
        nanoProxy.stop();
    }

    @Test
    public void testResponseHeader() throws Exception {
        HttpURLConnection directConnection = (HttpURLConnection) url.openConnection();
        String expected = directConnection.getHeaderField("Content-Length");

        NanoProxy nanoProxy = new NanoProxy(1024);
        HttpURLConnection proxyConnection = (HttpURLConnection) url.openConnection(proxy);
        String actual = proxyConnection.getHeaderField("Content-Length");

        assertThat(expected, is(actual));
        nanoProxy.stop();
    }

    @Test
    public void testPostMethod() throws Exception {
        HttpURLConnection directConnection = (HttpURLConnection) url.openConnection();
        directConnection.setRequestMethod("POST");
        String urlParameters = "sn=C02G8416DRJM&cn=&locale=&caller=&num=12345";
        directConnection.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(directConnection.getOutputStream());
        wr.writeBytes(urlParameters);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(directConnection.getInputStream()));
        String expected = bufferedReader.readLine();

        NanoProxy nanoProxy = new NanoProxy(1024);
        HttpURLConnection proxyConnection = (HttpURLConnection) url.openConnection(proxy);
        proxyConnection.setRequestMethod("POST");
        proxyConnection.setDoOutput(true);
        DataOutputStream wrForProxy = new DataOutputStream(proxyConnection.getOutputStream());
        wrForProxy.writeBytes(urlParameters);
        BufferedReader in = new BufferedReader(new InputStreamReader(proxyConnection.getInputStream(), "UTF-8"));
        String actual = in.readLine();

        assertThat(actual,is(expected));
        nanoProxy.stop();
    }

    @Test
    public void test_bad_request() throws Exception {
        NanoProxy nanoProxy = new NanoProxy(1024);
        HttpURLConnection missURL = (HttpURLConnection) url.openConnection(proxy);
        missURL.setRequestMethod("GET");
        missURL.getInputStream();

        nanoProxy.stop();
    }
}
