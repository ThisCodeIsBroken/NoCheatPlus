package fr.neatmonster.nocheatplus;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.blockbreak.BlockBreakListener;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractListener;
import fr.neatmonster.nocheatplus.checks.blockplace.BlockPlaceListener;
import fr.neatmonster.nocheatplus.checks.chat.ChatListener;
import fr.neatmonster.nocheatplus.checks.combined.CombinedListener;
import fr.neatmonster.nocheatplus.checks.fight.FightListener;
import fr.neatmonster.nocheatplus.checks.inventory.InventoryListener;
import fr.neatmonster.nocheatplus.checks.moving.MovingListener;
import fr.neatmonster.nocheatplus.clients.ModUtil;
import fr.neatmonster.nocheatplus.command.CommandHandler;
import fr.neatmonster.nocheatplus.compat.DefaultComponentFactory;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.MCAccessFactory;
import fr.neatmonster.nocheatplus.components.ComponentRegistry;
import fr.neatmonster.nocheatplus.components.ComponentWithName;
import fr.neatmonster.nocheatplus.components.ConsistencyChecker;
import fr.neatmonster.nocheatplus.components.IHoldSubComponents;
import fr.neatmonster.nocheatplus.components.INeedConfig;
import fr.neatmonster.nocheatplus.components.INotifyReload;
import fr.neatmonster.nocheatplus.components.JoinLeaveListener;
import fr.neatmonster.nocheatplus.components.MCAccessHolder;
import fr.neatmonster.nocheatplus.components.NCPListener;
import fr.neatmonster.nocheatplus.components.NameSetPermState;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.PermStateReceiver;
import fr.neatmonster.nocheatplus.components.TickListener;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.config.DefaultConfig;
import fr.neatmonster.nocheatplus.event.IHaveMethodOrder;
import fr.neatmonster.nocheatplus.event.ListenerManager;
import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;
import fr.neatmonster.nocheatplus.logging.LogUtil;
import fr.neatmonster.nocheatplus.logging.StaticLogFile;
import fr.neatmonster.nocheatplus.metrics.Metrics;
import fr.neatmonster.nocheatplus.metrics.Metrics.Graph;
import fr.neatmonster.nocheatplus.metrics.Metrics.Plotter;
import fr.neatmonster.nocheatplus.metrics.MetricsData;
import fr.neatmonster.nocheatplus.permissions.PermissionUtil;
import fr.neatmonster.nocheatplus.permissions.PermissionUtil.CommandProtectionEntry;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.updates.Updates;
import fr.neatmonster.nocheatplus.utilities.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.OnDemandTickListener;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/*
 * M"""""""`YM          MM'""""'YMM dP                           dP   MM"""""""`YM dP                   
 * M  mmmm.  M          M' .mmm. `M 88                           88   MM  mmmmm  M 88                   
 * M  MMMMM  M .d8888b. M  MMMMMooM 88d888b. .d8888b. .d8888b. d8888P M'        .M 88 dP    dP .d8888b. 
 * M  MMMMM  M 88'  `88 M  MMMMMMMM 88'  `88 88ooood8 88'  `88   88   MM  MMMMMMMM 88 88    88 Y8ooooo. 
 * M  MMMMM  M 88.  .88 M. `MMM' .M 88    88 88.  ... 88.  .88   88   MM  MMMMMMMM 88 88.  .88       88 
 * M  MMMMM  M `88888P' MM.     .dM dP    dP `88888P' `88888P8   dP   MM  MMMMMMMM dP `88888P' `88888P' 
 * MMMMMMMMMMM          MMMMMMMMMMM                                   MMMMMMMMMMMM                      
 */
/**
 * This is the main class of NoCheatPlus. The commands, events listeners and tasks are registered here.
 */
public class NoCheatPlus extends JavaPlugin implements NoCheatPlusAPI {
	
	//////////////////
	// Static API
	//////////////////
	
	/**
	 * Convenience method.
	 * @deprecated Use fr.neatmonster.nocheatplus.utilities.NCPAPIProvider.getNoCheatPlusAPI() instead, this method might get removed.
	 * @return
	 */
	public static NoCheatPlusAPI getAPI() {
		return NCPAPIProvider.getNoCheatPlusAPI();
	}
	
	//////////////////
	// Not static.
	//////////////////
	
	/** Names of players with a certain permission. */
	protected final NameSetPermState nameSetPerms = new NameSetPermState(Permissions.ADMINISTRATION_NOTIFY);
	
	/** Lower case player name to milliseconds point of time of release */
	private final Map<String, Long> denyLoginNames = Collections.synchronizedMap(new HashMap<String, Long>());
	
	/** MCAccess instance. */
	protected MCAccess mcAccess = null;

    /** Is the configuration outdated? */
    private boolean              configOutdated  = false;

//    /** Is a new update available? */
//    private boolean              updateAvailable = false;
    
    /** Player data future stuff. */
    protected final DataManager dataMan = new DataManager();
    
	private int dataManTaskId = -1;
	
