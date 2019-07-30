package com;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
public class KcpServer extends KCP implements Runnable{
	
	 //byte[] buffer=new byte[1024];
	 private static DatagramSocket datagramSocket;
	 private static DatagramPacket datagramPacket;
	 private InetSocketAddress remote;
	 private volatile static boolean running;
	 private final Object waitLock = new Object();
	 private static KCP kcp;
	 public KcpServer(long conv_) throws SocketException, UnknownHostException {
		super(conv_);
		kcp=this;
		datagramSocket = new DatagramSocket(33333);//监听33333端口
	    //datagramSocket.setSoTimeout(3000);//超时时间100ms
	 } 
	 //udp发送数据
	 @Override
	 protected void output(byte[] buffer, int size) {
		 datagramPacket = new DatagramPacket(buffer, size,remote);
		 try{
			datagramSocket.send(datagramPacket);
		 }catch(IOException e) {
			e.printStackTrace();
		 }
	 }
	 //UDP收到消息
	 public static void UDP_Input() throws SocketException {
		//创建一个数据报套接字，并将其绑定到指定port上
	    byte[] receMsgs=new byte[1024];
	    //DatagramPacket(byte buf[], int length),建立一个字节数组来接收UDP包
	    datagramPacket = new DatagramPacket(receMsgs, receMsgs.length);	       	   
	    try{
	    	// receive()来等待接收UDP数据报
	    	//System.out.println("等待数据");
	        datagramSocket.receive(datagramPacket);
	        //System.out.println(datagramPacket.getData());
	        //System.out.println("获得数据");
	        //调用kcp的input
	        String receStr = new String(datagramPacket.getData(), 0 , datagramPacket.getLength());
	        System.out.println(receStr);
	        onReceive(receStr.getBytes());
	    }catch (Exception e) {					
			//System.out.println("超时未获得数据");
			e.printStackTrace();
	    }
	 }
	/**
      * 开启线程处理kcp状态
     */
	public void start() {
		 this.running = true;
		 Thread t = new Thread(this);//启动这个线程
         t.setName("kcp Server thread");
         t.start();
         //启动数据接受线程
         ReceiveThread receiveThread=new ReceiveThread();
         receiveThread.start();
	}
	/**
	 * UDP接收数据处理线程
	 * @author 19026404
	 *
	 */
	public class ReceiveThread extends Thread{
		@Override
		public void run() {
			
			while(running) {
				try {
					UDP_Input();
				} catch (SocketException e) {
					e.printStackTrace();
				}
			}
		}
	}
 	public void noDelay(int nodelay, int interval, int resend, int nc)
	{
	    this.NoDelay(nodelay, interval, resend, nc);
	}
	/**
	 * set maximum window size: sndwnd=32, rcvwnd=32 by default
	 *
	 * @param sndwnd
	 * @aram rcvwnd
	 */
	public void wndSize(int sndwnd, int rcvwnd)
	{
	    this.kcp.WndSize(sndwnd, rcvwnd);
	}
	/**
	 * change MTU size, default is 1400
	 *
	 * @param mtu
	 */
	public void setMtu(int mtu)
	{
	    this.SetMtu(mtu);
	}
	 /**
	 * conv
	 *
	 * @param conv
	 */
	public void setConv(int conv)
	{
	    this.conv = conv;
	}
	/**
     *开启线程处理kcp状态
     */
	@Override
	public void run() {
		long start, end;
        while (running)
        {     
        	try {
        		start = System.currentTimeMillis();//开始时间     
                //this.kcp.Update(start); //
                end = System.currentTimeMillis();//结束时间
                if (end - start < interval)
                {
                    synchronized (waitLock)
                    {
                        try
                        {
                           // System.out.println("等待开始");
                        	waitLock.wait(this.interval - end + start);
                        	//System.out.println("等待结束");
                        } catch (InterruptedException ex){
                        	ex.printStackTrace();
                        }
                    }
                }
			} catch (Exception e) {
				running=false;
				e.printStackTrace();
			}
        }
	}
	/**
     * 发送消息
     *
     * @param bb
     */
    public void send(ByteBuffer bb)
    {
        if (this!= null)
        {
            int sendResult=this.kcp.Send(bb.array());
            if(sendResult==0) {
            	System.out.println("数据加入发送队列");
            }
        }
    }
    /**
     * 收到服务器消息 
     *
     * @param dp
     */
    public static void onReceive(byte[] buffer)
    {	
    	int result=0;
    	byte[] receiveByte=new byte[1024];
        if (kcp!= null)
        {
            result=kcp.Input(buffer);
            System.out.println(result);
            //返回0表示正常
            if(result==0) {
            	int dataLength=kcp.Recv(receiveByte);
            	if(dataLength>0) {
            		System.out.println(new String(receiveByte,0,dataLength));
            	}
            }
        } 
    } 
}
