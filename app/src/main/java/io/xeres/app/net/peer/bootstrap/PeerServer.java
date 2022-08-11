/*
 * Copyright (c) 2019-2020 by David Gerber - https://zapek.com
 *
 * This file is part of Xeres.
 *
 * Xeres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeres.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.xeres.app.net.peer.bootstrap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.xeres.app.database.DatabaseSessionManager;
import io.xeres.app.net.peer.PeerConnectionManager;
import io.xeres.app.properties.NetworkProperties;
import io.xeres.app.service.LocationService;
import io.xeres.app.service.SettingsService;
import io.xeres.app.xrs.service.serviceinfo.ServiceInfoRsService;
import io.xeres.common.properties.StartupProperties;
import io.xeres.ui.support.tray.TrayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import static io.xeres.app.net.peer.ConnectionType.TCP_INCOMING;


abstract class PeerServer
{
	@SuppressWarnings("NonConstantLogger")
	protected final Logger log = LoggerFactory.getLogger(getClass().getName());

	private final SettingsService settingsService;
	private final NetworkProperties networkProperties;
	private final LocationService locationService;
	private final PeerConnectionManager peerConnectionManager;
	private final DatabaseSessionManager databaseSessionManager;
	private final ServiceInfoRsService serviceInfoRsService;
	private final TrayService trayService;

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private ChannelFuture channel;

	protected PeerServer(SettingsService settingsService, NetworkProperties networkProperties, LocationService locationService, PeerConnectionManager peerConnectionManager, DatabaseSessionManager databaseSessionManager, ServiceInfoRsService serviceInfoRsService, TrayService trayService)
	{
		this.settingsService = settingsService;
		this.networkProperties = networkProperties;
		this.locationService = locationService;
		this.peerConnectionManager = peerConnectionManager;
		this.databaseSessionManager = databaseSessionManager;
		this.serviceInfoRsService = serviceInfoRsService;
		this.trayService = trayService;
	}

	@Transactional(readOnly = true) // needed for getPort() to work
	public void start(int localPort)
	{
		bossGroup = new NioEventLoopGroup(1);
		workerGroup = new NioEventLoopGroup();

		try
		{
			var serverBootstrap = new ServerBootstrap();
			serverBootstrap.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.option(ChannelOption.SO_BACKLOG, 128) // should be more
					.option(ChannelOption.SO_REUSEADDR, true)
					.handler(new LoggingHandler(LogLevel.DEBUG))
					.childHandler(new PeerInitializer(peerConnectionManager, databaseSessionManager, locationService, settingsService, networkProperties, serviceInfoRsService, TCP_INCOMING, trayService));

			channel = serverBootstrap.bind(localPort).sync();
			log.info("Listening on {}, port {}", channel.channel().localAddress(), localPort);
		}
		catch (InterruptedException e)
		{
			throw new IllegalStateException("Interrupted: " + e.getMessage(), e);
		}
	}

	public void stop()
	{
		if (channel == null)
		{
			return;
		}

		if (StartupProperties.getBoolean(StartupProperties.Property.FAST_SHUTDOWN, false))
		{
			log.debug("Shutting down netty server (fast)...");
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
		else
		{
			log.info("Shutting down netty server...");
			try
			{
				workerGroup.shutdownGracefully().sync();
				bossGroup.shutdownGracefully().sync();
			}
			catch (InterruptedException e)
			{
				log.error("Error while shutting down netty server: {}", e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
	}
}
