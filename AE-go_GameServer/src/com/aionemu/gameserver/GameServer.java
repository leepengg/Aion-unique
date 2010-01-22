/*
 * This file is part of aion-emu <aion-emu.com>.
 *
 * aion-emu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * aion-emu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with aion-emu.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver;

import org.apache.log4j.Logger;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.commons.log4j.exceptions.Log4jInitializationError;
import com.aionemu.commons.network.NioServer;
import com.aionemu.commons.services.LoggingService;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.dao.PlayerDAO;
import com.aionemu.gameserver.network.loginserver.LoginServer;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.QuestHandlersManager;
import com.aionemu.gameserver.services.ItemService;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.DeadlockDetector;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.ThreadUncaughtExceptionHandler;
import com.aionemu.gameserver.utils.Util;
import com.aionemu.gameserver.utils.gametime.GameTimeManager;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.zone.ZoneManager;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * <tt>GameServer</tt> is the main class of the application and represents the whole game server.<br>
 * This class is also an entry point with main() method.
 * 
 * @author -Nemesiss-
 * @author SoulKeeper
 */
public class GameServer
{
	/** Logger for gameserver */
	private static final Logger	log	= Logger.getLogger(GameServer.class);

	private Injector			injector; 

	/**
	 * Creates instance of GameServer, which includes loading static data, initializing world.
	 */
	private GameServer()
	{
		// Inits the injection module and injector itself
		// This will trigger creating singletons that are defined
		// in InjectionModule with asEagerSingleton() call
		DataInjectionModule dataIM = new DataInjectionModule();
		NetworkInjectionModule networkIM = new NetworkInjectionModule();
		
		injector = Guice.createInjector(dataIM,networkIM, new IDFactoriesInjectionModule());
		dataIM.setInjector(injector);
		networkIM.setInjector(injector);
	}

	/**
	 * Launching method for GameServer
	 * 
	 * @param args
	 *            arguments, not used
	 */
	public static void main(String[] args)
	{
		long start = System.currentTimeMillis();

		initUtilityServicesAndConfig();

		GameServer gs = new GameServer();
		//Set all players is offline
		DAOManager.getDAO(PlayerDAO.class).setPlayersOffline(false);
		gs.spawnMonsters();
		
		
		
		SkillEngine.getInstance().setWorld(gs.injector.getInstance(World.class));
		QuestEngine.getInstance().setItemService(gs.injector.getInstance(ItemService.class), gs.injector.getInstance(SpawnEngine.class));
		ZoneManager.getInstance().initializeZones();
		QuestHandlersManager.init(gs.injector);
		Util.printMemoryUsage(log);
		log.info("#################################################");
		log.info("AE Game Server started in " + (System.currentTimeMillis() - start) / 1000 + " seconds.");
		log.info("#################################################");
		
		gs.startServers();
		GameTimeManager.startClock();
		
		if(Config.DEADLOCK_DETECTOR_ENABLED)
		{
			log.info("Starting deadlock detector");
			new Thread(new DeadlockDetector(Config.DEADLOCK_DETECTOR_INTERVAL)).start();
		}
		
		
		Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook(gs.injector)));

		//gs.injector.getInstance(com.aionemu.gameserver.utils.chathandlers.ChatHandlers.class);
	}

	/**
	 * This method is basically responsible for triggering NPCs spawning with {@link SpawnEngine}.
	 */
	private void spawnMonsters()
	{
		SpawnEngine spawnEngine = injector.getInstance(SpawnEngine.class);
		spawnEngine.spawnAll();
	}

	/**
	 * Starts servers for connection with aion client and login server.
	 */
	private void startServers()
	{
		NioServer nioServer = injector.getInstance(NioServer.class);
		LoginServer loginServer = injector.getInstance(LoginServer.class);

		// Nio must go first
		nioServer.connect();
		loginServer.connect();
	}

	/**
	 * Initialize all helper services, that are not directly related to aion gs, which includes:
	 * <ul>
	 * <li>Logging</li>
	 * <li>Database factory</li>
	 * <li>Thread pool</li>
	 * </ul>
	 * 
	 * This method also initializes {@link Config}
	 * 
	 * @throws Log4jInitializationError
	 */
	private static void initUtilityServicesAndConfig() throws Log4jInitializationError
	{
		// Set default uncaught exception handler
		Thread.setDefaultUncaughtExceptionHandler(new ThreadUncaughtExceptionHandler());
		// First of all we must initialize logging
		LoggingService.init();
		// init config
		Config.load();
		// Second should be database factory
		DatabaseFactory.init();
		// Initialize DAOs
		DAOManager.init();
		// Initialize thread pools
		ThreadPoolManager.getInstance();
	}
}
