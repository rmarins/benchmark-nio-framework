package com.rafaelmarins.bench.nio;

import static io.netty.channel.Channels.pipeline;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.execution.ExecutionHandler;
import io.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import io.netty.handler.stream.ChunkedWriteHandler;

public class HttpServerPipelineFactory implements ChannelPipelineFactory {

	private static String serverRoot;

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());

// thread pool disabled
//        pipeline.addLast(
//                "executor",
//                new ExecutionHandler(
//                        new OrderedMemoryAwareThreadPoolExecutor(
//                                Constant.THREAD_POOL_SIZE,
//                                Constant.CHANNEL_MEMORY_LIMIT,
//                                Constant.GLOBAL_MEMORY_LIMIT,
//                                5000,
//                                TimeUnit.MILLISECONDS)));

//        pipeline.addLast("log", new LoggingHandler(InternalLogLevel.INFO));

        pipeline.addLast("streamer", new ChunkedWriteHandler());

        if (serverRoot == null) {
        	String prop = System.getProperty("serverRoot");
        	if (prop == null) {
        		serverRoot = System.getProperty("user.dir");
        	} else {
        		serverRoot = prop;
        	}
        }

        pipeline.addLast("handler", new HttpServerHandler(serverRoot));
        return pipeline;
    }
}
