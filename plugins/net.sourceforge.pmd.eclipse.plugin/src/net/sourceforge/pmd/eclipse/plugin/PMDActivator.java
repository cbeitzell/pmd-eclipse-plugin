package net.sourceforge.pmd.eclipse.plugin;

import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSetNotFoundException;
import net.sourceforge.pmd.ui.nls.StringKeys;
import net.sourceforge.pmd.ui.nls.StringTable;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;

import net.sourceforge.pmd.core.IRuleSetManager;
import net.sourceforge.pmd.core.PluginConstants;
import net.sourceforge.pmd.core.ext.RuleSetsExtensionProcessor;
import net.sourceforge.pmd.core.impl.RuleSetManagerImpl;
import net.sourceforge.pmd.runtime.preferences.IPreferences;
import net.sourceforge.pmd.runtime.preferences.IPreferencesFactory;
import net.sourceforge.pmd.runtime.preferences.IPreferencesManager;
import net.sourceforge.pmd.runtime.preferences.impl.PreferencesFactoryImpl;
import net.sourceforge.pmd.runtime.properties.IProjectProperties;
import net.sourceforge.pmd.runtime.properties.IProjectPropertiesManager;
import net.sourceforge.pmd.runtime.properties.IPropertiesFactory;
import net.sourceforge.pmd.runtime.properties.PropertiesException;
import net.sourceforge.pmd.runtime.properties.impl.PropertiesFactoryImpl;
import net.sourceforge.pmd.runtime.writer.IAstWriter;
import net.sourceforge.pmd.runtime.writer.IRuleSetWriter;
import net.sourceforge.pmd.runtime.writer.impl.WriterFactoryImpl;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.eclipse.core.resources.IProject;

/**
 * The activator class controls the plug-in life cycle
 */
