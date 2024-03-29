//=====================================================================
//
// KCP - A Better ARQ Protocol Implementation
// skywind3000 (at) gmail.com, 2010-2011
//  
// Features:
// + Average RTT reduce 30% - 40% vs traditional ARQ like tcp.
// + Maximum RTT reduce three times vs tcp.
// + Lightweight, distributed as a single source file.
//
//=====================================================================
package com;

import java.util.ArrayList;

public abstract class KCP {

	// =====================================================================
	// KCP BASIC
	// =====================================================================
	public final int IKCP_RTO_NDL = 30; // no delay min rto
	public final int IKCP_RTO_MIN = 100; // normal min rto
	public final int IKCP_RTO_DEF = 200;
	public final int IKCP_RTO_MAX = 60000;
	public final int IKCP_CMD_PUSH = 81; // cmd: push data
	public final int IKCP_CMD_ACK = 82; // cmd: ack
	public final int IKCP_CMD_WASK = 83; // cmd: window probe (ask)
	public final int IKCP_CMD_WINS = 84; // cmd: window size (tell)
	public final int IKCP_ASK_SEND = 1; // need to send IKCP_CMD_WASK
	public final int IKCP_ASK_TELL = 2; // need to send IKCP_CMD_WINS
	public final int IKCP_WND_SND = 32;
	public final int IKCP_WND_RCV = 32;
	public final int IKCP_MTU_DEF = 1400;
	public final int IKCP_ACK_FAST = 3;
	public final int IKCP_INTERVAL = 100;
	public final int IKCP_OVERHEAD = 24;
	public final int IKCP_DEADLINK = 10;
	public final int IKCP_THRESH_INIT = 2;
	public final int IKCP_THRESH_MIN = 2;
	public final int IKCP_PROBE_INIT = 7000; // 7 secs to probe window size
	public final int IKCP_PROBE_LIMIT = 120000; // up to 120 secs to probe window

	protected abstract void output(byte[] buffer, int size); // buffer长度

	// encode 8 bits unsigned int
	public static void ikcp_encode8u(byte[] p, int offset, byte c) {
		p[0 + offset] = c;
	}

	// decode 8 bits unsigned int
	public static byte ikcp_decode8u(byte[] p, int offset) {
		return p[0 + offset];
	}

	/* encode 16 bits unsigned int (msb) */
	public static void ikcp_encode16u(byte[] p, int offset, int w) {
		p[offset + 0] = (byte) (w >> 8);
		p[offset + 1] = (byte) (w >> 0);
	}

	/* decode 16 bits unsigned int (msb) */
	public static int ikcp_decode16u(byte[] p, int offset) {
		int ret = (p[offset + 0] & 0xFF) << 8 | (p[offset + 1] & 0xFF);
		return ret;
	}

	/* encode 32 bits unsigned int (msb) */
	public static void ikcp_encode32u(byte[] p, int offset, long l) {
		p[offset + 0] = (byte) (l >> 24);
		p[offset + 1] = (byte) (l >> 16);
		p[offset + 2] = (byte) (l >> 8);
		p[offset + 3] = (byte) (l >> 0);
	}

	/* decode 32 bits unsigned int (msb) */
	public static long ikcp_decode32u(byte[] p, int offset) {
		long ret = (p[offset + 0] & 0xFFL) << 24 | (p[offset + 1] & 0xFFL) << 16 | (p[offset + 2] & 0xFFL) << 8
				| p[offset + 3] & 0xFFL;
		return ret;
	}

	public static void slice(ArrayList list, int start, int stop) {
		int size = list.size();
		for (int i = 0; i < size; ++i) {
			if (i < stop - start) {
				list.set(i, list.get(i + start));
			} else {
				list.remove(stop - start);
			}
		}
	}

	static long _imin_(long a, long b) {
		return a <= b ? a : b;
	}

	static long _imax_(long a, long b) {
		return a >= b ? a : b;
	}

