package com.lefu.remote.netty.test.file;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.lefu.remote.netty.ChannelHandlerFactoryAdapter;
import com.lefu.remote.netty.server.IOServer;
import com.lefu.remote.netty.server.NettyServer;

public class FileServer {
	public static final AttributeKey<RecordBean> KEY = AttributeKey.valueOf("key");
	private NettyServer ioServer;
	
	public FileServer() {
		ioServer = new IOServer(new FileServerHandlerFactory());
		try {
			ioServer.bind(9999);
			System.in.read();
			ioServer.destroy();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		new FileServer();
	}
	
	public class FileServerHandlerFactory extends ChannelHandlerFactoryAdapter {
		@Override
		public ChannelOutboundHandler newEncoder() {
			return new MessageToByteEncoder<byte[]>() {

				@Override
				protected void encode(ChannelHandlerContext ctx, byte[] msg,
						ByteBuf out) throws Exception {
					out.writeBytes(msg);
				}
				
			};
		}
		
		@Override
		public ChannelInboundHandler newDecoder() {
			return new ByteToMessageDecoder() {
				private final DateFormat format = new SimpleDateFormat("MMddHHmmss");
				private final byte[] temp = new byte[10240];
				
				@Override
				protected void decode(ChannelHandlerContext ctx, ByteBuf in,
						List<Object> out) throws Exception {
					if (in.readableBytes() == 0) {
						return;
					}
					Attribute<RecordBean> attr = ctx.channel().attr(KEY);
					RecordBean rb = attr.get();
					if (rb == null) {
						rb = new RecordBean();
						attr.set(rb);
					}
					if (!rb.isStarted()) {
						if (in.readableBytes() < 8) {
							return;
						}
						long size = in.readLong();
						System.out.println("File size " + size);
						rb.setTotal(size);
						rb.setStarted(true);
						File temp = File.createTempFile(format.format(new Date()), ".file");
						rb.setFile(temp.getAbsolutePath());
						MessageDigest digest = MessageDigest.getInstance("SHA");
						OutputStream outStream = new DigestOutputStream(new BufferedOutputStream(new FileOutputStream(temp), 10240), digest);
						rb.setOut(outStream);
						rb.setDigest(digest);
					}
					OutputStream stream = rb.getOut();
					int readSize = 0;
					if (rb.getTotal() - rb.getOffset() >= in.readableBytes()) {
						readSize = in.readableBytes();
					} else {
						long remaining = rb.getTotal() - rb.getOffset();
						readSize = (int) remaining;
					}
					if (readSize > temp.length) { // Maybe readSize more than temp's capacity
						readSize = temp.length;
					}
					in.readBytes(temp, 0, readSize);
					stream.write(temp, 0, readSize);
					long offset = rb.getOffset();
					offset += readSize;
					rb.setOffset(offset);
					if (offset >= rb.getTotal()) {
						stream.close();
						rb.setStarted(false);
						rb.setOffset(0l);
						out.add(rb.getFile());
						System.out.println(HexUtil.bytesToHexString(rb.getDigest().digest()));
					}
				}
				
			};
		}
		
		@Override
		public ChannelInboundHandler newInstance() {
			return new ChannelInboundHandlerAdapter() {
				
				 @Override
				 public void channelActive(ChannelHandlerContext ctx) throws Exception {
					 
				 }
				 
				 @Override
				 public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//					 Attribute<RecordBean> attr = ctx.attr(KEY);
//					 if (attr.get().getOut() != null) {
//						 attr.get().getOut().close();
//						 System.out.println("OutputStream closed.");
//					 }
				 }
				 
				 @Override
				 public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
					 String path = (String) msg;
					 File f = new File(path);
					 System.out.println(f.getAbsolutePath() + ":" + f.length());
				 }
				 
				 @Override
				 public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
				            throws Exception {
					 cause.printStackTrace();
				 }
			};
		}
	}
	
}
