package com.develotters.wiretap;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;

public class Main {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		TcpServer.create()
				.port(getPort(args))
				.wiretap(true)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
				.doOnBound(disposableServer -> LOGGER.info("Listening on " + disposableServer.port()))
				.doOnConnection(Main::timeoutConfigurer)
				.handle(Main::payloadHandler)
				.bindNow()
				.onDispose()
				.block();
	}

	private static int getPort(String[] args) {
		List<String> arguments = Arrays.stream(args).toList();
		int portIndex = arguments.indexOf("--port") + 1;
		if (portIndex > 0) {
			return Integer.parseInt(arguments.get(portIndex));
		}

		return 8080;
	}

	private static Publisher<Void> payloadHandler(NettyInbound in, NettyOutbound out) {
		return in.receive()
				.asString()
				.flatMap(payload -> out.sendString(Mono.empty()));
	}

	private static void timeoutConfigurer(Connection connection) {
		connection.addHandler(new ReadTimeoutHandler(5, TimeUnit.SECONDS));
	}
}