	static long _ibound_(long lower, long middle, long upper) {
		return _imin_(_imax_(lower, middle), upper);
	}

	static int _itimediff(long later, long earlier) {
		return ((int) (later - earlier));
	}

	public class Segment {
		// conv为一个表示会话编号的整数，和tcp的 conv一样，
		// 通信双方需保证 conv相同，相互的数据包才能够被认可
		protected long conv = 0;
		// cmd用来区分分片的作用。IKCP_CMD_PUSH:数据分片 IKCP_CMD_ACK:ack分片
		// IKCP_CMD_WASK请求告知窗口大小 IKCP_CMD_WINS:告知窗口大小
		protected long cmd = 0;
		// message中的segment分片ID（在message中的索引，由大到小，0表示最后一个分片）
		protected long frg = 0;
		// 剩余接收窗口大小(接收窗口大小-接收队列大小)
		protected long wnd = 0;
		// message发送时刻的时间戳
		protected long ts = 0;
		// message分片segment的序号
		protected long sn = 0;
		// 待接收消息序号(接收滑动窗口左端)
		protected long una = 0;
		// 下次超时重传的时间戳
		protected long resendts = 0;
		// 该分片的超时重传等待时间
		protected long rto = 0;
		// 收到ack时计算的该分片被跳过的累计次数
		protected long fastack = 0;
		// 发送分片的次数，每发送一次加一。
		protected long xmit = 0;
		// 发送数据
		protected byte[] data;

		protected Segment(int size) {
			this.data = new byte[size];
		}

		// ---------------------------------------------------------------------
		// ikcp_encode_seg
		// ---------------------------------------------------------------------
		// encode a segment into buffer
		protected int encode(byte[] ptr, int offset) {
			int offset_ = offset;

			ikcp_encode32u(ptr, offset, conv);
			offset += 4;
			ikcp_encode8u(ptr, offset, (byte) cmd);
			offset += 1;
			ikcp_encode8u(ptr, offset, (byte) frg);
			offset += 1;
			ikcp_encode16u(ptr, offset, (int) wnd);
			offset += 2;
			ikcp_encode32u(ptr, offset, ts);
			offset += 4;
			ikcp_encode32u(ptr, offset, sn);
			offset += 4;
			ikcp_encode32u(ptr, offset, una);
			offset += 4;
			ikcp_encode32u(ptr, offset, (long) data.length);
			offset += 4;

			return offset - offset_;
		}
	}

	long conv = 0;
	// long user = user;
	long snd_una = 0;
	long snd_nxt = 0;
	long rcv_nxt = 0;
	long ts_recent = 0;
	long ts_lastack = 0;
	long ts_probe = 0;
	long probe_wait = 0;
	long snd_wnd = IKCP_WND_SND;
	long rcv_wnd = IKCP_WND_RCV;
	long rmt_wnd = IKCP_WND_RCV;
	long cwnd = 0;
	long incr = 0;
	long probe = 0;
	long mtu = IKCP_MTU_DEF;
	long mss = this.mtu - IKCP_OVERHEAD;
	byte[] buffer = new byte[(int) (mtu + IKCP_OVERHEAD) * 3];
	ArrayList<Segment> nrcv_buf = new ArrayList<>(128);
	ArrayList<Segment> nsnd_buf = new ArrayList<>(128);
	ArrayList<Segment> nrcv_que = new ArrayList<>(128);
	ArrayList<Segment> nsnd_que = new ArrayList<>(128);
	long state = 0;
	ArrayList<Long> acklist = new ArrayList<>(128);
	// long ackblock = 0;
	// long ackcount = 0;
	long rx_srtt = 0;
	long rx_rttval = 0;
	long rx_rto = IKCP_RTO_DEF;
	long rx_minrto = IKCP_RTO_MIN;
	long current = 0;
	long interval = IKCP_INTERVAL;
	long ts_flush = IKCP_INTERVAL;
	long nodelay = 0;
	long updated = 0;
	long logmask = 0;
	long ssthresh = IKCP_THRESH_INIT;
	long fastresend = 0;
	long nocwnd = 0;
	long xmit = 0;
	long dead_link = IKCP_DEADLINK;
	// long output = NULL;
	// long writelog = NULL;
	public Object Segment;

