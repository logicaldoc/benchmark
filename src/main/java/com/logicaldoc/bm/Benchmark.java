package com.logicaldoc.bm;

import java.io.File;

import org.slf4j.LoggerFactory;

import com.logicaldoc.core.task.Task;

/**
 * Executes the benchmark machinery inside LogicalDOC
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.2
 */
public class Benchmark extends Task {
	
	public static final String NAME = "Benchmark";

	private MultiLoader loader = null;

	public Benchmark() {
		super(NAME);
		log = LoggerFactory.getLogger(Benchmark.class);
	}

	@Override
	public boolean isIndeterminate() {
		return true;
	}

	@Override
	public boolean isConcurrent() {
		return false;
	}

	@Override
	protected void runTask() {
		log.info("Start the benchmark");
		try {
			loader = MultiLoader.start(new File(config.getProperty("benchmark.dir")));

			while (!loader.isFinished()) {
				try {
					Thread.sleep(500);
					if (interruptRequested)
						loader.shutdown();
				} catch (InterruptedException e) {

				}
			}

			try {
				loader.shutdown();
			} catch (Throwable t) {

			}
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} finally {
			log.info("Benchmark finished");
		}
	}

}