	protected Metrics metrics = null;
    
	/**
	 * Commands that were changed for protecting them against tab complete or
	 * use.
	 */
	protected List<CommandProtectionEntry> changedCommands = null;
	
	
	private final ListenerManager listenerManager = new ListenerManager(this, false);
	
	private boolean manageListeners = true;
	
	/** The event listeners. */
    private final List<Listener> listeners       = new ArrayList<Listener>();
    
    /** Components that need notification on reloading.
     * (Kept here, for if during runtime some might get added.)*/
    private final List<INotifyReload> notifyReload = new LinkedList<INotifyReload>();
	
	/** Permission states stored on a per-world basis, updated with join/quit/kick.  */
	protected final List<PermStateReceiver> permStateReceivers = new ArrayList<PermStateReceiver>();
	
	/** Components that check consistency. */
	protected final List<ConsistencyChecker> consistencyCheckers = new ArrayList<ConsistencyChecker>();
	
	/** Index at which to continue. */
	protected int consistencyCheckerIndex = 0;
	
	protected int consistencyCheckerTaskId = -1;
	
	/** Listeners for players joining and leaving (monitor level) */
	protected final List<JoinLeaveListener> joinLeaveListeners = new ArrayList<JoinLeaveListener>();
	
	/** Sub component registries. */
	protected final List<ComponentRegistry<?>> subRegistries = new ArrayList<ComponentRegistry<?>>();
	
	/** Queued sub component holders, emptied on the next tick usually. */
	protected final List<IHoldSubComponents> subComponentholders = new ArrayList<IHoldSubComponents>(20);
	
	/** All registered components.  */
	protected Set<Object> allComponents = new LinkedHashSet<Object>(50);
	
	/** Tick listener that is only needed sometimes (component registration). */
	protected final OnDemandTickListener onDemandTickListener = new OnDemandTickListener() {
		@Override
		public boolean delegateTick(final int tick, final long timeLast) {
			processQueuedSubComponentHolders();
			return false;
		}
	};

	/**
	 * Remove expired entries.
	 */
    private void checkDenyLoginsNames() {
		final long ts = System.currentTimeMillis();
		final List<String> rem = new LinkedList<String>();
		synchronized (denyLoginNames) {
			for (final Entry<String, Long> entry : denyLoginNames.entrySet()){
				if (entry.getValue().longValue() < ts)  rem.add(entry.getKey());
			}
			for (final String name : rem){
				denyLoginNames.remove(name);
			}
		}
	}
    
    @Override
    public boolean allowLogin(String playerName){
    	playerName = playerName.trim().toLowerCase();
    	final Long time = denyLoginNames.remove(playerName);
    	if (time == null) return false;
    	return System.currentTimeMillis() <= time;
    }
    
    @Override
    public int allowLoginAll(){
    	int denied = 0;
    	final long now = System.currentTimeMillis();
    	for (final String playerName : denyLoginNames.keySet()){
    		final Long time = denyLoginNames.get(playerName);
        	if (time != null && time > now) denied ++;
    	}
    	denyLoginNames.clear();
    	return denied;
    }
    
    @Override
	public void denyLogin(String playerName, long duration){
		final long ts = System.currentTimeMillis() + duration;
		playerName = playerName.trim().toLowerCase();
		synchronized (denyLoginNames) {
			final Long oldTs = denyLoginNames.get(playerName);
			if (oldTs != null && ts < oldTs.longValue()) return;
			denyLoginNames.put(playerName, ts);
			// TODO: later maybe save these ?
		}
		checkDenyLoginsNames();
	}
	
    @Override
	public boolean isLoginDenied(String playerName){
		return isLoginDenied(playerName, System.currentTimeMillis());
	}
	
    @Override
	public String[] getLoginDeniedPlayers() {
		checkDenyLoginsNames();
		String[] kicked = new String[denyLoginNames.size()];
		denyLoginNames.keySet().toArray(kicked);
		return kicked;
	}

    @Override
	public boolean isLoginDenied(String playerName, long time) {
		playerName = playerName.trim().toLowerCase();
		final Long oldTs = denyLoginNames.get(playerName);
		if (oldTs == null) return false; 
		else return time < oldTs.longValue();
	}
	