public class PMDActivator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "net.sourceforge.pmd.eclipse.plugin";

	// The shared instance
	private static PMDActivator plugin;
	
	/**
	 * The constructor
	 */
	public PMDActivator() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
        configureLogs(loadPreferences());
        this.registerStandardRuleSets();
        this.registerAdditionalRuleSets();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static PMDActivator getDefault() {
		return plugin;
	}

    private static final Logger log = Logger.getLogger(PMDActivator.class);

    private StringTable stringTable; // NOPMD by Herlin on 11/10/06 00:22
    private String[] priorityLabels; // NOPMD by Herlin on 11/10/06 00:22

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path.
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return AbstractUIPlugin.imageDescriptorFromPlugin("net.sourceforge.pmd.eclipse.plugin", path);
	}
    
    /**
     * Get an image corresponding to the severity
     */
    public Image getImage(String key, String iconPath) {
        final ImageRegistry registry = getImageRegistry();
        Image image = registry.get(key);
        if (image == null) {
            final ImageDescriptor descriptor = getImageDescriptor(iconPath);
            if (descriptor != null) {
                registry.put(key, descriptor);
                image = registry.get(key);
            }
        }

        return image;
    }

    /**
     * Helper method to log error
     * 
     * @see IStatus
     */
    public void logError(String message, Throwable t) {
        getLog().log(new Status(IStatus.ERROR, getBundle().getSymbolicName(), 0, message + t.getMessage(), t));
        if (log != null) {
            log.error(message, t);
        }
    }

    /**
     * Helper method to log error
     * 
     * @see IStatus
     */
    public void logError(IStatus status) {
        getLog().log(status);
        if (log != null) {
            log.error(status.getMessage(), status.getException());
        }
    }

    /**
     * Helper method to display error
     */
    public void showError(final String message, final Throwable t) {
        logError(message, t);
        Display.getDefault().syncExec(new Runnable() {

            public void run() {
                
                MessageDialog.openError(Display.getCurrent().getActiveShell(), getStringTable().getString(StringKeys.MSGKEY_ERROR_TITLE), message
                        + String.valueOf(t));
            }
        });
    }
    
    /**
     * @return an instance of the string table
     */
    public StringTable getStringTable() {
        if (this.stringTable == null) {
            this.stringTable = new StringTable();
        }
        
        return this.stringTable;
    }

    /**
     * @return the priority values
     */
    public Integer[] getPriorityValues() {
        return new Integer[] {
                new Integer(1),
                new Integer(2),
                new Integer(3),
                new Integer(4),
                new Integer(5)
        };
    }

    /**
     * Return the priority labels
     */
    public String[] getPriorityLabels() {
        if (this.priorityLabels == null) {
            final StringTable stringTable = getStringTable();
            this.priorityLabels = new String[]{
                stringTable.getString(StringKeys.MSGKEY_PRIORITY_ERROR_HIGH),
                stringTable.getString(StringKeys.MSGKEY_PRIORITY_ERROR),
                stringTable.getString(StringKeys.MSGKEY_PRIORITY_WARNING_HIGH),
                stringTable.getString(StringKeys.MSGKEY_PRIORITY_WARNING),
                stringTable.getString(StringKeys.MSGKEY_PRIORITY_INFORMATION)
            };
        }

        return this.priorityLabels; // NOPMD by Herlin on 11/10/06 00:22
    }

    public static final String ROOT_LOG_ID = "net.sourceforge.pmd";
    private static final String PMD_ECLIPSE_APPENDER_NAME = "PMDEclipseAppender";
    private IPreferencesFactory preferencesFactory = new PreferencesFactoryImpl();
    private IPropertiesFactory propertiesFactory = new PropertiesFactoryImpl();
    
    /**
     * Load the PMD plugin preferences
     */
    public IPreferences loadPreferences() {
        return getPreferencesManager().loadPreferences();
    }
    
    /**
     * @return the plugin preferences manager
     */
    public IPreferencesManager getPreferencesManager() {
        return this.preferencesFactory.getPreferencesManager();
    }
    
    /**
     * @return the plugin project properties manager
     */
    public IProjectPropertiesManager getPropertiesManager() {
        return this.propertiesFactory.getProjectPropertiesManager();
    }
    
    /**
     * @param project a workspace project
     * @return the PMD properties for that project
     */
    public IProjectProperties loadProjectProperties(IProject project) throws PropertiesException {
        return getPropertiesManager().loadProjectProperties(project);
    }

    /**
     * Helper method to log information
     * 
     * @see IStatus
     */
    public void logInformation(String message) {
        getLog().log(new Status(IStatus.INFO, getBundle().getSymbolicName(), 0, message, null));
    }
    
    /**
     * @return an instance of an AST writer
     */
    public IAstWriter getAstWriter() {
        return new WriterFactoryImpl().getAstWriter();
    }
    
    /**
     * @return an instance of a ruleset writer
     */
    public IRuleSetWriter getRuleSetWriter() {
        return new WriterFactoryImpl().getRuleSetWriter();
    }
    
    /**
     * Apply the log preferencs
     */
    public void applyLogPreferences(IPreferences preferences) {
        Logger log = Logger.getLogger(ROOT_LOG_ID);
        log.setLevel(preferences.getLogLevel());
        RollingFileAppender appender = (RollingFileAppender) log.getAppender(PMD_ECLIPSE_APPENDER_NAME);
        if (appender == null) {
            configureLogs(preferences);
        } else if (!appender.getFile().equals(preferences.getLogFileName())) {
            appender.setFile(preferences.getLogFileName());
            appender.activateOptions();
        }
    }

    /**
     * Configure the logging
     *
     */
    private void configureLogs(IPreferences preferences) {
        try {
            Layout layout = new PatternLayout("%d{yyyy/MM/dd HH:mm:ss,SSS} %-5p %-32c{1} %m%n");
            
            RollingFileAppender appender = new RollingFileAppender(layout, preferences.getLogFileName());
            appender.setName(PMD_ECLIPSE_APPENDER_NAME);
            appender.setMaxBackupIndex(1);
            appender.setMaxFileSize("10MB");
            
            Logger.getRootLogger().addAppender(new ConsoleAppender(layout));
            Logger.getRootLogger().setLevel(Level.WARN);
            Logger.getRootLogger().setAdditivity(false);
            
            Logger.getLogger(ROOT_LOG_ID).addAppender(appender);            
            Logger.getLogger(ROOT_LOG_ID).setLevel(preferences.getLogLevel());
            Logger.getLogger(ROOT_LOG_ID).setAdditivity(false);

        } catch (IOException e) {
            logError("IO Exception when configuring logging.", e);
        }
    }

    private final IRuleSetManager ruleSetManager = new RuleSetManagerImpl(); // NOPMD:SingularField

    /**
     * @return the ruleset manager instance
     */
    public final IRuleSetManager getRuleSetManager() {
        return this.ruleSetManager;
    }

    /**
     * Logs inside the Eclipse environment
     * 
     * @param severity the severity of the log (IStatus code)
     * @param message the message to log
     * @param t a possible throwable, may be null
     */
    public final void log(final int severity, final String message, final Throwable t) {
        final Bundle bundle = getBundle();
        if (bundle != null) {
            getLog().log(new Status(severity, bundle.getSymbolicName(), 0, message, t));
        }
        
        // TODO : when bundle is not created yet (ie at startup), we cannot log ; find a way to log.
    }

    /**
     * Registering the standard rulesets
     * 
     */
    private void registerStandardRuleSets() {
        final RuleSetFactory factory = new RuleSetFactory();
        for (int i = 0; i < PluginConstants.PMD_RULESETS.length; i++) {
            try {
                final RuleSet ruleSet = factory.createRuleSets(PluginConstants.PMD_RULESETS[i]).getAllRuleSets()[0];
                getRuleSetManager().registerRuleSet(ruleSet);
                getRuleSetManager().registerDefaultRuleSet(ruleSet);
            } catch (RuleSetNotFoundException e) {
                this.log(IStatus.WARNING, "The RuleSet \"" + PluginConstants.PMD_RULESETS[i] + "\" cannot be found", e);
            }
        }
    }

    /**
     * Register additional rulesets that may be provided by a fragment. Find
     * extension points implementation and call them
     * 
     */
    private void registerAdditionalRuleSets() {
        try {
            final RuleSetsExtensionProcessor processor = new RuleSetsExtensionProcessor(getRuleSetManager());
            processor.process();
        } catch (CoreException e) {
            log(IStatus.ERROR, "Error when processing RuleSets extensions", e);
        }
    }
}

