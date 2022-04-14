package net.simplyrin.slashcommandscleaner;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.RootCommandNode;

import net.md_5.bungee.ServerConnection;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.connection.CancelSendSignal;
import net.md_5.bungee.connection.DownstreamBridge;
import net.md_5.bungee.protocol.packet.Commands;

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
public class CustomDownstreamBridge extends DownstreamBridge {
	
	private SlashCommandsCleaner instance;
	private UserConnection con;

	public CustomDownstreamBridge(SlashCommandsCleaner instance, ProxyServer bungee, UserConnection con, ServerConnection server) {
		super(bungee, con, server);
		
		this.instance = instance;
		this.con = con;
	}
	
	@Override
	public void handle(Commands commands) throws Exception {
		var player = (ProxiedPlayer) this.con;

		this.instance.getLogger().info("[Slash-Commands] Packet detected: " + player.getName());
		
		if (player.hasPermission("slashcommandscleaner.bypass")) {
			super.handle(commands);
			return;
		}
		
		var list = this.instance.getConfig().getStringList("FakeList");
		
		commands.setRoot(new RootCommandNode<Object>());
		for (String command : list) {
			var child = LiteralArgumentBuilder.literal(command).build();
			commands.getRoot().addChild(child);
		}
		
		this.instance.getLogger().info("[Slash-Commands] Cancelled: " + player.getName());
		
		this.con.unsafe().sendPacket(commands);
		
		throw CancelSendSignal.INSTANCE;
	}

}