    @Override
	public int sendAdminNotifyMessage(final String message){
		if (ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_BACKEND_INGAMECHAT_SUBSCRIPTIONS)){
			// TODO: Might respect console settings, or add extra config section (e.g. notifications).
			return sendAdminNotifyMessageSubscriptions(message);
		}
		else{
			return sendAdminNotifyMessageStored(message);
		}
	}
	
	/**
	 * Send notification to players with stored notify-permission (world changes, login, permissions are not re-checked here). 
	 * @param message
	 * @return
	 */
	public int sendAdminNotifyMessageStored(final String message){
		final Set<String> names = nameSetPerms.getPlayers(Permissions.ADMINISTRATION_NOTIFY);
		if (names == null) return 0;
		int done = 0;
		for (final String name : names){
			final Player player = DataManager.getPlayerExact(name);
			if (player != null){
				player.sendMessage(message);
				done ++;
			}
		}
		return done;
	}
	
	/**
	 * Send notification to all CommandSenders found in permission subscriptions for the notify-permission as well as players that have stored permissions (those get re-checked here).
	 * @param message
	 * @return
	 */
	public int sendAdminNotifyMessageSubscriptions(final String message){
		final Set<Permissible> permissibles = Bukkit.getPluginManager().getPermissionSubscriptions(Permissions.ADMINISTRATION_NOTIFY);
		final Set<String> names = nameSetPerms.getPlayers(Permissions.ADMINISTRATION_NOTIFY);
		final Set<String> done = new HashSet<String>(permissibles.size() + (names == null ? 0 : names.size()));
		for (final Permissible permissible : permissibles){
			if (permissible instanceof CommandSender && permissible.hasPermission(Permissions.ADMINISTRATION_NOTIFY)){
				final CommandSender sender = (CommandSender) permissible;
				sender.sendMessage(message);
				done.add(sender.getName());
			}
		}
		// Fall-back checking for players.
		if (names != null){
			for (final String name : names){
				if (!done.contains(name)){
					final Player player = DataManager.getPlayerExact(name);
					if (player != null && player.hasPermission(Permissions.ADMINISTRATION_NOTIFY)){
						player.sendMessage(message); 
						done.add(name);
					}
				}
			}
		}
		return done.size();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> Collection<ComponentRegistry<T>> getComponentRegistries(final Class<ComponentRegistry<T>> clazz) {
		final List<ComponentRegistry<T>> result = new LinkedList<ComponentRegistry<T>>();
		for (final ComponentRegistry<?> registry : subRegistries){
			if (clazz.isAssignableFrom(registry.getClass())){
				try{
					result.add((ComponentRegistry<T>) registry);
				}
				catch(Throwable t){
					// Ignore.
				}
			}
		}
		return result;
	}

	/**
	 * Convenience method to add components according to implemented interfaces,
     * like Listener, INotifyReload, INeedConfig.<br>
     * For the NoCheatPlus instance this must be done after the configuration has been initialized.
     * This will also register ComponentRegistry instances if given.
	 */
	@Override
	public boolean addComponent(final Object obj) {
		return addComponent(obj, true);
	}
	
	/**
	 * Convenience method to add components according to implemented interfaces,
     * like Listener, INotifyReload, INeedConfig.<br>
     * For the NoCheatPlus instance this must be done after the configuration has been initialized.
     * @param allowComponentRegistry Only registers ComponentRegistry instances if this is set to true. 
	 */
	@Override
	public boolean addComponent(final Object obj, final boolean allowComponentRegistry) {
		
		// TODO: Allow to add ComponentFactory + contract (renew with reload etc.)?
		if (obj == this) throw new IllegalArgumentException("Can not register NoCheatPlus with itself.");
		
		if (allComponents.contains(obj)){
			// All added components are in here.
			return false;
		}
		boolean added = false;
		if (obj instanceof Listener) {
			addListener((Listener) obj);
			added = true;
		}
		if (obj instanceof INotifyReload) {
			notifyReload.add((INotifyReload) obj);
			if (obj instanceof INeedConfig) {
				((INeedConfig) obj).onReload();
			}
			added = true;
		}
		if (obj instanceof TickListener){
			TickTask.addTickListener((TickListener) obj);
			added = true;
		}
		if (obj instanceof PermStateReceiver){
			// No immediate update done.
			permStateReceivers.add((PermStateReceiver) obj);
			added = true;
		}
		if (obj instanceof MCAccessHolder){
			// These will get notified in initMcAccess (iterates over allComponents).
			((MCAccessHolder) obj).setMCAccess(getMCAccess());
			added = true;
		}
		if (obj instanceof ConsistencyChecker){
			consistencyCheckers.add((ConsistencyChecker) obj);
			added = true;
		}
		if (obj instanceof JoinLeaveListener){
			joinLeaveListeners.add((JoinLeaveListener) obj);
			added = true;
		}
		
		// Add to sub registries.
		for (final ComponentRegistry<?> registry : subRegistries){
			final Object res = ReflectionUtil.invokeGenericMethodOneArg(registry, "addComponent", obj);
			if (res != null && (res instanceof Boolean) && ((Boolean) res).booleanValue()){
				added = true;
			}
		}
		
		// Add ComponentRegistry instances after adding to sub registries to prevent adding it to itself.
		if (allowComponentRegistry && (obj instanceof ComponentRegistry<?>)){
			subRegistries.add((ComponentRegistry<?>) obj);
			added = true;
		}
		
		// Components holding more components to register later.
		if (obj instanceof IHoldSubComponents){
			subComponentholders.add((IHoldSubComponents) obj);
			onDemandTickListener.register();
			added = true; // Convention.
		}
		
		// Add to allComponents if in fact added.
		if (added) allComponents.add(obj);
		return added;
	}
	
	/**
	 * Interfaces checked for managed listeners: IHaveMethodOrder (method), ComponentWithName (tag)<br>
	 * @param listener
	 */
	private void addListener(final Listener listener) {
		// private: Use addComponent.
		if (manageListeners){
			String tag = "NoCheatPlus";
			if (listener instanceof ComponentWithName){
				tag = ((ComponentWithName) listener).getComponentName();
			}
			listenerManager.registerAllEventHandlers(listener, tag);
			listeners.add(listener);
		}
		else{
			Bukkit.getPluginManager().registerEvents(listener, this);
			if (listener instanceof IHaveMethodOrder){
				// TODO: Might log the order too, might prevent registration ?
				// TODO: Alternative: queue listeners and register after startup (!)
				LogUtil.logWarning("[NoCheatPlus] Listener demands registration order, but listeners are not managed: " + listener.getClass().getName());
			}
		}
	}
	
	/**
	 * Test if NCP uses the ListenerManager at all.
	 * @return If so.
	 */
	public boolean doesManageListeners(){
		return manageListeners;
	}

	@Override
	public void removeComponent(final Object obj) {
		if (obj instanceof Listener){
			listeners.remove(obj);
			listenerManager.remove((Listener) obj);
		}
		if (obj instanceof PermStateReceiver){
			permStateReceivers.remove((PermStateReceiver) obj);
		}
		if (obj instanceof TickListener){
			TickTask.removeTickListener((TickListener) obj);
		}
		if (obj instanceof INotifyReload) {
			notifyReload.remove(obj);
		}
		if (obj instanceof ConsistencyChecker){
			consistencyCheckers.remove(obj);
		}
		if (obj instanceof JoinLeaveListener){
			joinLeaveListeners.remove((JoinLeaveListener) obj);
		}
		
		// Remove sub registries.
		if (obj instanceof ComponentRegistry<?>){
			subRegistries.remove(obj);
		}
		// Remove from present registries, order prevents to remove from itself.
		for (final ComponentRegistry<?> registry : subRegistries){
			ReflectionUtil.invokeGenericMethodOneArg(registry, "removeComponent", obj);
		}

		allComponents.remove(obj);
	}
    
    /* (non-Javadoc)
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
        /*
         *  ____  _           _     _      
         * |  _ \(_)___  __ _| |__ | | ___ 
         * | | | | / __|/ _` | '_ \| |/ _ \
         * | |_| | \__ \ (_| | |_) | |  __/
         * |____/|_|___/\__,_|_.__/|_|\___|
         */
        
    	final boolean verbose = ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_DEBUG);
    	
		// Remove listener references.
    	if (verbose){
    		if (listenerManager.hasListenerMethods()) LogUtil.logInfo("[NoCheatPlus] Cleanup ListenerManager...");
    		else LogUtil.logInfo("[NoCheatPlus] (ListenerManager not in use, prevent registering...)");
    	}
		listenerManager.setRegisterDirectly(false);
		listenerManager.clear();
		
		BukkitScheduler sched = getServer().getScheduler();
		
		// Stop data-man task.
		if (dataManTaskId != -1){
			sched.cancelTask(dataManTaskId);
			dataManTaskId = -1;
		}
        
        // Stop the tickTask.
		if (verbose) LogUtil.logInfo("[NoCheatPlus] Stop TickTask...");
        TickTask.setLocked(true);
        TickTask.purge();
        TickTask.cancel();
        TickTask.removeAllTickListeners();
        // (Keep the tick task locked!)
        
		// Stop metrics task.
		if (metrics != null){
			if (verbose) LogUtil.logInfo("[NoCheatPlus] Stop Metrics...");
			metrics.cancel();
			metrics = null;
		}
		
		// Stop consistency checking task.
		if (consistencyCheckerTaskId != -1){
			sched.cancelTask(consistencyCheckerTaskId);
		}
        
        // Just to be sure nothing gets left out.
        if (verbose) LogUtil.logInfo("[NoCheatPlus] Stop all remaining tasks...");
        sched.cancelTasks(this);

        // Exemptions cleanup.
        if (verbose) LogUtil.logInfo("[NoCheatPlus] Reset ExemptionManager...");
        NCPExemptionManager.clear();
        
		// Data cleanup.
		if (verbose) LogUtil.logInfo("[NoCheatPlus] Cleanup DataManager...");
		dataMan.onDisable();
		
		// Hooks:
		// (Expect external plugins to unregister their hooks on their own.)
		// (No native hooks present, yet.)
     	
		// Unregister all added components explicitly.
		if (verbose) LogUtil.logInfo("[NoCheatPlus] Unregister all registered components...");
		final ArrayList<Object> allComponents = new ArrayList<Object>(this.allComponents);
        for (int i = allComponents.size() - 1; i >= 0; i--){
        	removeComponent(allComponents.get(i));
        }
        
        // Cleanup BlockProperties.
        if (verbose) LogUtil.logInfo("[NoCheatPlus] Cleanup BlockProperties...");
        BlockProperties.cleanup();
        
        if (verbose) LogUtil.logInfo("[NoCheatPlus] Cleanup some mappings...");
        // Remove listeners.
        listeners.clear();
        // Remove config listeners.
        notifyReload.clear();
        // World specific permissions.
		permStateReceivers.clear();
		// Sub registries.
		subRegistries.clear();
		// Just in case: clear the subComponentHolders.
		subComponentholders.clear();

		// Clear command changes list (compatibility issues with NPCs, leads to recalculation of perms).
		if (changedCommands != null){
			changedCommands.clear();
			changedCommands = null;
		}
