package main;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author miche
 */
public class ConnectionData {

    public final int port;
    public final ServerSocket serverSocket;
    public final Socket clientSocket;
    public final Socket echoSocket;
    public final PrintWriter out;
    public final BufferedReader in;

    public ConnectionData(int port,
            ServerSocket serverSocket,
            Socket clientSocket,
            Socket echoSocket,
            PrintWriter out,
            BufferedReader in) {
        this.port = port;
        this.serverSocket = serverSocket;
        this.echoSocket = echoSocket;
        this.clientSocket = clientSocket;
        this.out = out;
        this.in = in;
    }
    
}
