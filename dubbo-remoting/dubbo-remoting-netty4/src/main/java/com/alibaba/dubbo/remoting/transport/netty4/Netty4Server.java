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

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ExecutorUtil;
import com.alibaba.dubbo.common.utils.NamedThreadFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.Server;
import com.alibaba.dubbo.remoting.transport.AbstractServer;
import com.alibaba.dubbo.remoting.transport.dispatcher.ChannelHandlers;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * NettyServer
 * 
 * @author qian.lei
 * @author chao.liuc
 * @author 赵甜
 * 升级组件到netty 4.x
 */
public class Netty4Server extends AbstractServer implements Server {

	private static final Logger logger = LoggerFactory.getLogger(Netty4Server.class);

	private Map<String, Channel>  channels; // <ip:port, channel>

	private EventLoopGroup bossGroup;

	private EventLoopGroup workGroup;


	private io.netty.channel.Channel channel;

	public Netty4Server(URL url, ChannelHandler handler) throws RemotingException{
		super(url, ChannelHandlers.wrap(handler, ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME)));
	}

	@Override
	protected void doOpen() throws Throwable {

		bossGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors(), (new NamedThreadFactory("NettyServerBoss", true)));
		workGroup = new NioEventLoopGroup(getUrl()
				.getPositiveParameter(Constants.IO_THREADS_KEY, Constants.DEFAULT_IO_THREADS)
				, new NamedThreadFactory("NettyServerWorker", true));

		final Netty4Handler nettyHandler = new Netty4Handler(getUrl(), this);

		ServerBootstrap bootstrap = new ServerBootstrap()
				.group(bossGroup, workGroup)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.TCP_NODELAY, true)
				.option(ChannelOption.SO_BACKLOG, 1024)
				.childOption(ChannelOption.TCP_NODELAY, true)
				.childHandler(new ChannelInitializer<NioSocketChannel>() {

					@Override
					protected void initChannel(NioSocketChannel ch) throws Exception {
						Netty4CodecAdapter adapter = new Netty4CodecAdapter(getCodec() ,getUrl(), Netty4Server.this);
						ChannelPipeline pipeline = ch.pipeline();
						/*int idleTimeout = getIdleTimeout();
		                if (idleTimeout > 10000) {
		                    pipeline.addLast("timer", new IdleStateHandler(timer, idleTimeout / 1000, 0, 0));
		                }*/
						pipeline.addLast("decoder", adapter.getDecoder());
						pipeline.addLast("encoder", adapter.getEncoder());
						pipeline.addLast("handler", nettyHandler);

					}
				});


		channels = nettyHandler.getChannels();

		// bind
		channel = bootstrap.bind(getBindAddress()).awaitUninterruptibly().channel();
	}

	@Override
	protected void doClose() throws Throwable {
		try {
			if (channel != null) {
				// unbind.
				channel.close().syncUninterruptibly();
			}
		} catch (Throwable e) {
			logger.warn(e.getMessage(), e);
		}
		try {
			Collection<com.alibaba.dubbo.remoting.Channel> channels = getChannels();
			if (channels != null && channels.size() > 0) {
				for (com.alibaba.dubbo.remoting.Channel channel : channels) {
					try {
						channel.close();
					} catch (Throwable e) {
						logger.warn(e.getMessage(), e);
					}
				}
			}
		} catch (Throwable e) {
			logger.warn(e.getMessage(), e);
		}
		try {
			if(bossGroup != null){
				bossGroup.shutdownGracefully().syncUninterruptibly();
			}
			if(workGroup != null){
				workGroup.shutdownGracefully().syncUninterruptibly();
			}
		} catch (Throwable e) {
			logger.warn(e.getMessage(), e);
		}
		try {
			if (channels != null) {
				channels.clear();
			}
		} catch (Throwable e) {
			logger.warn(e.getMessage(), e);
		}
	}

	public Collection<Channel> getChannels() {
		Collection<Channel> chs = new HashSet<Channel>();
		for (Channel channel : this.channels.values()) {
			if (channel.isConnected()) {
				chs.add(channel);
			} else {
				channels.remove(NetUtils.toAddressString(channel.getRemoteAddress()));
			}
		}
		return chs;
	}

	public Channel getChannel(InetSocketAddress remoteAddress) {
		return channels.get(NetUtils.toAddressString(remoteAddress));
	}

	public boolean isBound() {
		return channel.isRegistered();
	}

}