package com.rafaelmarins.bench.nio;

import static java.lang.System.*;
import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;

@Sharable
public class HttpServerHandler extends SimpleChannelUpstreamHandler {

	private static final String HELLO_WORLD = "<html><body><h1>Ol‡ Mundo!!!</h1></body></html>";

	private long lineBreakCounter = Long.MIN_VALUE;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

        HttpRequest request = (HttpRequest) e.getMessage();
        boolean isKeepAlive = isKeepAlive(request);

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf-8");
        response.setContent(copiedBuffer(HELLO_WORLD, CharsetUtil.UTF_8));

        if (isKeepAlive) {
        	setContentLength(response, response.getContent().readableBytes());
        }

        Channel ch = e.getChannel();
        ChannelFuture writeFuture = ch.write(response);
        
        // Decide whether to close the connection or not.
        if (!isKeepAlive) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }

//        lineBreak();
//        out.print(".");

    }

    private void lineBreak() {
        if (lineBreakCounter % 80 == 0) {
        	out.print(" " + (lineBreakCounter - Long.MIN_VALUE));
        	out.println();
        }
        if (lineBreakCounter >= Long.MAX_VALUE) {
        	lineBreakCounter = Long.MIN_VALUE;
        } else {
            lineBreakCounter++;
        }
    }

	@Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    		throws Exception {
//        e.getCause().printStackTrace();
//        lineBreak();
//    	err.print("!");
        try {
        	e.getChannel().close();
        } catch (Throwable t) {
        	// ignore
        }
    }

}