//		// Restore changed commands.
//		if (verbose) LogUtil.logInfo("[NoCheatPlus] Undo command changes...");
//		undoCommandChanges();
		
		// Cleanup the configuration manager.
		if (verbose) LogUtil.logInfo("[NoCheatPlus] Cleanup ConfigManager...");
		ConfigManager.cleanup();
		
		// Cleanup file logger.
		if (verbose) LogUtil.logInfo("[NoCheatPlus] Cleanup file logger...");
		StaticLogFile.cleanup();

		// Tell the server administrator the we finished unloading NoCheatPlus.
		if (verbose) LogUtil.logInfo("[NoCheatPlus] All cleanup done.");
		final PluginDescriptionFile pdfFile = getDescription();
		LogUtil.logInfo("[NoCheatPlus] Version " + pdfFile.getVersion() + " is disabled.");
	}

	/**
	 * Does not undo 100%, but restore old permission, permission-message, label (unlikely to be changed), permission default.
	 * @deprecated Leads to compatibility issues with NPC plugins such as Citizens 2, due to recalculation of permissions (specifically during disabling).
	 */
	public void undoCommandChanges() {
		if (changedCommands != null){
			while (!changedCommands.isEmpty()){
				final CommandProtectionEntry entry = changedCommands.remove(changedCommands.size() - 1);
				entry.restore();
			}
			changedCommands = null;
		}
	}
	
	protected void setupCommandProtection() {
		final List<CommandProtectionEntry> changedCommands = PermissionUtil.protectCommands(
				Arrays.asList("plugins", "version", "icanhasbukkit"), "nocheatplus.feature.command", false);
		if (this.changedCommands == null) this.changedCommands = changedCommands;
		else this.changedCommands.addAll(changedCommands);
	}	

	/* (non-Javadoc)
	 * @see org.bukkit.plugin.java.JavaPlugin#onLoad()
	 */
	@Override
	public void onLoad() {
		NCPAPIProvider.setNoCheatPlusAPI(this);
		// TODO: Can not set to null in onDisable...
		super.onLoad(); // TODO: Check what this does / order.
	}

	/* (non-Javadoc)
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        /*
         *  _____             _     _      
         * | ____|_ __   __ _| |__ | | ___ 
         * |  _| | '_ \ / _` | '_ \| |/ _ \
         * | |___| | | | (_| | |_) | |  __/
         * |_____|_| |_|\__,_|_.__/|_|\___|
         */
    	
    	// Reset TickTask (just in case).
    	TickTask.setLocked(true);
    	TickTask.purge();
    	TickTask.cancel();
    	TickTask.reset();
    	
        // Read the configuration files.
        ConfigManager.init(this);
        
        // Setup file logger.
        StaticLogFile.setupLogger(new File(getDataFolder(), ConfigManager.getConfigFile().getString(ConfPaths.LOGGING_BACKEND_FILE_FILENAME)));
        
        final ConfigFile config = ConfigManager.getConfigFile();
        
        // Initialize MCAccess.
        initMCAccess(config);
        
        // Initialize BlockProperties.
		initBlockProperties(config);
		
		// Initialize data manager.
		dataMan.onEnable();
        
        // Allow entries to TickTask (just in case).
        TickTask.setLocked(false);

		// List the events listeners and register.
		manageListeners = config.getBoolean(ConfPaths.MISCELLANEOUS_MANAGELISTENERS);
		if (manageListeners) {
			listenerManager.setRegisterDirectly(true);
			listenerManager.registerAllWithBukkit();
		}
		else{
			// Just for safety.
			listenerManager.setRegisterDirectly(false);
			listenerManager.clear();
		}
		
		// Add the "low level" system components first.
		for (final Object obj : new Object[]{
				nameSetPerms,
				getCoreListener(),
				// Put ReloadListener first, because Checks could also listen to it.
	        	new INotifyReload() {
	        		// Only for reloading, not INeedConfig.
					@Override
					public void onReload() {
						processReload();
					}
	        	},
	        	NCPExemptionManager.getListener(),
	        	new ConsistencyChecker() {
					@Override
					public void checkConsistency(final Player[] onlinePlayers) {
						NCPExemptionManager.checkConsistency(onlinePlayers);
					}
				},
	        	dataMan,
		}){
			addComponent(obj);
			// Register sub-components (allow later added to use registries, if any).
            processQueuedSubComponentHolders();
		}
        
		// Register "higher level" components (check listeners).
        for (final Object obj : new Object[]{
        	new BlockInteractListener(),
        	new BlockBreakListener(),
        	new BlockPlaceListener(),
        	new ChatListener(),
        	new CombinedListener(),
        	// Do mind registration order: Combined must come before Fight.
        	new FightListener(),
        	new InventoryListener(),
        	new MovingListener(),
        }){
        	addComponent(obj);
            // Register sub-components (allow later added to use registries, if any).
            processQueuedSubComponentHolders();
        }
        
        // Register optional default components.
        final DefaultComponentFactory dcf = new DefaultComponentFactory();
        for (final Object obj : dcf.getAvailableComponentsOnEnable()){
        	addComponent(obj);
        	// Register sub-components to enable registries for optional components.
            processQueuedSubComponentHolders();
        }
        
        // Register the commands handler.
        PluginCommand command = getCommand("nocheatplus");
        CommandHandler commandHandler = new CommandHandler(this, notifyReload);
        command.setExecutor(commandHandler);
        // (CommandHandler is TabExecutor.)
        
        // Set up the tick task.
        TickTask.start(this);
        
        this.dataManTaskId  = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override
			public void run() {
				dataMan.checkExpiration();
			}
		}, 1207, 1207);
        
        // Set up consistency checking.
        scheduleConsistencyCheckers();

        
        // Setup the graphs, plotters and start Metrics.
        if (config.getBoolean(ConfPaths.MISCELLANEOUS_REPORTTOMETRICS)) {
            MetricsData.initialize();
            try {
                this.metrics = new Metrics(this);
                final Graph checksFailed = metrics.createGraph("Checks Failed");
                for (final CheckType type : CheckType.values())
                    if (type.getParent() != null)
                        checksFailed.addPlotter(new Plotter(type.name()) {
                            @Override
                            public int getValue() {
                                return MetricsData.getFailed(type);
                            }
                        });
                final Graph serverTicks = metrics.createGraph("Server Ticks");
                final int[] ticksArray = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                        19, 20};
                for (final int ticks : ticksArray)
                    serverTicks.addPlotter(new Plotter(ticks + " tick(s)") {
                        @Override
                        public int getValue() {
                            return MetricsData.getTicks(ticks);
                        }
                    });
                metrics.start();
            } catch (final Exception e) {
            	LogUtil.logWarning("[NoCheatPlus] Failed to initialize metrics:");
            	LogUtil.logWarning(e);
            	if (metrics != null){
            		metrics.cancel();
            		metrics = null;
            	}
            }
        }