	public KCP(long conv_) {
		conv = conv_;
	}

	// ---------------------------------------------------------------------
	// user/upper level recv: returns size, returns below zero for EAGAIN
	// ---------------------------------------------------------------------
	// 返回接收到的buffer长度
	public int Recv(byte[] buffer) {
		if (0 == nrcv_que.size()) {
			return -1;
		}

		int peekSize = PeekSize();
		if (0 > peekSize) {
			return -2;
		}

		if (peekSize > buffer.length) {
			return -3;
		}

		boolean recover = false;
		if (nrcv_que.size() >= rcv_wnd) {
			recover = true;
		}

		// merge fragment.
		int count = 0;
		int n = 0;
		for (Segment seg : nrcv_que) {
			System.arraycopy(seg.data, 0, buffer, n, seg.data.length);
			n += seg.data.length;
			count++;
			if (0 == seg.frg) {
				break;
			}
		}

		if (0 < count) {
			slice(nrcv_que, count, nrcv_que.size());
		}

		// move available data from rcv_buf -> nrcv_que
		count = 0;
		for (Segment seg : nrcv_buf) {
			if (seg.sn == rcv_nxt && nrcv_que.size() < rcv_wnd) {
				nrcv_que.add(seg);
				rcv_nxt++;
				count++;
			} else {
				break;
			}
		}

		if (0 < count) {
			slice(nrcv_buf, count, nrcv_buf.size());
		}

		// fast recover
		if (nrcv_que.size() < rcv_wnd && recover) {
			// ready to send back IKCP_CMD_WINS in ikcp_flush
			// tell remote my window size
			probe |= IKCP_ASK_TELL;
		}

		return n;
	}

	// ---------------------------------------------------------------------
	// peek data size
	// ---------------------------------------------------------------------
	// check the size of next message in the recv queue
	// 璁＄畻鎺ユ敹闃熷垪涓湁澶氬皯鍙敤鐨勬暟鎹�
	public int PeekSize() {
		if (0 == nrcv_que.size()) {
			return -1;
		}

		Segment seq = nrcv_que.get(0);

		if (0 == seq.frg) {
			return seq.data.length;
		}

		if (nrcv_que.size() < seq.frg + 1) {
			return -1;
		}

		int length = 0;

		for (Segment item : nrcv_que) {
			length += item.data.length;
			if (0 == item.frg) {
				break;
			}
		}

		return length;
	}

	// ---------------------------------------------------------------------
	// user/upper level send, returns below zero for error
	// ---------------------------------------------------------------------
	// 涓婂眰瑕佸彂閫佺殑鏁版嵁涓㈢粰鍙戦�侀槦鍒楋紝鍙戦�侀槦鍒椾細鏍规嵁mtu澶у皬鍒嗙墖
	public int Send(byte[] buffer) {

		if (0 == buffer.length) {
			return -1;
		}

		int count;

		// 鏍规嵁mss澶у皬鍒嗙墖
		if (buffer.length < mss) {
			count = 1;
		} else {
			count = (int) (buffer.length + mss - 1) / (int) mss;
		}

		if (255 < count) {
			return -2;
		}

		if (0 == count) {
			count = 1;
		}

		int offset = 0;

		// 鍒嗙墖鍚庡姞鍏ュ埌鍙戦�侀槦鍒�
		int length = buffer.length;
		for (int i = 0; i < count; i++) {
			int size = (int) (length > mss ? mss : length);
			Segment seg = new Segment(size);
			System.arraycopy(buffer, offset, seg.data, 0, size);
			offset += size;
			seg.frg = count - i - 1;
			nsnd_que.add(seg);
			length -= size;
		}
		// System.out.println("数据分片数量" + nsnd_que.size());
		return 0;
	}

