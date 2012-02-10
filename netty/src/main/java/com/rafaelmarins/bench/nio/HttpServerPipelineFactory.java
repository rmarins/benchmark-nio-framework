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

	private static String docRoot;
	private static String uploadDir;

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());

        boolean threadEnabled = Boolean.parseBoolean(System.getProperty("threading", "false"));
        if (threadEnabled) {
	        pipeline.addLast(
	                "executor",
	                new ExecutionHandler(
	                        new OrderedMemoryAwareThreadPoolExecutor(
	                                Constant.THREAD_POOL_SIZE,
	                                Constant.CHANNEL_MEMORY_LIMIT,
	                                Constant.GLOBAL_MEMORY_LIMIT,
	                                5000,
	                                TimeUnit.MILLISECONDS)));
        }

        pipeline.addLast("streamer", new ChunkedWriteHandler());

        if (docRoot == null) {
        	String prop = System.getProperty("docRoot");
        	if (prop == null) {
        		docRoot = System.getProperty("user.dir");
        	} else {
        		docRoot = prop;
        	}
        }

        if (uploadDir == null) {
        	String propup = System.getProperty("uploadDir");
        	if (propup == null) {
        		uploadDir = System.getProperty("user.dir");
        	} else {
        		uploadDir = propup;
        	}
        }

        pipeline.addLast("handler", new HttpServerHandler(docRoot, uploadDir));
        return pipeline;
    }
}
