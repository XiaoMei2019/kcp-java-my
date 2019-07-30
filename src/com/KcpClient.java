package com;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class KcpClient extends KCP implements Runnable {

	private static DatagramSocket datagramSocket;
	private static DatagramPacket datagramPacket;
	private InetSocketAddress remote;
	private volatile boolean running;
	private final Object waitLock = new Object();
	private static KCP kcp;

	public KcpClient(long conv_) throws SocketException, UnknownHostException {
		super(conv_);
		kcp = this;
		datagramSocket = new DatagramSocket();
	}

	// udp发送数据
	@Override
	protected void output(byte[] buffer, int size) {
		try {
			datagramPacket = new DatagramPacket(buffer, size);
			datagramPacket.setAddress(InetAddress.getByName("127.0.0.1"));// 设置信息发送的ip地址,255.255.255.255受限的局域网
			datagramPacket.setPort(33333);// 设置信息发送到的端口
			datagramSocket.send(datagramPacket);
			// System.out.println(remote.getPort());
			// datagramSocket.close();//发送完关闭即可，接下来就是监听30000端口
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// UDP收到消息
	public static void UDP_Input() throws SocketException {
		// 创建一个数据报套接字，并将其绑定到指定port上
		byte[] receMsgs = new byte[1024];
		// DatagramPacket(byte buf[], int length),建立一个字节数组来接收UDP包
		datagramPacket = new DatagramPacket(receMsgs, receMsgs.length);
		try {
			// receive()来等待接收UDP数据报
			// System.out.println("等待数据");
			datagramSocket.receive(datagramPacket);
			// System.out.println(datagramPacket.getData());
			// System.out.println("获得数据");
			// 调用kcp的input
			String receStr = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
			// System.out.println(receStr);
			onReceive(receStr.getBytes());
		} catch (Exception e) {
			// System.out.println("超时未获得数据");
			e.printStackTrace();
		}
	}

	public void connect(InetSocketAddress addr) {
		this.remote = addr;
	}

	/**
	 * a
	 * 
	 * 开启线程处理kcp状态
	 */
	public void start() {
		this.running = true;
		Thread t = new Thread(this);// 启动这个线程
		t.setName("kcp client thread start");
		t.start();
		// 启动数据接受线程
		ReceiveThread receiveThread = new ReceiveThread();
		receiveThread.start();
	}

	/**
	 * UDP接收数据处理线程
	 * 
	 * @author 19026404
	 *
	 */
	public class ReceiveThread extends Thread {
		@Override
		public void run() {
			while (running) {
				try {
					UDP_Input();
				} catch (SocketException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void noDelay(int nodelay, int interval, int resend, int nc) {
		this.NoDelay(nodelay, interval, resend, nc);
	}

	/**
	 * set maximum window size: sndwnd=32, rcvwnd=32 by default
	 *
	 * @param sndwnd
	 * @aram rcvwnd
	 */
	public void wndSize(int sndwnd, int rcvwnd) {
		this.kcp.WndSize(sndwnd, rcvwnd);
	}

	/**
	 * change MTU size, default is 1400
	 *
	 * @param mtu
	 */
	public void setMtu(int mtu) {
		this.SetMtu(mtu);
	}

	/**
	 * 开启线程处理kcp状态
	 */
	@Override
	public void run() {
		long start, end;
		while (running) {
			try {
				start = System.currentTimeMillis();// 开始时间
				this.kcp.Update(start); //
				end = System.currentTimeMillis();// 结束时间
				if (end - start < this.interval) {
					synchronized (waitLock) {
						try {
							// System.out.println("导致线程进入等待状态直到它被通知或者经过指定的时间。这些方法只能在同步方法中调用");
							waitLock.wait(this.interval - end + start);
						} catch (InterruptedException ex) {
							ex.printStackTrace();
						}
					}
				}
			} catch (Exception e) {
				running = false;
				e.printStackTrace();
			}
		}
	}

	/**
	 * 发送消息
	 *
	 * @param bb
	 */
	public void send(byte[] bb) {
		if (this != null) {
			// System.out.print(bb.length);
			int sendResult = this.kcp.Send(bb);
			if (sendResult == 0) {
				System.out.println("数据加入发送队列成功");
			}
		}
	}

	/**
	 * 关掉
	 *
	 */
	public void close() {
		if (this.running) {
			this.running = false;
		}
	}

	/**
	 * 收到服务器消息
	 *
	 * @param dp
	 */
	public static void onReceive(byte[] buffer) {
		int result = 0;
		byte[] receiveByte = new byte[1024];
		if (kcp != null) {
			result = kcp.Input(buffer);
			System.out.println(result);
			// 返回0表示正常
			if (result == 0) {
				int dataLength = kcp.Recv(receiveByte);
				if (dataLength > 0) {
					System.out.println(new String(receiveByte, 0, dataLength));
				}
			}
		}
	}
}