	// ---------------------------------------------------------------------
	// parse ack update ack. 更新ACK
	// ---------------------------------------------------------------------
	void update_ack(int rtt) {
		if (0 == rx_srtt) {
			rx_srtt = rtt;
			rx_rttval = rtt / 2;
		} else {
			int delta = (int) (rtt - rx_srtt);
			if (0 > delta) {
				delta = -delta;
			}

			rx_rttval = (3 * rx_rttval + delta) / 4;
			rx_srtt = (7 * rx_srtt + rtt) / 8;
			if (rx_srtt < 1) {
				rx_srtt = 1;
			}
		}

		int rto = (int) (rx_srtt + _imax_(1, 4 * rx_rttval));
		rx_rto = _ibound_(rx_minrto, rto, IKCP_RTO_MAX);
	}

	// 小缓冲区 ，更新snd_una为snd_buf中seg.sn或snd.nxt
	void shrink_buf() {
		if (nsnd_buf.size() > 0) {
			snd_una = nsnd_buf.get(0).sn;
		} else {
			snd_una = snd_nxt;
		}
	}

	// 解析ACk
	// 遍历snd_buf中（snd_una, snd_nxt），将sn相等的删除，直到大于sn
	// ack报文则包含了对端收到的kcp包的序号，接到ack包后需要删除发送缓冲区中与ack包中的发送包序号（sn）相同的kcp包。
	void parse_ack(long sn) {
		if (_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0) {
			return;
		}

		int index = 0;
		for (Segment seg : nsnd_buf) {
			if (_itimediff(sn, seg.sn) < 0) {
				break;
			}

			// 鍘熺増ikcp_parse_fastack&ikcp_parse_ack閫昏緫閲嶅
			seg.fastack++;

			if (sn == seg.sn) {
				nsnd_buf.remove(index);
				break;
			}
			index++;
		}
	}

	// 解析una字段后需要把发送缓冲区里面包序号小于una的包全部丢弃掉
	void parse_una(long una) {
		int count = 0;
		for (Segment seg : nsnd_buf) {
			if (_itimediff(una, seg.sn) > 0) {
				count++;
			} else {
				break;
			}
		}

		if (0 < count) {
			slice(nsnd_buf, count, nsnd_buf.size());
		}
	}

	// ---------------------------------------------------------------------
	// ack append
	// ---------------------------------------------------------------------
	// 更新segment的sn及ts放在acklist中
	void ack_push(long sn, long ts) {
		// c鍘熺増瀹炵幇涓寜*2鎵╁ぇ瀹归噺
		acklist.add(sn);
		acklist.add(ts);
	}

	// ---------------------------------------------------------------------
	// parse data
	// ---------------------------------------------------------------------
	// 鐢ㄦ埛鏁版嵁鍖呰В鏋�
	void parse_data(Segment newseg) {
		long sn = newseg.sn;
		boolean repeat = false;

		if (_itimediff(sn, rcv_nxt + rcv_wnd) >= 0 || _itimediff(sn, rcv_nxt) < 0) {
			return;
		}

		int n = nrcv_buf.size() - 1;
		int after_idx = -1;

		// 鍒ゆ柇鏄惁鏄噸澶嶅寘锛屽苟涓旇绠楁彃鍏ヤ綅缃�
		for (int i = n; i >= 0; i--) {
			Segment seg = nrcv_buf.get(i);
			if (seg.sn == sn) {
				repeat = true;
				break;
			}

			if (_itimediff(sn, seg.sn) > 0) {
				after_idx = i;
				break;
			}
		}

		// 濡傛灉涓嶆槸閲嶅鍖咃紝鍒欐彃鍏�
		if (!repeat) {
			if (after_idx == -1) {
				nrcv_buf.add(0, newseg);
			} else {
				nrcv_buf.add(after_idx + 1, newseg);
			}
		}

		// move available data from nrcv_buf -> nrcv_que
		// 灏嗚繛缁寘鍔犲叆鍒版帴鏀堕槦鍒�
		int count = 0;
		for (Segment seg : nrcv_buf) {
			if (seg.sn == rcv_nxt && nrcv_que.size() < rcv_wnd) {
				nrcv_que.add(seg);
				rcv_nxt++;
				count++;
			} else {
				break;
			}
		}

		// 浠庢帴鏀剁紦瀛樹腑绉婚櫎
		if (0 < count) {
			slice(nrcv_buf, count, nrcv_buf.size());
		}
	}

