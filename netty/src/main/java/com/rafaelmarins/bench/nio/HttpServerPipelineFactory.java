package com.rafaelmarins.bench.nio;

import static org.jboss.netty.channel.Channels.pipeline;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

public class HttpServerPipelineFactory implements ChannelPipelineFactory {
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

        pipeline.addLast("handler", new HttpServerHandler());
        return pipeline;
    }
}
