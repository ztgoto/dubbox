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

import java.util.concurrent.TimeUnit;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NamedThreadFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.transport.AbstractClient;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.internal.SystemPropertyUtil;

/**
 * NettyClient.
 * 
 * @author qian.lei
 * @author william.liangf
 * @author 赵甜
 * 升级组件到netty 4.x
 */
public class Netty4Client extends AbstractClient {
    
    private static final Logger logger = LoggerFactory.getLogger(Netty4Client.class);

    

    private static final int DEFAULT_EVENT_LOOP_THREADS;
    
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                if (logger.isInfoEnabled()) {
                    logger.info("Run shutdown hook of netty client now.");
                }

                group.shutdownGracefully().syncUninterruptibly();
            }
        }, "DubboShutdownHook-NettyClient"));
        
        DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", Constants.DEFAULT_IO_THREADS));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopThreads: " + DEFAULT_EVENT_LOOP_THREADS);
        }
    }
    
    private static final EventLoopGroup group = new NioEventLoopGroup(DEFAULT_EVENT_LOOP_THREADS
    		, new NamedThreadFactory("NettyClientTCPWorker", true));

    private Bootstrap bootstrap;

    private volatile Channel channel; // volatile, please copy reference to use
    
    public Netty4Client(final URL url, final ChannelHandler handler) throws RemotingException{
        super(url, wrapChannelHandler(url, handler));
    }
    
    @Override
    protected void doOpen() throws Throwable {
    	
        final Netty4Handler nettyHandler = new Netty4Handler(getUrl(), this);
        bootstrap = new Bootstrap().group(group)
        		.channel(NioSocketChannel.class)
        		.option(ChannelOption.SO_KEEPALIVE, true)
        		.option(ChannelOption.TCP_NODELAY, true)
        		.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getTimeout())
        		.handler(new ChannelInitializer<NioSocketChannel>() {

					@Override
					protected void initChannel(NioSocketChannel ch) throws Exception {
						Netty4CodecAdapter adapter = new Netty4CodecAdapter(getCodec(), getUrl(), Netty4Client.this);
		                ChannelPipeline pipeline = ch.pipeline();
		                pipeline.addLast("decoder", adapter.getDecoder());
		                pipeline.addLast("encoder", adapter.getEncoder());
		                pipeline.addLast("handler", nettyHandler);
						
					}
				});
    }

    protected void doConnect() throws Throwable {
        long start = System.currentTimeMillis();
        ChannelFuture future = bootstrap.connect(getConnectAddress());
        try{
            boolean ret = future.awaitUninterruptibly(getConnectTimeout(), TimeUnit.MILLISECONDS);
            
            if (ret && future.isSuccess()) {
                Channel newChannel = future.channel();
                
                try {
                    // 关闭旧的连接
                    Channel oldChannel = Netty4Client.this.channel; // copy reference
                    if (oldChannel != null) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close old netty channel " + oldChannel + " on create new netty channel " + newChannel);
                            }
                            oldChannel.close().syncUninterruptibly();
                        } finally {
                            Netty4Channel.removeChannelIfDisconnected(oldChannel);
                        }
                    }
                } finally {
                    if (Netty4Client.this.isClosed()) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close new netty channel " + newChannel + ", because the client closed.");
                            }
                            newChannel.close().syncUninterruptibly();
                        } finally {
                            Netty4Client.this.channel = null;
                            Netty4Channel.removeChannelIfDisconnected(newChannel);
                        }
                    } else {
                        Netty4Client.this.channel = newChannel;
                    }
                }
            } else if (future.cause() != null) {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + ", error message is:" + future.cause().getMessage(), future.cause());
            } else {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + " client-side timeout "
                        + getConnectTimeout() + "ms (elapsed: " + (System.currentTimeMillis() - start) + "ms) from netty client "
                        + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion());
            }
        }finally{
            if (! isConnected()) {
                future.cancel(true);
            }
        }
    }

    @Override
    protected void doDisConnect() throws Throwable {
        try {
            Netty4Channel.removeChannelIfDisconnected(channel);
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }
    }
    
    @Override
    protected void doClose() throws Throwable {
        /*try {
            bootstrap.releaseExternalResources();
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }*/
    }

    @Override
    protected com.alibaba.dubbo.remoting.Channel getChannel() {
        Channel c = channel;
        if (c == null || ! c.isActive())
            return null;
        return Netty4Channel.getOrAddChannel(c, getUrl(), this);
    }
    

}