	// when you received a low level packet (eg. UDP packet), call it
	// ---------------------------------------------------------------------
	// input data
	// ---------------------------------------------------------------------
	// 收到底层来的UPD数据包，将他解析，返回0表示正常，其他异常
	public int Input(byte[] data) {
		long s_una = snd_una;
		if (data.length < IKCP_OVERHEAD) {
			return 0;
		}
		int offset = 0;
		while (true) {
			long ts, sn, length, una, conv_;
			int wnd;
			byte cmd, frg;

			if (data.length - offset < IKCP_OVERHEAD) {
				break;
			}

			conv_ = ikcp_decode32u(data, offset);
			offset += 4;
			if (conv != conv_) {
				// System.out.println(conv_);
				// System.out.println(conv);
				return -1;
			}

			cmd = ikcp_decode8u(data, offset);
			offset += 1;
			frg = ikcp_decode8u(data, offset);
			offset += 1;
			wnd = ikcp_decode16u(data, offset);
			offset += 2;
			ts = ikcp_decode32u(data, offset);
			offset += 4;
			sn = ikcp_decode32u(data, offset);
			offset += 4;
			una = ikcp_decode32u(data, offset);
			offset += 4;
			length = ikcp_decode32u(data, offset);
			offset += 4;

			if (data.length - offset < length) {
				return -2;
			}

			if (cmd != IKCP_CMD_PUSH && cmd != IKCP_CMD_ACK && cmd != IKCP_CMD_WASK && cmd != IKCP_CMD_WINS) {
				return -3;
			}

			rmt_wnd = (long) wnd;
			parse_una(una);
			shrink_buf();

			if (IKCP_CMD_ACK == cmd) {
				if (_itimediff(current, ts) >= 0) {
					update_ack(_itimediff(current, ts));
				}
				parse_ack(sn);
				shrink_buf();
			} else if (IKCP_CMD_PUSH == cmd) {
				if (_itimediff(sn, rcv_nxt + rcv_wnd) < 0) {
					ack_push(sn, ts);
					if (_itimediff(sn, rcv_nxt) >= 0) {
						Segment seg = new Segment((int) length);
						seg.conv = conv_;
						seg.cmd = cmd;
						seg.frg = frg;
						seg.wnd = wnd;
						seg.ts = ts;
						seg.sn = sn;
						seg.una = una;

						if (length > 0) {
							System.arraycopy(data, offset, seg.data, 0, (int) length);
						}

						parse_data(seg);
					}
				}
			} else if (IKCP_CMD_WASK == cmd) {
				// ready to send back IKCP_CMD_WINS in Ikcp_flush
				// tell remote my window size
				probe |= IKCP_ASK_TELL;
			} else if (IKCP_CMD_WINS == cmd) {
				// do nothing
			} else {
				return -3;
			}

			offset += (int) length;
		}

		if (_itimediff(snd_una, s_una) > 0) {
			if (cwnd < rmt_wnd) {
				long mss_ = mss;
				if (cwnd < ssthresh) {
					cwnd++;
					incr += mss_;
				} else {
					if (incr < mss_) {
						incr = mss_;
					}
					incr += (mss_ * mss_) / incr + (mss_ / 16);
					if ((cwnd + 1) * mss_ <= incr) {
						cwnd++;
					}
				}
				if (cwnd > rmt_wnd) {
					cwnd = rmt_wnd;
					incr = rmt_wnd * mss_;
				}
			}
		}

		return 0;
	}

