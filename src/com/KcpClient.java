package com;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
public class KcpClient extends KCP implements Runnable{
	
	 private DatagramSocket ds;
	 private DatagramPacket sendPack;
	 private InetSocketAddress remote;
	 private volatile boolean running;
	 private final Object waitLock = new Object();
	 private KCP kcp;
	 public KcpClient(long conv_) throws SocketException, UnknownHostException {
		super(conv_);
		kcp=this;
	    ds=new DatagramSocket();
	 } 
	 //udp发送数据
	 @Override
	 protected void output(byte[] buffer, int size) {
		 try {
			 sendPack = new DatagramPacket(buffer,size);
			 sendPack.setAddress(InetAddress.getByName("127.0.0.1"));//设置信息发送的ip地址,255.255.255.255受限的局域网
			 sendPack.setPort(33333);//设置信息发送到的端口
			 ds.send(sendPack);
			//System.out.println(remote.getPort());
			//datagramSocket.close();//发送完关闭即可，接下来就是监听30000端口 	
		} catch (IOException e) {
			e.printStackTrace();
		}
	 }
	

	public void connect(InetSocketAddress addr){   	
		this.remote = addr;
    }
	
	/**a
	 * 
     * 开启线程处理kcp状态
     */
	public void start() {
		 this.running = true;
		 Thread t = new Thread(this);//启动这个线程
         t.setName("kcp server thread");
         t.start();
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
     * 开启线程处理kcp状态
     */
	@Override
	public void run() {
		long start, end;
        while (running)
        {     
        	try {
        		start = System.currentTimeMillis();//开始时间     
                this.kcp.Update(start); //
                end = System.currentTimeMillis();//结束时间
                if (end - start < this.interval)
                {
                    synchronized (waitLock)
                    {
                        try
                        {	
                        	//System.out.println("导致线程进入等待状态直到它被通知或者经过指定的时间。这些方法只能在同步方法中调用");
                            waitLock.wait(this.interval - end + start);
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
    public void send(byte[] bb)
    {   
        if (this!= null)
        {   
        	//System.out.print(bb.length);
            int sendResult=this.kcp.Send(bb);
            if(sendResult==0) {
            	System.out.println("数据加入发送队列成功");
            }
        }
    }
	@Override
	public int Recv(byte[] buffer) {
		
		return super.Recv(buffer);
	}
	/**
     * 关掉
     *
     */
    public void close()
    {
        if (this.running)
        {
            this.running = false;
        }
    }
    
	 
}
