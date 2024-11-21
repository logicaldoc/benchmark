package com.logicaldoc.bm;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.logicaldoc.util.config.ContextProperties;
import com.logicaldoc.util.config.LogConfigurator;
import com.logicaldoc.util.plugin.LogicalDOCPlugin;
import com.logicaldoc.util.plugin.PluginException;

/**
 * Entry-point for the benchmark plug-in
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.2
 */
public class BenchmarkPlugin extends LogicalDOCPlugin {

	protected static Log log = LogFactory.getLog(BenchmarkPlugin.class);

	@Override
	public void install() throws PluginException {
		super.install();

		try {
			// Add the scheduling defaults
			ContextProperties pbean = new ContextProperties();
			pbean.setProperty("schedule.cron.Benchmark", "00 00 00 1 * ?");
			pbean.setProperty("schedule.length.Benchmark", "-1");
			pbean.setProperty("schedule.enabled.Benchmark", "true");
			pbean.setProperty("schedule.mode.Benchmark", "simple");
			pbean.setProperty("schedule.interval.Benchmark", "18000000000");
			pbean.setProperty("schedule.delay.Benchmark", "1800000");
			//pbean.write();

			// Some settings
			pbean.setProperty("benchmark.dir", "/benchmark");
			pbean.write();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

		// Add audit logger issues
		LogConfigurator logging = new LogConfigurator();
		logging.addTextAppender("Benchmark");
		logging.write();
		logging.addHtmlAppender("Benchmark_WEB");
		logging.write();
		//logging.addCategory("com.logicaldoc.bm.Benchmark", new String[] { "Benchmark", "Benchmark_WEB" });
		logging.addLogger("com.logicaldoc.bm.Benchmark", (List<String>)Arrays.asList("Benchmark", "Benchmark_WEB"));
		
		logging.write();

		setRestartRequired();
	}
}