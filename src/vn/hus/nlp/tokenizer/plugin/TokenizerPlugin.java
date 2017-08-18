package vn.hus.nlp.tokenizer.plugin;


import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import vn.hus.nlp.tokenizer.TokenizerProvider;

/**
 * The activator class controls the plug-in life cycle
 */
public class TokenizerPlugin extends Plugin {

	public static final String PLUGIN_ID = "vn.hus.nlp.tokenizer.plugin";

	// The shared instance
	private static TokenizerPlugin plugin;
	/**
	 * A tokenizer provider for the bundle
	 */
	private static TokenizerProvider tokenizerProvider = null;
	
	/**
	 * The constructor
	 */
	public TokenizerPlugin() {
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		// initialize the tokenizer provider
		getTokenizerProvider();
		
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		// dispose the tokenizer provider
		if (tokenizerProvider != null) {
			tokenizerProvider.dispose();
			tokenizerProvider = null;
		}
		
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static TokenizerPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns the tokenizer provider for the bundle
	 * @return the tokenizer provider
	 */
	public TokenizerProvider getTokenizerProvider() {
		if (tokenizerProvider == null) {
			tokenizerProvider = TokenizerProvider.getInstance();
		}
		return tokenizerProvider;
	}
 
	
}
