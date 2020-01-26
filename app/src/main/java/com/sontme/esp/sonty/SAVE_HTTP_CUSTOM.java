package com.sontme.esp.sonty;


import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

public class SAVE_HTTP_CUSTOM {

    public static String GET_PAGE(String host, int port, String URL) {
        Socket socket = null;
        try {
            boolean USE_PROXY = false;

            if (USE_PROXY == true) {
                // EXPERIMENTAL
                SocketAddress sa = InetSocketAddress.createUnresolved("172.245.185.119", 1080);
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, sa);
                socket = new Socket(proxy);

                System.getProperties().put("proxySet", "true");
                System.getProperties().put("socksProxyHost", "172.245.185.119");
                System.getProperties().put("socksProxyPort", "1080");
            } else {
                socket = new Socket(InetAddress.getByName(host), port);
            }
            socket.setSoTimeout(1000);
            socket.setTcpNoDelay(true);

            // URL
            String header = createGetHeader(
                    host,
                    "close",
                    "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:99.0) Gecko/20200101 Firefox/99.0"
            );
            String GET_String = "GET " + URL + "HTTP/1.1 " + "" + header;

            String s = "GET " + URL + " HTTP/1.1" + "\n";

            PrintWriter pw = new PrintWriter(socket.getOutputStream());
            pw.println("GET " + URL + " HTTP/1.1");
            pw.println(header);
            pw.println("");
            pw.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String full_str = "";
            String str;
            while ((str = br.readLine()) != null) {
                full_str += str;
            }

            return full_str;
            //region EXCEPTION HANDLING
        } catch (Exception e) {
            System.out.println("HTTP _ ERROR " + e.getMessage());
            e.printStackTrace();
            BackgroundService.retry_count++;
            Log.d("HTTP_ERROR_", e.toString());
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return "HTTP ERROR " + e.getMessage();
        } finally {
            try {
                //socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //endregion
    }

    public static String createGetHeader(String host, String connection, String userAgent) {
        /* Connection: Keep-Alive / close */
        return "Host: " + host + "\n" +
                "Connection: " + connection + "\n" +
                "User-Agent: " + userAgent + "\n\n";

    }

    private static String convertInputStreamToString(InputStream is) {
        StringWriter writer = new StringWriter();
        try {
            //org.apache.commons.io.IOUtils.toString(is, StandardCharsets.UTF_8);
            org.apache.commons.io.IOUtils.copy(is, writer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return writer.toString();
    }
}
