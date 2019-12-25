package com.sontme.esp.sonty;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class PeerToPeer {

    interface PeerListener {
        void update(InetAddress peer);
    }

    class Responder implements PeerListener {
        @Override
        public void update(InetAddress peer) {
        }
    }

    public static class UDPClient extends AsyncTask<Object, Object, Object> {

        public static List<InetAddress> listAllBroadcastAddresses() throws SocketException {
            List<InetAddress> broadcastList = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces
                    = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                for (InterfaceAddress a : networkInterface.getInterfaceAddresses()) {
                    if (a.getBroadcast() != null) {
                        broadcastList.add(a.getBroadcast());
                        Log.d("torrent_", "broadcast: " + a.getBroadcast() + " _ " + a.getAddress());
                    }
                }
            }
            return broadcastList;
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Object doInBackground(Object... string) {
            DatagramSocket c;
            try {
                c = new DatagramSocket();
                c.setBroadcast(true);
                byte[] sendData = "DISCOVER_REQUEST".getBytes();

                List<InetAddress> broadcasts = listAllBroadcastAddresses();
                for (InetAddress inet : broadcasts) {
                    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, inet, 5001);
                    c.send(packet);
                    //c.close();
                }
                c.setReuseAddress(true);
                c.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Object... progress) {

        }

        @Override
        protected void onPostExecute(Object o) {
        }
    }

    public static class UDPListener extends AsyncTask<Object, Object, Object> {

        private List<PeerListener> listeners = new ArrayList<PeerListener>();

        public void addListener(PeerListener toAdd) {
            listeners.add(toAdd);
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Object doInBackground(Object... string) {
            MulticastSocket socket;
            byte[] buf = new byte[256];
            try {
                socket = new MulticastSocket(5001);
                InetAddress group = InetAddress.getByName("255.255.255.255");
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = new String(
                            packet.getData(), 0, packet.getLength());
                    byte[] str = "DISCOVER_ACCEPT".getBytes();
                    if (received.equals("DISCOVER_REQUEST")) {
                        DatagramPacket dp2 = new DatagramPacket(str, str.length, InetAddress.getByName(packet.getAddress().getHostAddress()), 5001);
                        socket.send(dp2);
                    }
                    if (received.equals("DISCOVER_ACCEPT")) {
                        for (PeerListener hl : listeners) {
                            hl.update(packet.getAddress());
                        }
                    }
                    if ("end".equals(received)) {
                        break;
                    }
                }
                socket.leaveGroup(group);
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Object... progress) {

        }

        @Override
        protected void onPostExecute(Object o) {

        }
    }

    public static class TCPClient extends AsyncTask<Object, Object, Object> {

        String ip;

        public TCPClient(String ip) {
            this.ip = ip;
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Object doInBackground(Object... string) {
            try {
                BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
                Socket clientSocket = new Socket(ip, 4111);
                DataOutputStream outToServer =
                        new DataOutputStream(clientSocket.getOutputStream());
                DataInputStream inFromServer = new DataInputStream(clientSocket.getInputStream());
                String received = inFromUser.readLine();
                outToServer.writeUTF("answer:" + received);
                outToServer.flush();

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Object... progress) {

        }

        @Override
        protected void onPostExecute(Object o) {

        }
    }

    public static class TCPServer extends AsyncTask<Object, Object, Object> {

        private List<PeerListener> listeners = new ArrayList<PeerListener>();

        public void addListener(PeerListener toAdd) {
            listeners.add(toAdd);
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Object doInBackground(Object... string) {
            try {
                ServerSocket welcomeSocket = new ServerSocket(4111);
                while (true) {
                    Socket connectionSocket = welcomeSocket.accept();
                    DataInputStream inFromClient =
                            new DataInputStream(connectionSocket.getInputStream());

                    DataOutputStream outToClient =
                            new DataOutputStream(connectionSocket.getOutputStream());

                    //String received = inFromClient.readUTF();
                    for (PeerListener hl : listeners) {
                        hl.update(connectionSocket.getInetAddress());
                    }

                    //if (received.equals("query")) {
                    //outToClient.writeBytes("welcome");
                    //}
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Object... progress) {

        }

        @Override
        protected void onPostExecute(Object o) {

        }
    }

    public static ArrayList<String> SearchSubnet(String currentIP) {
        int timeout = 500;
        ArrayList<String> ips = new ArrayList<>();

        try {
            String subnet = getSubnet(currentIP);

            for (int i = 1; i < 254; i++) {
                String host = subnet + i;
                if (InetAddress.getByName(host).isReachable(timeout)) {
                    ips.add(host);
                }
            }
        } catch (Exception e) {

        }
        return ips;
    }

    public static String getSubnet(String currentIP) {
        int firstSeparator = currentIP.lastIndexOf("/");
        int lastSeparator = currentIP.lastIndexOf(".");
        return currentIP.substring(firstSeparator + 1, lastSeparator + 1);
    }

    public static boolean checkPortOpen(String ip, int port) {
        Socket socket;
        try {
            socket = new Socket(ip, port);
            if (socket.isConnected() == true) {
                socket.close();
                return true;
            } else {
                socket.close();
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}

