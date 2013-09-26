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

public class NanoHTTPDTest {

    private URL url;

    @Before
    public void setUp() throws Exception {
        final String someWebsite = "http://selfsolve.apple.com/wcResults.do";
        url = new URL(someWebsite);
    }

    @Test
    public void testGetMethod() throws Exception {
        // given
        HttpURLConnection directConnection = (HttpURLConnection) url.openConnection();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(directConnection.getInputStream(), "UTF-8"));
        String expected = bufferedReader.readLine().substring(0, 20);

        NanoHTTPD nanoHTTPD = new NanoHTTPD(1024);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1024));
        HttpURLConnection proxyConnection = (HttpURLConnection) url.openConnection(proxy);
        BufferedReader in = new BufferedReader(new InputStreamReader(proxyConnection.getInputStream(), "UTF-8"));
        String string = in.readLine().substring(0, 20);

        // then
        assertThat(string, is(expected));
        nanoHTTPD.stop();
    }

    @Test
    public void testPostMethod() throws Exception {
        //given
        HttpURLConnection directConnection = (HttpURLConnection) url.openConnection();

        directConnection.setRequestMethod("POST");
        String urlParameters = "sn=C02G8416DRJM&cn=&locale=&caller=&num=12345";

        directConnection.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(directConnection.getOutputStream());
        wr.writeBytes(urlParameters);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(directConnection.getInputStream()));
        String expected = bufferedReader.readLine();

        NanoHTTPD nanoHTTPD = new NanoHTTPD(1024);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1024));
        HttpURLConnection proxyConnection = (HttpURLConnection) url.openConnection(proxy);
        proxyConnection.setRequestMethod("POST");

        proxyConnection.setDoOutput(true);
        DataOutputStream wrForProxy = new DataOutputStream(proxyConnection.getOutputStream());
        wrForProxy.writeBytes(urlParameters);

        BufferedReader in = new BufferedReader(new InputStreamReader(proxyConnection.getInputStream(), "UTF-8"));
        String string = in.readLine();

        //then
        assertThat(string,is(expected));
        nanoHTTPD.stop();
    }

}