	// 未使用的窗口大小
	int wnd_unused() {
		if (nrcv_que.size() < rcv_wnd) {
			return (int) (int) rcv_wnd - nrcv_que.size();
		}
		return 0;
	}

	// ---------------------------------------------------------------------
	// ikcp_flush
	// ---------------------------------------------------------------------
	void flush() {
		long current_ = current;
		byte[] buffer_ = buffer;
		int change = 0;
		int lost = 0;

		// 'ikcp_update' haven't been called.
		if (0 == updated) {
			return;
		}
		// 'ikcp_update' have been called.
		Segment seg = new Segment(0);
		seg.conv = conv;
		seg.cmd = IKCP_CMD_ACK;
		seg.wnd = (long) wnd_unused();
		seg.una = rcv_nxt;

		// flush acknowledges
		// 灏哸cklist涓殑ack鍙戦�佸嚭鍘�
		int count = acklist.size() / 2;// ????
		int offset = 0;
		for (int i = 0; i < count; i++) {
			if (offset + IKCP_OVERHEAD > mtu) {
				output(buffer, offset);
				offset = 0;
			}
			// ikcp_ack_get
			seg.sn = acklist.get(i * 2 + 0);
			seg.ts = acklist.get(i * 2 + 1);
			offset += seg.encode(buffer, offset);
			// System.out.println(offset);
		}
		acklist.clear();

		// probe window size (if remote window size equals zero)
		// rmt_wnd=0鏃讹紝鍒ゆ柇鏄惁闇�瑕佽姹傚绔帴鏀剁獥鍙�
		if (0 == rmt_wnd) {
			if (0 == probe_wait) {
				probe_wait = IKCP_PROBE_INIT;
				ts_probe = current + probe_wait;
			} else {
				// 閫愭鎵╁ぇ璇锋眰鏃堕棿闂撮殧
				if (_itimediff(current, ts_probe) >= 0) {
					if (probe_wait < IKCP_PROBE_INIT) {
						probe_wait = IKCP_PROBE_INIT;
					}
					probe_wait += probe_wait / 2;
					if (probe_wait > IKCP_PROBE_LIMIT) {
						probe_wait = IKCP_PROBE_LIMIT;
					}
					ts_probe = current + probe_wait;
					probe |= IKCP_ASK_SEND;
				}
			}
		} else {
			ts_probe = 0;
			probe_wait = 0;
		}

		// flush window probing commands
		// 璇锋眰瀵圭鎺ユ敹绐楀彛
		if ((probe & IKCP_ASK_SEND) != 0) {
			seg.cmd = IKCP_CMD_WASK;
			if (offset + IKCP_OVERHEAD > mtu) {
				output(buffer, offset);
				offset = 0;
			}
			offset += seg.encode(buffer, offset);
		}

		// flush window probing commands(c#)
		// 鍛婅瘔瀵圭鑷繁鐨勬帴鏀剁獥鍙�
		if ((probe & IKCP_ASK_TELL) != 0) {
			seg.cmd = IKCP_CMD_WINS;
			if (offset + IKCP_OVERHEAD > mtu) {
				output(buffer, offset);
				offset = 0;
			}
			offset += seg.encode(buffer, offset);
		}

		probe = 0;

		// calculate window size
		long cwnd_ = _imin_(snd_wnd, rmt_wnd);
		// 濡傛灉閲囩敤鎷ュ鎺у埗
		if (0 == nocwnd) {
			cwnd_ = _imin_(cwnd, cwnd_);
		}

		count = 0;
		// move data from snd_queue to snd_buf
		for (Segment nsnd_que1 : nsnd_que) {
			if (_itimediff(snd_nxt, snd_una + cwnd_) >= 0) {
				break;
			}
			Segment newseg = nsnd_que1;
			newseg.conv = conv;
			newseg.cmd = IKCP_CMD_PUSH;
			newseg.wnd = seg.wnd;
			newseg.ts = current_;
			newseg.sn = snd_nxt;
			newseg.una = rcv_nxt;
			newseg.resendts = current_;
			newseg.rto = rx_rto;
			newseg.fastack = 0;
			newseg.xmit = 0;
			nsnd_buf.add(newseg);
			snd_nxt++;
			count++;
		}

		if (0 < count) {
			slice(nsnd_que, count, nsnd_que.size());
		}

		// calculate resent
		long resent = (fastresend > 0) ? fastresend : 0xffffffff;
		long rtomin = (nodelay == 0) ? (rx_rto >> 3) : 0;

		// flush data segments
		for (Segment segment : nsnd_buf) {
			boolean needsend = false;
			if (0 == segment.xmit) {
				// 绗竴娆′紶杈�
				needsend = true;
				segment.xmit++;
				segment.rto = rx_rto;
				segment.resendts = current_ + segment.rto + rtomin;
			} else if (_itimediff(current_, segment.resendts) >= 0) {
				// 涓㈠寘閲嶄紶
				needsend = true;
				segment.xmit++;
				xmit++;
				if (0 == nodelay) {
					segment.rto += rx_rto;
				} else {
					segment.rto += rx_rto / 2;
				}
				segment.resendts = current_ + segment.rto;
				lost = 1;
			} else if (segment.fastack >= resent) {
				// 蹇�熼噸浼�
				needsend = true;
				segment.xmit++;
				segment.fastack = 0;
				segment.resendts = current_ + segment.rto;
				change++;
			}

			if (needsend) {
				segment.ts = current_;
				segment.wnd = seg.wnd;
				segment.una = rcv_nxt;

				int need = IKCP_OVERHEAD + segment.data.length;
				if (offset + need >= mtu) {
					// System.out.println(offset+"---791行");
					// System.out.println(buffer.length+"---792行");
					output(buffer, offset);
					offset = 0;
				}

				offset += segment.encode(buffer, offset);
				if (segment.data.length > 0) {
					System.arraycopy(segment.data, 0, buffer, offset, segment.data.length);
					offset += segment.data.length;
				}

				if (segment.xmit >= dead_link) {
					state = -1; // state = 0(c#)
				}
			}
		}

		// flash remain segments
		if (offset > 0) {
			// System.out.println(offset+"---811行");
			output(buffer, offset);
		}

		// update ssthresh
		// 鎷ュ閬垮厤
		if (change != 0) {
			long inflight = snd_nxt - snd_una;
			ssthresh = inflight / 2;
			if (ssthresh < IKCP_THRESH_MIN) {
				ssthresh = IKCP_THRESH_MIN;
			}
			cwnd = ssthresh + resent;
			incr = cwnd * mss;
		}

		if (lost != 0) {
			ssthresh = cwnd / 2;
			if (ssthresh < IKCP_THRESH_MIN) {
				ssthresh = IKCP_THRESH_MIN;
			}
			cwnd = 1;
			incr = mss;
		}

		if (cwnd < 1) {
			cwnd = 1;
			incr = mss;
		}
	}

