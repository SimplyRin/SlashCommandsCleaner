package net.simplyrin.slashcommandscleaner;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.joor.Reflect;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.util.internal.PlatformDependent;
import lombok.Getter;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.BungeeServerInfo;
import net.md_5.bungee.ServerConnection;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.Util;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.PipelineUtils;
import net.md_5.bungee.protocol.MinecraftDecoder;
import net.md_5.bungee.protocol.MinecraftEncoder;
import net.md_5.bungee.protocol.Protocol;

/**
 * Created by SimplyRin on 2022/04/12.
 *
 * Copyright (c) 2022 SimplyRin
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class SlashCommandsCleaner extends Plugin implements Listener {

	// Player name
	private List<String> firstLogin;
	private List<String> loginList;
	
	@Getter
	private Configuration config;
	
	public boolean isFirstLogin(String name) {
		if (this.firstLogin.contains(name)) {
			this.firstLogin.remove(name);
			return true;
		}
		
		return false;
	}
	
	@Override
	public void onEnable() {
		this.firstLogin = new ArrayList<>();
		this.loginList = new ArrayList<>();
		
		File folder = this.getDataFolder();
		folder.mkdirs();
		
		var provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
		
		File file = new File(folder, "config.yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			Configuration config = new Configuration();
			config.set("FakeList", Arrays.asList("help", "list", "me", "msg", "teammsg", "tell", "tm", "trigger", "w"));

			try {
				provider.save(config, file);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			this.config = provider.load(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		this.getProxy().getPluginManager().registerListener(this, this);
	}
	
	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent event) {
		var player = event.getPlayer();
		
		if (this.firstLogin.contains(player.getName())) {
			this.firstLogin.remove(player.getName());
		}
		
		this.loginList.remove(player.getName());
	}
	
	@EventHandler(priority = EventPriority.LOWEST)
	public void onServerConnect(ServerConnectEvent event) {
		final UserConnection userConnection = (UserConnection) event.getPlayer();
		final ServerConnectRequest request = event.getRequest();
		final ProxiedPlayer player = event.getPlayer();
		
		final BungeeServerInfo target = (BungeeServerInfo) event.getTarget(); // Update in case the event changed target
		
		final BungeeCord bungee = BungeeCord.getInstance();
		
		event.setCancelled(true);

		if (player.getServer() != null && Objects.equals(player.getServer().getInfo(), target)) {
			player.sendMessage(new TextComponent(bungee.getTranslation("already_connected")));
			return;
		}
		if (userConnection.getPendingConnects().contains(target)) {
			player.sendMessage(new TextComponent(bungee.getTranslation("already_connecting")));
			return;
		}

		userConnection.getPendingConnects().add(target);

		ChannelInitializer<Channel> initializer = new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				PipelineUtils.BASE.initChannel(ch);
				ch.pipeline().addAfter(PipelineUtils.FRAME_DECODER, PipelineUtils.PACKET_DECODER, 
						new MinecraftDecoder(Protocol.HANDSHAKE, false, player.getPendingConnection().getVersion()));

				ch.pipeline().addAfter(PipelineUtils.FRAME_PREPENDER, PipelineUtils.PACKET_ENCODER, 
						new MinecraftEncoder(Protocol.HANDSHAKE, false, player.getPendingConnection().getVersion()));

				ch.pipeline().get(HandlerBoss.class).setHandler(new CustomServerConnector(SlashCommandsCleaner.this, bungee, userConnection, target));
			}
		};
		
		ChannelFutureListener listener = new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (!future.isSuccess()) {
					future.channel().close();
					userConnection.getPendingConnects().remove(target);

					ServerInfo def = userConnection.updateAndGetNextServer(target);
					if (request.isRetry() && def != null && (player.getServer() == null || def != player.getServer().getInfo())) {
						player.sendMessage(new TextComponent(bungee.getTranslation("fallback_lobby")));
						userConnection.connect(def, null, true, ServerConnectEvent.Reason.LOBBY_FALLBACK);
					} else if (userConnection.isDimensionChange()) {
						player.disconnect(new TextComponent(bungee.getTranslation("fallback_kick", connectionFailMessage(player, future.cause()))));
					} else {
						player.sendMessage(new TextComponent(bungee.getTranslation("fallback_kick", connectionFailMessage(player, future.cause()))));
					}
				}
			}
		};
		
		ChannelWrapper ch = Reflect.on(userConnection).field("ch").get();

		Bootstrap b = new Bootstrap()
				.channel(PipelineUtils.getChannel(target.getAddress()))
				.group(ch.getHandle().eventLoop())
				.handler(initializer)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, request.getConnectTimeout())
				.remoteAddress(target.getAddress());
		// Windows is bugged, multi homed users will just have to live with random connecting IPs
		if (player.getPendingConnection().getListener().isSetLocalAddress() && !PlatformDependent.isWindows() 
				&& player.getPendingConnection().getListener().getSocketAddress() instanceof InetSocketAddress) {
			b.localAddress(player.getPendingConnection().getListener().getHost().getAddress(), 0);
		}
		b.connect().addListener(listener);

		if (!this.loginList.contains(player.getName())) {
			userConnection.setServer(new ServerConnection(ch, target));
			this.firstLogin.add(player.getName());
		}
		this.loginList.add(player.getName());
	}
	
	private String connectionFailMessage(ProxiedPlayer player, Throwable cause) {
		return player.getGroups().contains("admin") ? Util.exception(cause, false) : cause.getClass().getName();
	}

}
