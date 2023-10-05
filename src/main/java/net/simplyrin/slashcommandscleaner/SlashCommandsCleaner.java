package net.simplyrin.slashcommandscleaner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.RootCommandNode;

import lombok.Getter;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.TabCommandsEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.protocol.packet.Commands;

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

	@Getter
	private Configuration config;
	
	@Override
	public void onEnable() {
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
			config.set("denychildlist", Arrays.asList("help"));
			config.set("fakelist.default", Arrays.asList("help", "list", "me", "msg", "teammsg", "tell", "tm", "trigger", "w"));
			config.set("fakelist.member", Arrays.asList("++default", "time", "weather"));
			config.set("fakelist.plus", Arrays.asList("++member", "gamemode"));

			this.saveConfig(provider, config, file);
		}
		
		try {
			this.config = provider.load(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		this.getProxy().getPluginManager().registerListener(this, this);
	}
	
	public void saveConfig(ConfigurationProvider provider, Configuration config, File file) {
		try {
			provider.save(config, file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@EventHandler
	public void onTabCommands(TabCommandsEvent event) {
		var player = event.getPlayer();

		this.getLogger().info("[Slash-Commands] Packet detected: " + player.getName());
		
		if (player.hasPermission("slashcommandscleaner.bypass")) {
			return;
		}
		
		var root = event.getCommands().getRoot();

		event.clearCommands();
		
		boolean changed = false;
		
		List<String> list = new ArrayList<>();
		
		for (String key : this.config.getSection("fakelist").getKeys()) {
			if (player.hasPermission("slashcommandscleaner." + key)) {
				changed = true;

				this.addChild(player, list, key, event.getCommands(), root);
			}
		}
		
		if (changed) {
			return;
		}
		
		this.addChild(player, list, "default", event.getCommands(), root);
	}
	
	public void addChild(ProxiedPlayer player, List<String> added, String key, Commands commands, RootCommandNode root) {
		if (added.contains(key)) {
			return;
		}
		added.add(key);
		
		this.getLogger().info("[Slash-Commands] " + player.getName() + " -> " + key);
		
		var list = this.getConfig().getStringList("fakelist." + key);
		
		for (String command : list) {
			if (command.startsWith("++")) {
				this.addChild(player, added, command.substring(2), commands, root);
				continue;
			}
			
			if (commands.getRoot().getChild(command) != null) {
				continue;
			}
			
			var denyChildList = this.getConfig().getStringList("denychildlist");
			
			var child = root.getChild(command);
			if (child == null || denyChildList.contains(command)) {
				child = LiteralArgumentBuilder.literal(command).build();
			}

			commands.getRoot().addChild(child);
		}
	}

}