	// ---------------------------------------------------------------------
	// update state (call it repeatedly, every 10ms-100ms), or you can ask
	// ikcp_check when to call it again (without ikcp_input/_send calling).
	// 'current' - current timestamp in millisec.
	// 更新状态（每10ms-100ms重复调用），或者可以询问ikcp_check何时再次调用（不使用ikcp_input/_send调用）。
	// ---------------------------------------------------------------------
	public void Update(long current_) {

		current = current_;

		// 之前没有调用 Update函数
		if (0 == updated) {// 不需要更新
			updated = 1;// 现在表示已经调用Update函数
			ts_flush = current;
		}

		// 涓ゆ鏇存柊闂撮殧
		int slap = _itimediff(current, ts_flush);

		// interval璁剧疆杩囧ぇ鎴栬�匲pdate璋冪敤闂撮殧澶箙
		if (slap >= 10000 || slap < -10000) {
			ts_flush = current;
			slap = 0;
		}

		// flush鍚屾椂璁剧疆涓嬩竴娆℃洿鏂版椂闂�
		if (slap >= 0) {
			ts_flush += interval;
			if (_itimediff(current, ts_flush) >= 0) {
				ts_flush = current + interval;
			}
			flush();
		}
	}

	// ---------------------------------------------------------------------
	// Determine when should you invoke ikcp_update:
	// returns when you should invoke ikcp_update in millisec, if there
	// is no ikcp_input/_send calling. you can call ikcp_update in that
	// time, instead of call update repeatly.
	// Important to reduce unnacessary ikcp_update invoking. use it to
	// schedule ikcp_update (eg. implementing an epoll-like mechanism,
	// or optimize ikcp_update when handling massive kcp connections)
	// ---------------------------------------------------------------------
	public long Check(long current_) {

		long ts_flush_ = ts_flush;
		long tm_flush = 0x7fffffff;
		long tm_packet = 0x7fffffff;
		long minimal;

		if (0 == updated) {
			return current_;
		}

		if (_itimediff(current_, ts_flush_) >= 10000 || _itimediff(current_, ts_flush_) < -10000) {
			ts_flush_ = current_;
		}

		if (_itimediff(current_, ts_flush_) >= 0) {
			return current_;
		}

		tm_flush = _itimediff(ts_flush_, current_);

		for (Segment seg : nsnd_buf) {
			int diff = _itimediff(seg.resendts, current_);
			if (diff <= 0) {
				return current_;
			}
			if (diff < tm_packet) {
				tm_packet = diff;
			}
		}

		minimal = tm_packet < tm_flush ? tm_packet : tm_flush;
		if (minimal >= interval) {
			minimal = interval;
		}

		return current_ + minimal;
	}

