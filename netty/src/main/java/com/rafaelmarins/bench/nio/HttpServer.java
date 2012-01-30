package com.rafaelmarins.bench.nio;

import static java.lang.String.format;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * Hello world!
 * 
 */
public class HttpServer {

	private final int port;

	public HttpServer(int port) {
		this.port = port;
	}

	public void run() {

		// Configure the server.
		ServerBootstrap bootstrap = new ServerBootstrap(
				new NioServerSocketChannelFactory(
						Executors.newCachedThreadPool(),
						Executors.newCachedThreadPool()));

		// Set up the event pipeline factory.
		bootstrap.setPipelineFactory(new HttpServerPipelineFactory());

        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption(
                "child.receiveBufferSizePredictorFactory",
                new AdaptiveReceiveBufferSizePredictorFactory(
                        Constant.MIN_READ_BUFFER_SIZE,
                        Constant.INITIAL_READ_BUFFER_SIZE,
                        Constant.MAX_READ_BUFFER_SIZE)
                );

		// Bind and start to accept incoming connections.
		bootstrap.bind(new InetSocketAddress(port));
	}

	public static void main(String[] args) {
		int port;
		if (args.length > 0) {
			port = Integer.parseInt(args[0]);
		} else {
			port = 8010;
		}
		new HttpServer(port).run();
		System.out.println(format("Server running at http://127.0.0.1:%d/", port));
	}
}
