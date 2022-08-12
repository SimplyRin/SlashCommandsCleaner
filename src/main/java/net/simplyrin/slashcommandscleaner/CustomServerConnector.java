package net.simplyrin.slashcommandscleaner;

import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.joor.Reflect;

import com.google.common.base.Joiner;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.BungeeServerInfo;
import net.md_5.bungee.ServerConnection;
import net.md_5.bungee.ServerConnector;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.score.Objective;
import net.md_5.bungee.api.score.Score;
import net.md_5.bungee.api.score.Scoreboard;
import net.md_5.bungee.api.score.Team;
import net.md_5.bungee.connection.CancelSendSignal;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.ProtocolConstants;
import net.md_5.bungee.protocol.packet.EntityStatus;
import net.md_5.bungee.protocol.packet.GameState;
import net.md_5.bungee.protocol.packet.Login;
import net.md_5.bungee.protocol.packet.PluginMessage;
import net.md_5.bungee.protocol.packet.Respawn;
import net.md_5.bungee.protocol.packet.ScoreboardObjective;
import net.md_5.bungee.protocol.packet.ScoreboardScore;
import net.md_5.bungee.protocol.packet.ViewDistance;

/**
 * Created by SimplyRin on 2022/04/13.
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
public class CustomServerConnector extends ServerConnector {
	
	private final SlashCommandsCleaner instance;
	private final ProxyServer bungee;
	private final UserConnection user;
	private final BungeeServerInfo target;
	
	public CustomServerConnector(SlashCommandsCleaner instance, ProxyServer bungee, UserConnection user, BungeeServerInfo target) {
		super(bungee, user, target);
		
		this.instance = instance;
		this.bungee = bungee;
		this.user = user;
		this.target = target;
	}

	@Override
	public void handle(Login login) throws Exception {
		ChannelWrapper ch = Reflect.on(this).field("ch").get();

		ServerConnection server = new ServerConnection(ch, this.target);
		ServerConnectedEvent event = new ServerConnectedEvent(this.user, server);
		this.bungee.getPluginManager().callEvent(event);

		ch.write(BungeeCord.getInstance().registerChannels(this.user.getPendingConnection().getVersion()));
		Queue<DefinedPacket> packetQueue = this.target.getPacketQueue();
		synchronized (packetQueue) {
			while (!packetQueue.isEmpty()) {
				ch.write(packetQueue.poll());
			}
		}

		PluginMessage brandMessage = this.user.getPendingConnection().getBrandMessage();
		if (brandMessage != null) {
			ch.write(brandMessage);
		}

		Set<String> registeredChannels = this.user.getPendingConnection().getRegisteredChannels();
		if (!registeredChannels.isEmpty()) {
			ch.write(new PluginMessage(this.user.getPendingConnection()
					.getVersion() >= ProtocolConstants.MINECRAFT_1_13 ? "minecraft:register" : "REGISTER", Joiner.on("\0")
					.join(registeredChannels).getBytes(StandardCharsets.UTF_8), false));
		}

		if (this.user.getSettings() != null) {
			ch.write(this.user.getSettings());
		}

		if (this.user.getForgeClientHandler().getClientModList() == null && !this.user.getForgeClientHandler().isHandshakeComplete()) { // Vanilla
			this.user.getForgeClientHandler().setHandshakeComplete();
		}

		var isFirstLogin = this.instance.isFirstLogin(this.user.getName());
		if (this.user.getServer() == null || !(login.getDimension() instanceof Integer) || isFirstLogin) {
			// Once again, first connection
			this.user.setClientEntityId(login.getEntityId());
			this.user.setServerEntityId(login.getEntityId());

			// Set tab list size, TODO: what shall we do about packet mutability
			// Set tab list size, TODO: what shall we do about packet mutability
			Login modLogin = new Login( login.getEntityId(), login.isHardcore(), login.getGameMode(), login.getPreviousGameMode(),
					login.getWorldNames(), login.getDimensions(), login.getDimension(), login.getWorldName(), login.getSeed(), 
					login.getDifficulty(), (byte) this.user.getPendingConnection().getListener().getTabListSize(), login.getLevelType(),
					login.getViewDistance(), login.getSimulationDistance(), login.isReducedDebugInfo(), login.isNormalRespawn(),
					login.isDebug(), login.isFlat(), login.getDeathLocation() );

			this.user.unsafe().sendPacket(modLogin);

			if (this.user.getServer() != null && !isFirstLogin) {
				
				this.user.getServer().setObsolete(true);
				this.user.getTabListHandler().onServerChange();

				this.user.getServerSentScoreboard().clear();

				for (UUID bossbar : this.user.getSentBossBars()) {
					// Send remove bossbar packet
					this.user.unsafe().sendPacket(new net.md_5.bungee.protocol.packet.BossBar(bossbar, 1));
				}
				this.user.getSentBossBars().clear();

				this.user.unsafe().sendPacket( new Respawn( login.getDimension(), login.getWorldName(), login.getSeed(), 
						login.getDifficulty(), login.getGameMode(), login.getPreviousGameMode(), login.getLevelType(), 
						login.isDebug(), login.isFlat(), false, login.getDeathLocation() ) );
				this.user.getServer().disconnect( "Quitting" );
			} else {
				ByteBuf brand = ByteBufAllocator.DEFAULT.heapBuffer();
				DefinedPacket.writeString(this.bungee.getName() + " (" + this.bungee.getVersion() + ")", brand);
				this.user.unsafe().sendPacket(new PluginMessage(this.user.getPendingConnection()
						.getVersion() >= ProtocolConstants.MINECRAFT_1_13 ? "minecraft:brand" : "MC|Brand", 
								DefinedPacket.toArray(brand), this.getHandshakeHandler().isServerForge()));
				brand.release();
			}

			this.user.setDimension(login.getDimension());
		} else {
			this.user.getServer().setObsolete(true);
			this.user.getTabListHandler().onServerChange();

			Scoreboard serverScoreboard = this.user.getServerSentScoreboard();
			for (Objective objective : serverScoreboard.getObjectives()) {
				this.user.unsafe().sendPacket(new ScoreboardObjective(objective.getName(), objective.getValue(), 
						ScoreboardObjective.HealthDisplay.fromString(objective.getType()), (byte) 1));
			}
			for (Score score : serverScoreboard.getScores()) {
				this.user.unsafe().sendPacket(new ScoreboardScore(score.getItemName(), (byte) 1, score.getScoreName(), score.getValue()));
			}
			for (Team team : serverScoreboard.getTeams()) {
				this.user.unsafe().sendPacket(new net.md_5.bungee.protocol.packet.Team(team.getName()));
			}
			serverScoreboard.clear();

			for (UUID bossbar : this.user.getSentBossBars()) {
				// Send remove bossbar packet
				this.user.unsafe().sendPacket(new net.md_5.bungee.protocol.packet.BossBar(bossbar, 1));
			}
			this.user.getSentBossBars().clear();

			// Update debug info from login packet
			this.user.unsafe().sendPacket(new EntityStatus(user.getClientEntityId(), 
					login.isReducedDebugInfo() ? EntityStatus.DEBUG_INFO_REDUCED : EntityStatus.DEBUG_INFO_NORMAL));
			// And immediate respawn
			if (this.user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_15) {
				this.user.unsafe().sendPacket(new GameState(GameState.IMMEDIATE_RESPAWN, login.isNormalRespawn() ? 0 : 1));
			}

			this.user.setDimensionChange(true);
			if (login.getDimension() == this.user.getDimension()) {
				this.user.unsafe().sendPacket( new Respawn( (Integer) login.getDimension() >= 0 ? -1 : 0, login.getWorldName(), 
						login.getSeed(), login.getDifficulty(), login.getGameMode(), login.getPreviousGameMode(), 
						login.getLevelType(), login.isDebug(), login.isFlat(), false, login.getDeathLocation() ) );
			}

			this.user.setServerEntityId(login.getEntityId());
			this.user.unsafe().sendPacket( new Respawn( login.getDimension(), login.getWorldName(), login.getSeed(), 
					login.getDifficulty(), login.getGameMode(), login.getPreviousGameMode(), login.getLevelType(), 
					login.isDebug(), login.isFlat(), false, login.getDeathLocation() ) );

			if (this.user.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_14) {
				this.user.unsafe().sendPacket(new ViewDistance(login.getViewDistance()));
			}
			this.user.setDimension(login.getDimension());

			// Remove from old servers
			this.user.getServer().disconnect("Quitting");
		}

		// TODO: Fix this?
		if (!this.user.isConnected()) {
			server.disconnect("Quitting");
			// Silly server admins see stack trace and die
			this.bungee.getLogger().warning("No client connected for pending server!");
			return;
		}

		// Add to new server
		// TODO: Move this to the connected() method of DownstreamBridge
		this.target.addPlayer(this.user);
		this.user.getPendingConnects().remove(this.target);
		this.user.setServerJoinQueue(null);
		this.user.setDimensionChange(false);

		ServerInfo from = (this.user.getServer() == null) ? null : this.user.getServer().getInfo();
		this.user.setServer(server);
		ch.getHandle().pipeline().get(HandlerBoss.class).setHandler(new CustomDownstreamBridge(this.instance, this.bungee, this.user, server));

		this.bungee.getPluginManager().callEvent(new ServerSwitchEvent(this.user, from));

		// Reflect.on(this).set("thisState", State.FINISHED);

		throw CancelSendSignal.INSTANCE;
	}

}