	// change MTU size, default is 1400
	public int SetMtu(int mtu_) {
		if (mtu_ < 50 || mtu_ < (int) IKCP_OVERHEAD) {
			return -1;
		}

		byte[] buffer_ = new byte[(mtu_ + IKCP_OVERHEAD) * 3];
		if (null == buffer_) {
			return -2;
		}

		mtu = (long) mtu_;
		mss = mtu - IKCP_OVERHEAD;
		buffer = buffer_;
		return 0;
	}

	public int Interval(int interval_) {
		if (interval_ > 5000) {
			interval_ = 5000;
		} else if (interval_ < 10) {
			interval_ = 10;
		}
		interval = (long) interval_;
		return 0;
	}

	// fastest: ikcp_nodelay(kcp, 1, 20, 2, 1)
	// nodelay: 0:disable(default), 1:enable
	// interval: internal update timer interval in millisec, default is 100ms
	// resend: 0:disable fast resend(default), 1:enable fast resend
	// nc: 0:normal congestion control(default), 1:disable congestion control
	public int NoDelay(int nodelay_, int interval_, int resend_, int nc_) {

		if (nodelay_ >= 0) {
			nodelay = nodelay_;
			if (nodelay_ != 0) {
				rx_minrto = IKCP_RTO_NDL;
			} else {
				rx_minrto = IKCP_RTO_MIN;
			}
		}

		if (interval_ >= 0) {
			if (interval_ > 5000) {
				interval_ = 5000;
			} else if (interval_ < 10) {
				interval_ = 10;
			}
			interval = interval_;
		}

		if (resend_ >= 0) {
			fastresend = resend_;
		}

		if (nc_ >= 0) {
			nocwnd = nc_;
		}

		return 0;
	}

	// set maximum window size: sndwnd=32, rcvwnd=32 by default
	public int WndSize(int sndwnd, int rcvwnd) {
		if (sndwnd > 0) {
			snd_wnd = (long) sndwnd;
		}

		if (rcvwnd > 0) {
			rcv_wnd = (long) rcvwnd;
		}
		return 0;
	}

	// get how many packet is waiting to be sent
	public int WaitSnd() {
		return nsnd_buf.size() + nsnd_que.size();
	}
}
