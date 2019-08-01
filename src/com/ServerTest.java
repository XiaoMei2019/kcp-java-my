package com;

import java.net.SocketException;
import java.net.UnknownHostException;

public class ServerTest extends KcpServer {
	public ServerTest(long conv_) throws SocketException, UnknownHostException {
		super(conv_);
	}

	public static void main(String[] args) throws SocketException, UnknownHostException {
		ServerTest kcpServer = new ServerTest(13333);// con
		kcpServer.NoDelay(1, 20, 2, 1);
		kcpServer.WndSize(32, 32);
		// kcpServer.setTimeout(10 * 1000);//超时时间10S
		kcpServer.SetMtu(1024);
		kcpServer.start();
	}
}
