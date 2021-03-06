package net.rcode.assetserver.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import net.rcode.assetserver.addon.AddonManager;
import net.rcode.assetserver.cache.Cache;
import net.rcode.assetserver.cache.FileSystemCache;
import net.rcode.assetserver.ejs.EjsRuntime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up an assetserver instance.  This class is used at initialization time
 * and produces a Runtime instance.
 * 
 * @author stella
 *
 */
public class AssetServer {
	public static final String CONFIG_NAME=".asconfig";
	public static final String ACCESS_NAME=".asaccess";
	
	public static final Logger logger=LoggerFactory.getLogger("assetserver");
	
	private EjsRuntime javascriptRuntime;
	private File configDirectory;
	private File configFile;
	private String defaultTextFileEncoding="UTF-8";
	
	private ResourceContextManager contextManager;
	private AssetRoot root;
	private MimeMapping mimeMapping;
	private File sharedCacheLocation;
	private Cache sharedCache;
	private AddonManager addonManager;
	private FilterChainInitializerLookup filterLookup;
	private boolean globalDisableOptimization;
	
	private ServerConfig config;
	
	public AssetServer(File location) throws Exception {
		this.config=new ServerConfig();
		
		this.javascriptRuntime=new EjsRuntime();
		initializeJavaScriptRuntime();
		
		mimeMapping=new MimeMapping();
		mimeMapping.loadDefaults();
		
		setupLocation(location);
		setSharedCacheLocation(new File(configDirectory, ".ascache"));
		
		root=new AssetRoot(this);
		
		// Setup addon manager
		addonManager=new AddonManager(this);
		
		// Add the directory containing the executable to the addon search path
		String execFileName=System.getProperty("exec.file");	// Set by the launchers
		if (execFileName==null) {
			logger.warn("No exec.file system property.  This is needed to discover addons.");
		} else {
			addonManager.getSearchPath().add(new File(execFileName).getParentFile());
		}
		
		// Add the .asaddon directory
		addonManager.getSearchPath().add(new File(configDirectory, ".asaddon"));
		
		// Initialize the context builder and the root context
		ResourceContextBuilder contextBuilder=new ResourceContextBuilder();
		ResourceContext rootContext=new ResourceContext(null);
		filterLookup=new FilterChainInitializerLookup();
		filterLookup.addBuiltins();
		
		rootContext.setFilterLookup(filterLookup);
		
		// Initialize with server defaults
		Reader defaultsReader=new InputStreamReader(getClass().getResourceAsStream("asconfig-defaults.js"), "UTF-8");
		try {
			contextBuilder.evaluateServerConfig(rootContext, this, defaultsReader, "asconfig-defaults.js");
		} finally {
			defaultsReader.close();
		}
		
		// See if we have an asconfig
		if (configFile.isFile()) {
			// Load the root configuration
			logger.info("Loading server configuration from " + configFile);
			Reader configReader=new InputStreamReader(new FileInputStream(configFile), "UTF-8");
			try {
				contextBuilder.evaluateServerConfig(rootContext, this, configReader, configFile.toString());
			} finally {
				configReader.close();
			}
		} else {
			// Setup a default mount configuration (single directory mount)
			// and let the mount handler process asaccess files
			logger.info("No server configuration file found: " + configFile);
		}
		
		// If no mounts, then add one
		if (root.getMountPoints().isEmpty()) {
			logger.info("No mounts configured. Setting up directory " + configDirectory + " as server root.");
			ResourceMount rootMount=new ResourceMount(configDirectory, this);
			root.add("/", rootMount);
		}
		
		// And put it all together with the ResourceContextManager
		contextManager=new ResourceContextManager(rootContext, contextBuilder);
	}
	
	public Logger getLogger() {
		return logger;
	}
	
	public AddonManager getAddonManager() {
		return addonManager;
	}
	
	public FilterChainInitializerLookup getFilterLookup() {
		return filterLookup;
	}
	
	public String getDefaultTextFileEncoding() {
		return defaultTextFileEncoding;
	}
	
	public void setDefaultTextFileEncoding(String defaultTextFileEncoding) {
		this.defaultTextFileEncoding = defaultTextFileEncoding;
	}
	
	public ResourceContextManager getContextManager() {
		return contextManager;
	}
	
