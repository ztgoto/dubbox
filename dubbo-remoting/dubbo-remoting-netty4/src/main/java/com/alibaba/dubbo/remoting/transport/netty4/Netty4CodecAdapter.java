/*
 * Copyright 1999-2011 Alibaba Group.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.remoting.transport.netty4;

import java.io.IOException;
import java.util.List;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.Codec2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * NettyCodecAdapter.
 * 
 * @author qian.lei
 * @author 赵甜 升级组件到netty 4.x
 */
final class Netty4CodecAdapter {

	private final ChannelHandler encoder = new InternalEncoder();

	private final ChannelHandler decoder = new InternalDecoder();

	private final Codec2 codec;

	private final URL url;

	private final int bufferSize;

	private final com.alibaba.dubbo.remoting.ChannelHandler handler;

	public Netty4CodecAdapter(Codec2 codec, URL url, com.alibaba.dubbo.remoting.ChannelHandler handler) {
		this.codec = codec;
		this.url = url;
		this.handler = handler;
		int b = url.getPositiveParameter(Constants.BUFFER_KEY, Constants.DEFAULT_BUFFER_SIZE);
		this.bufferSize = b >= Constants.MIN_BUFFER_SIZE && b <= Constants.MAX_BUFFER_SIZE ? b
				: Constants.DEFAULT_BUFFER_SIZE;
	}

	public ChannelHandler getEncoder() {
		return encoder;
	}

	public ChannelHandler getDecoder() {
		return decoder;
	}

	@Sharable
	private class InternalEncoder extends MessageToByteEncoder<Object> {

		@Override
		protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
			Netty4BackedChannelBuffer buffer = new Netty4BackedChannelBuffer(
					ByteBufAllocator.DEFAULT.buffer(bufferSize));
			Netty4Channel channel = Netty4Channel.getOrAddChannel(ctx.channel(), url, handler);
			try {
				codec.encode(channel, buffer, msg);
				out.writeBytes(buffer.toByteBuffer());
			} finally {
				Netty4Channel.removeChannelIfDisconnected(ctx.channel());
				buffer.release();
			}

		}

	}

	private class InternalDecoder extends ByteToMessageDecoder {


		@Override
		protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
			if (!in.isReadable())
				return;
			Netty4BackedChannelBuffer message = new Netty4BackedChannelBuffer(in);


			Netty4Channel channel = Netty4Channel.getOrAddChannel(ctx.channel(), url, handler);

			Object result;
			int saveReaderIndex;

			try {
				// decode object.
				do {
					saveReaderIndex = message.readerIndex();
					try {
						result = codec.decode(channel, message);
					} catch (IOException e) {
						if(message.readable()){
							message.discardReadBytes();
						}
						throw e;
					}
					if (result == Codec2.DecodeResult.NEED_MORE_INPUT) {
						message.readerIndex(saveReaderIndex);
						break;
					} else {
						if (saveReaderIndex == message.readerIndex()) {
							message.clear();
							throw new IOException("Decode without read data.");
						}
						if (result != null) {
							out.add(result);
						}
					}
				} while (message.readable());
			} finally {
				if (message.readable()) {
					message.discardReadBytes();
				} 
				Netty4Channel.removeChannelIfDisconnected(ctx.channel());
			}
		}
		

	}

}