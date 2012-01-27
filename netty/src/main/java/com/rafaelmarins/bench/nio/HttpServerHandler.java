package com.rafaelmarins.bench.nio;

import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;

public class HttpServerHandler extends SimpleChannelUpstreamHandler {

	private static final String HELLO_WORLD = "<html><body><h1>hello world</h1></body></html>";

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

        HttpRequest request = (HttpRequest) e.getMessage();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentLength(response, HELLO_WORLD.length());
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf-8");
        response.setContent(copiedBuffer(HELLO_WORLD, CharsetUtil.UTF_8));

        Channel ch = e.getChannel();
        ChannelFuture writeFuture = ch.write(response);
        
        // Decide whether to close the connection or not.
        if (!isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    		throws Exception {
        e.getCause().printStackTrace();
        e.getChannel().close();
    }

}
