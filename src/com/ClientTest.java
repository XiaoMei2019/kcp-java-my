package com;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

/**
 * 周富炖 海尔优家IOT
 * 
 * @author 19026404
 *
 */
public class ClientTest extends KcpClient {
	public ClientTest(long conv_) throws SocketException, UnknownHostException {
		super(conv_);
	}

	/**
	 * @param args
	 * @throws SocketException
	 * @throws UnknownHostException
	 */
	public static void main(String[] args) throws SocketException, UnknownHostException {
		ClientTest kcpClient = new ClientTest(13333);//
		kcpClient.NoDelay(1, 10, 2, 1);
		kcpClient.WndSize(32, 32);
		// kcpClient.setTimeout(10 * 10000000);//超时时间100000S
		kcpClient.SetMtu(1024);
		kcpClient.connect(new InetSocketAddress("127.0.0.1", 33333));
		kcpClient.start();
		int i = 100;
		while (i > 0) {
			String content = "你好，我是客户端，用于测试！";
			byte[] buffer = content.getBytes(Charset.forName("utf-8"));
			kcpClient.send(buffer);
			i--;
		}

	}
}