//        if (config.getBoolean(ConfPaths.MISCELLANEOUS_CHECKFORUPDATES)){
//            // Is a new update available?
//        	final int timeout = config.getInt(ConfPaths.MISCELLANEOUS_UPDATETIMEOUT, 4) * 1000;
//        	getServer().getScheduler().scheduleAsyncDelayedTask(this, new Runnable() {
//				@Override
//				public void run() {
//					updateAvailable = Updates.checkForUpdates(getDescription().getVersion(), timeout);
//				}
//			});
//        }

        // Is the configuration outdated?
        configOutdated = Updates.isConfigOutdated(DefaultConfig.buildNumber, config);
        
		if (config.getBoolean(ConfPaths.MISCELLANEOUS_PROTECTPLUGINS)) {
			Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
				@Override
				public void run() {
					setupCommandProtection();
				}
			}); 
		}
		
		// Care for already online players.
		final Player[] onlinePlayers = getServer().getOnlinePlayers();
		// TODO: remap exemptionmanager !
		// TODO: Disable all checks for these players for one tick !
		// TODO: Prepare check data for players [problem: permissions]?
		Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override
			public void run() {
				postEnable(onlinePlayers);
			}
		});

		// Tell the server administrator that we finished loading NoCheatPlus now.
		LogUtil.logInfo("[NoCheatPlus] Version " + getDescription().getVersion() + " is enabled.");
	}
    
    /**
     * Empties and registers the subComponentHolders list.
     */
    protected void processQueuedSubComponentHolders(){
    	if (subComponentholders.isEmpty()) return;
    	final List<IHoldSubComponents> copied = new ArrayList<IHoldSubComponents>(subComponentholders);
    	subComponentholders.clear();
    	for (final IHoldSubComponents holder : copied){
    		for (final Object component : holder.getSubComponents()){
    			addComponent(component);
    		}
    	}
    }
    
    /**
     * All action done on reload.
     */
    protected void processReload(){
    	final ConfigFile config = ConfigManager.getConfigFile();
    	// TODO: Process registered ComponentFactory instances.
		// Set up MCAccess.
		initMCAccess(config);
		// Initialize BlockProperties
		initBlockProperties(config);
		// Reset Command protection.
		undoCommandChanges();
		if (config.getBoolean(ConfPaths.MISCELLANEOUS_PROTECTPLUGINS)) setupCommandProtection();
		// (Re-) schedule consistency checking.
		scheduleConsistencyCheckers();
    }
    
	@Override
	public MCAccess getMCAccess(){
		if (mcAccess == null) initMCAccess();
		return mcAccess;
	}
	
	/**
	 * Fall-back method to initialize from factory, only if not yet set. Uses the BukkitScheduler to ensure this works if called from async checks.
	 */
	private void initMCAccess() {
		getServer().getScheduler().callSyncMethod(this, new Callable<MCAccess>() {
			@Override
			public MCAccess call() throws Exception {
				if (mcAccess != null) return mcAccess;
				return initMCAccess(ConfigManager.getConfigFile());
			}
		});
	}
    
    /**
     * Re-setup MCAccess from internal factory and pass it to MCAccessHolder components, only call from the main thread.
     * @param config
     */
    public MCAccess initMCAccess(final ConfigFile config) {
    	// Reset MCAccess.
    	// TODO: Might fire a NCPSetMCAccessFromFactoryEvent (include getting and setting)!
    	final MCAccess mcAccess = new MCAccessFactory().getMCAccess(config.getBoolean(ConfPaths.COMPATIBILITY_BUKKITONLY));
    	setMCAccess(mcAccess);
    	return mcAccess;
	}
    
    /**
     * Set and propagate to registered MCAccessHolder instances.
     */
    @Override
    public void setMCAccess(final MCAccess mcAccess){
    	// Just sets it and propagates it.
    	// TODO: Might fire a NCPSetMCAccessEvent (include getting and setting)!
    	this.mcAccess = mcAccess;
    	for (final Object obj : this.allComponents){
    		if (obj instanceof MCAccessHolder){
    			try{
    				((MCAccessHolder) obj).setMCAccess(mcAccess);
    			} catch(Throwable t){
    				LogUtil.logSevere("[NoCheatPlus] MCAccessHolder(" + obj.getClass().getName() + ") failed to set MCAccess: " + t.getClass().getSimpleName());
    				LogUtil.logSevere(t);
    			}
    		}
    	}
		LogUtil.logInfo("[NoCheatPlus] McAccess set to: " + mcAccess.getMCVersion() + " / " + mcAccess.getServerVersionTag());
    }

	/**
     * Initialize BlockProperties, including config.
     */
    protected void initBlockProperties(ConfigFile config){
        // Set up BlockProperties.
        BlockProperties.init(getMCAccess(), ConfigManager.getWorldConfigProvider());
        BlockProperties.applyConfig(config, ConfPaths.COMPATIBILITY_BLOCKS);
        // Schedule dumping the blocks properties (to let other plugins override).
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override
			public void run() {
		        // Debug information about unknown blocks.
		        // (Probably removed later.)
				ConfigFile config = ConfigManager.getConfigFile();
		        BlockProperties.dumpBlocks(config.getBoolean(ConfPaths.BLOCKBREAK_FASTBREAK_DEBUG, config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false))));
			}
		});
    }
    
    /**
     * Actions to be done after enable of  all plugins. This aims at reloading mainly.
     */
    private void postEnable(final Player[] onlinePlayers){
    	for (final Player player : onlinePlayers){
    		updatePermStateReceivers(player);
    		NCPExemptionManager.registerPlayer(player);
    	}
    	// TODO: if (online.lenght > 0) LogUtils.logInfo("[NCP] Updated " + online.length + "players (post-enable).")
    }
    
    /**
	 * Quick solution to hide the listener methods, expect refactoring.
	 * @return
	 */
	private Listener getCoreListener() {
		return new NCPListener() {
			@EventHandler(priority = EventPriority.NORMAL)
			public void onPlayerLogin(final PlayerLoginEvent event) {
				// (NORMAL to have chat checks come after this.)
				if (event.getResult() != Result.ALLOWED) return;
				final Player player = event.getPlayer();
				// Check if login is denied:
				checkDenyLoginsNames();
				if (player.hasPermission(Permissions.BYPASS_DENY_LOGIN)) return;
				if (isLoginDenied(player.getName())) {
					// TODO: display time for which the player is banned.
					event.setResult(Result.KICK_OTHER);
					// TODO: Make message configurable.
					event.setKickMessage("You are temporarily denied to join this server.");
				}
			}

			@EventHandler(priority = EventPriority.MONITOR)
			public void onPlayerJoin(final PlayerJoinEvent event) {
				onJoin(event.getPlayer());
			}

			@EventHandler(priority = EventPriority.MONITOR)
			public void onPlayerchangedWorld(final PlayerChangedWorldEvent event)
			{
				final Player player = event.getPlayer();
				updatePermStateReceivers(player);
			}

			@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
			public void onPlayerKick(final PlayerKickEvent event) {
				onLeave(event.getPlayer());
			}

			@EventHandler(priority = EventPriority.MONITOR)
			public void onPlayerQuitMonitor(final PlayerQuitEvent event) {
				onLeave(event.getPlayer());
			}
		};
	}
	
	protected void onJoin(final Player player){
		updatePermStateReceivers(player);
		
		if (nameSetPerms.hasPermission(player.getName(), Permissions.ADMINISTRATION_NOTIFY)){
			// Login notifications...
			
//			// Update available.
//			if (updateAvailable) player.sendMessage(ChatColor.RED + "NCP: " + ChatColor.WHITE + "A new update of NoCheatPlus is available.\n" + "Download it at http://nocheatplus.org/update");
			
			// Outdated config.
			if (configOutdated) player.sendMessage(ChatColor.RED + "NCP: " + ChatColor.WHITE + "Your configuration might be outdated.\n" + "Some settings could have changed, you should regenerate it!");

		}
		ModUtil.motdOnJoin(player);
		for (final JoinLeaveListener jlListener : joinLeaveListeners){
			try{
				jlListener.playerJoins(player);
			}
			catch(Throwable t){
				LogUtil.logSevere("[NoCheatPlus] JoinLeaveListener(" + jlListener.getClass().getName() + ") generated an exception (join): " + t.getClass().getSimpleName());
				LogUtil.logSevere(t);
			}
		}
	}
	
	protected void onLeave(final Player player) {
		for (final PermStateReceiver pr : permStateReceivers) {
			pr.removePlayer(player.getName());
		}
		for (final JoinLeaveListener jlListener : joinLeaveListeners){
			try{
				jlListener.playerLeaves(player);
			}
			catch(Throwable t){
				LogUtil.logSevere("[NoCheatPlus] JoinLeaveListener(" + jlListener.getClass().getName() + ") generated an exception (leave): " + t.getClass().getSimpleName());
				LogUtil.logSevere(t);
			}
		}
	}
	
	protected void updatePermStateReceivers(final Player player) {
		final Map<String, Boolean> checked = new HashMap<String, Boolean>(20);
		final String name = player.getName();
		for (final PermStateReceiver pr : permStateReceivers) {
			for (final String permission : pr.getDefaultPermissions()) {
				Boolean state = checked.get(permission);
				if (state == null) {
					state = player.hasPermission(permission);
					checked.put(permission, state);
				}
				pr.setPermission(name, permission, state);
			}
		}
	}
	
	protected void scheduleConsistencyCheckers(){
		BukkitScheduler sched = getServer().getScheduler();
		if (consistencyCheckerTaskId != -1){
			sched.cancelTask(consistencyCheckerTaskId);
		}
		ConfigFile config = ConfigManager.getConfigFile();
		if (!config.getBoolean(ConfPaths.DATA_CONSISTENCYCHECKS_CHECK, true)) return;
		// Schedule task in seconds.
		final long delay = 20L * config.getInt(ConfPaths.DATA_CONSISTENCYCHECKS_INTERVAL, 1, 3600, 10);
		consistencyCheckerTaskId = sched.scheduleSyncRepeatingTask(this, new Runnable() {
			@Override
			public void run() {
				runConsistencyChecks();
			}
		}, delay, delay );
	}
	
	/**
	 * Run consistency checks for at most the configured duration. If not finished, a task will be scheduled to continue.
	 */
	protected void runConsistencyChecks(){
		final long tStart = System.currentTimeMillis();
		final ConfigFile config = ConfigManager.getConfigFile();
		if (!config.getBoolean(ConfPaths.DATA_CONSISTENCYCHECKS_CHECK) || consistencyCheckers.isEmpty()){
			consistencyCheckerIndex = 0;
			return;
		}
		final long tEnd = tStart + config.getLong(ConfPaths.DATA_CONSISTENCYCHECKS_MAXTIME, 1, 50, 2);
		if (consistencyCheckerIndex >= consistencyCheckers.size()) consistencyCheckerIndex = 0;
		final Player[] onlinePlayers = getServer().getOnlinePlayers();
		// Loop
		while (consistencyCheckerIndex < consistencyCheckers.size()){
			final ConsistencyChecker checker = consistencyCheckers.get(consistencyCheckerIndex);
			try{
				checker.checkConsistency(onlinePlayers);
			}
			catch (Throwable t){
				LogUtil.logSevere("[NoCheatPlus] ConsistencyChecker(" + checker.getClass().getName() + ") encountered an exception:");
				LogUtil.logSevere(t);
			}
			consistencyCheckerIndex ++; // Do not remove :).
			final long now = System.currentTimeMillis();
			if (now < tStart || now >= tEnd){
				break;
			}
		}
		// (The index might be bigger than size by now.)
		
		final boolean debug = config.getBoolean(ConfPaths.LOGGING_DEBUG);
		
		// If not finished, schedule further checks.
		if (consistencyCheckerIndex < consistencyCheckers.size()){
			getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
				@Override
				public void run() {
					runConsistencyChecks();
				}
			});
			if (debug){
				LogUtil.logInfo("[NoCheatPlus] Re-scheduled consistency-checks.");
			}
		}
		else if (debug){
			LogUtil.logInfo("[NoCheatPlus] Consistency-checks run.");
		}
	}

}
