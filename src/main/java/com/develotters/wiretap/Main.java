package com.develotters.wiretap;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRoutes;
import reactor.netty.tcp.TcpServer;

public class Main {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		List<String> arguments = Arrays.stream(args).toList();
		getServer(getServerType(arguments), getPort(arguments)).onDispose().block();
	}

	private static ServerType getServerType (List<String> arguments) {
		if (arguments.contains("--http")) {
			return ServerType.HTTP;
		}
		else {
			return ServerType.TCP;
		}
	}

	private static int getPort(List<String> arguments) {
		int portIndex = arguments.indexOf("--port") + 1;
		if (portIndex > 0) {
			return Integer.parseInt(arguments.get(portIndex));
		}

		return 8080;
	}

	private static DisposableServer getServer(ServerType serverType, int port) {
		return switch (serverType) {
			case HTTP -> createHttpServer(port);
			default -> createTcpServer(port);
		};
	}

	private static DisposableServer createTcpServer(int port) {
		return TcpServer.create()
				.port(port)
				.wiretap(true)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
				.doOnBound(Main::unBoundHandler)
				.doOnConnection(Main::timeoutConfigurer)
				.handle(Main::tcpPayloadHandler)
				.bindNow();
	}

	private static DisposableServer createHttpServer(int port) {
		return HttpServer.create()
				.port(port)
				.wiretap(true)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
				.doOnBound(Main::unBoundHandler)
//				.doOnConnection(Main::timeoutConfigurer)
				.route(Main::httpRouter)
				.bindNow();
	}

	private static void unBoundHandler(DisposableServer server) {
		LOGGER.info("Listening on " + server.port());
	}

	private static void timeoutConfigurer(Connection connection) {
		connection.addHandler(new ReadTimeoutHandler(10, TimeUnit.SECONDS));
	}

	private static Publisher<Void> tcpPayloadHandler(NettyInbound in, NettyOutbound out) {
		return in.receive()
				.asString()
				.flatMap(payload -> out.sendString(Mono.empty()));
	}

	private static void httpRouter(HttpServerRoutes routes) {
		routes
				.ws("/ws", (wsIn, wsOut) -> wsOut.sendString(wsIn.receive().asString().map(msg -> "echo: " + msg)))
				.route(rq -> true, (rq, rs) -> rs.sendString(Mono.just("ok")));
	}
}