	public void setGlobalDisableOptimization(boolean globalDisableOptimization) {
		this.globalDisableOptimization = globalDisableOptimization;
	}
	
	public boolean isGlobalDisableOptimization() {
		return globalDisableOptimization;
	}
	
	/**
	 * The location of the shared cache or null if no caching.  The default will be the
	 * ".cache" directory under the config directory.
	 * 
	 * @return current shared cache location
	 */
	public File getSharedCacheLocation() {
		return sharedCacheLocation;
	}
	
	/**
	 * Get the global config
	 * @return global config
	 */
	public ServerConfig getConfig() {
		return config;
	}
	
	/**
	 * (Re)set the shared cache location.  Resets the sharedCache field.
	 * @param sharedCacheLocation
	 */
	private void setSharedCacheLocation(File sharedCacheLocation) {
		this.sharedCacheLocation = sharedCacheLocation;
		if (sharedCacheLocation==null) {
			this.sharedCache=null;
		} else {
			this.sharedCache=new FileSystemCache(sharedCacheLocation);
		}
	}
	
	/**
	 * Set the shared cache
	 * @param cache
	 */
	public void setSharedCache(Cache cache) {
		this.sharedCache=cache;
	}
	
	/**
	 * @return The shared cache or null if no cache setup
	 */
	public Cache getSharedCache() {
		return sharedCache;
	}
	
	/**
	 * Get the shared runtime for executing script
	 * @return shared runtime
	 */
	public EjsRuntime getJavascriptRuntime() {
		return javascriptRuntime;
	}
	
	public AssetRoot getRoot() {
		return root;
	}
	
	/**
	 * @return The directory containing the config file
	 */
	public File getConfigDirectory() {
		return configDirectory;
	}
	
	/**
	 * @return The config file (within configDirectory).  This file may not exist.
	 */
	public File getConfigFile() {
		return configFile;
	}
	
	public MimeMapping getMimeMapping() {
		return mimeMapping;
	}
	
	private void setupLocation(File location) throws IOException {
		location=location.getCanonicalFile();
		if (location.isDirectory()) {
			// Config file is within the directory
			configFile=new File(location, CONFIG_NAME);
			configDirectory=location;
		} else {
			// Config file is passed in
			configFile=location;
			configDirectory=configFile.getParentFile();
		}
	}

	private void initializeJavaScriptRuntime() throws Exception {
		javascriptRuntime.loadLibraryStd();
	}
	
	public CharSequence summarizeConfiguration() {
		StringBuilder out=new StringBuilder(512);
		out.append("Mounts:\n");
		for (Map.Entry<String,AssetMount> entry: root.getMountPoints().entrySet()) {
			out.append("  ");
			if (entry.getKey()==null) out.append("/");
			else out.append(entry.getKey());
			out.append(" => ");
			out.append(entry.getValue().toString());
			out.append('\n');
		}
		
		int i=1;
		out.append("Root Filters:\n");
		for (ResourceContext.FilterBinding binding: contextManager.getRootContext().getFilters()) {
			out.append("  ");
			out.append(String.valueOf(i));
			out.append(". ");
			out.append(binding.predicate.toString());
			out.append(" => ");
			out.append(binding.initializer.toString());
			out.append('\n');
			
			i+=1;
		}
		
		// addons
		Collection<String> addonNames=addonManager.getNames();
		if (!addonNames.isEmpty()) {
			out.append("Activated addons:\n");
			for (String name: addonNames) {
				out.append("  ");
				out.append(name);
				out.append('\n');
			}
		}
		
		return out;
	}
	
	/**
	 * Set up a context to process a request and invoke the callback within this context.  This sets
	 * up thread locals needed to initiate actions agains the root.
	 * @param <T>
	 * @param callback
	 * @return the return value from the callback
	 */
	public <T> T withRequestContext(Callable<T> callback) throws Exception {
		enterRequestContext();
		try {
			return callback.call();
		} finally {
			exitRequestContext();
		}
	}

	/**
	 * Enters a RequestContext.  Must be balanced by a call to exitRequestContext().
	 * Should use withRequestContext instead
	 * @return 
	 */
	public RequestContext enterRequestContext() {
		return RequestContext.enter();
	}
	
	/**
	 * Leave the RequestContext
	 */
	public void exitRequestContext() {
		RequestContext.exit();
	}
	
	// --- The following methods exist to ease access from JavaScript
	
}
