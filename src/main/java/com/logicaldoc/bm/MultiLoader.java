package com.logicaldoc.bm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Executes a number of different load threads against one or more LogicalDOC
 * instances.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @author Alessandro Gasparini - LogicalDOC
 * @since 6.5
 */
public class MultiLoader {

	private static Logger log = LoggerFactory.getLogger(MultiLoader.class);

	private final static long DEFAULTWORKSPACE = 4L;

	protected String folderProfile;

	protected long rootFolder = DEFAULTWORKSPACE;

	public void setRootFolder(long rootFolder) {
		this.rootFolder = rootFolder;
	}

	private static final String COLUMNS_SUMMARY = String.format("%15s\t%15s\t%15s\t%15s\t%15s\t%15s\t%25s", "NAME",
			"COUNT", "TOTAL TIME", "AVERAGE TIME", "PER SECOND", "ERRORS", "DESCRIPTION");

	protected long startTime;

	private LoadSession session;

	private AbstractLoader[] loaders;

	private Thread consoleThread;

	private SocketListener listener;

	private boolean finished = false;

	private static MultiLoader instance = null;

	public static MultiLoader get() {
		return instance;
	}

	@SuppressWarnings("resource")
	public static void main(String[] args) {
		if (System.getProperty("bm.root") == null)
			System.setProperty("bm.root", ".");

		ApplicationContext context = new ClassPathXmlApplicationContext(new String[] { "/context.xml" });
		instance = (MultiLoader) context.getBean("MultiLoader");

		try {
			internalMain();
		} catch (Throwable e) {
			System.err.println("A failure prevented proper execution.");
			e.printStackTrace();
			log.error(e.getMessage(), e);
			System.exit(1);
		}
		System.exit(0);
	}

	private static void internalMain() {
		// Run
		instance.start();

		// Now lower this threads priority
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

		while (!instance.finished) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {

			}
			if (instance.allThreadsFinished()) {
				System.out.println("The test is complete.");
				instance.finished = true;
				break;
			}
		}

		// Finish off
		instance.close();
		instance.dumpThreadSummaries();
	}

	/**
	 * Designed to be launched from inside a plugin
	 * 
	 * @throws Exception
	 */
	public static MultiLoader start(File benchmarkDeployDir) throws Exception {
		log.info("Starting benchmark installed in {}", benchmarkDeployDir.getAbsolutePath());
		Config.reset();
		System.setProperty("bm.root", benchmarkDeployDir.getAbsolutePath());

		instance = new MultiLoader();
		LoadSession session = new LoadSession();
		session.setUrl(Config.get().getProperty("session.url"));
		session.setUsername(Config.get().getProperty("session.username"));
		session.setPassword(Config.get().getProperty("session.password"));
		session.connect();

		log.info("Started load session");
		instance.setSession(session);
		instance.init();

		Runnable runnable = () -> {
			try {
				instance.makeThreads();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			internalMain();
		};
		Thread thread = new Thread(runnable);
		thread.start();

		return instance;
	}

	private boolean allThreadsFinished() {
		for (AbstractLoader alt : loaders) {
			if (!alt.isFinished())
				return false;
		}
		return true;
	}

	public synchronized void start() {
		log.debug("start()");

		log.debug("session: {}", session);
		log.debug("loaders: {}", (Object[]) loaders);
		if (session == null || loaders == null) {
			log.warn("session IS NULL and loaders IS NULL");
			throw new RuntimeException("Application not initialized");
		}

		consoleThread.start();

		// Fire up the threads
		for (Thread thread : loaders) {
			thread.start();
		}
		log.debug("start() completed");
	}

	public String processCommand(String command) throws InterruptedException {
		if (command.equalsIgnoreCase("q")) {
			log.info("Requested stop");
			finish();
			throw new InterruptedException();
		} else if (command.equalsIgnoreCase("s") || command.equalsIgnoreCase("p")) {
			return dumpThreadSummaries();
		}
		return "";
	}

	private void finish() {
		stopThreads();
		finished = true;
	}

	public void shutdown() {
		finish();
		close();
	}

	private void initConsole() {
		log.debug("initConsole()");

		consoleThread = new Thread() {

			@Override
			public void run() {
				/*
				 * Waiting the end of the job
				 */
				System.out.println("   Enter 'q' to quit.");
				System.out.println("   Enter 's' to dump a thread summary.");

				while (!finished) {
					int keyPress = 0;
					try {
						keyPress = System.in.read();
					} catch (IOException e) {
						break;
					}

					try {
						processCommand(new String(new char[] { (char) keyPress }));
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		};

		consoleThread.setPriority(Thread.MIN_PRIORITY);
		log.debug("initConsole() completed");
	}

	public synchronized void stopThreads() {
		// Stop the threads
		for (AbstractLoader thread : loaders) {
			thread.setStop();
		}
		// Now join each thread to make sure they all finish their current
		// operation
		for (AbstractLoader thread : loaders) {
			// Notify any waits
			synchronized (thread) {
				thread.notifyAll();
			}
			try {
				thread.join();
			} catch (InterruptedException e) {
			}
		}
	}

	public String dumpThreadSummaries() {
		StringBuffer sb = new StringBuffer();

		sb.append("\n");
		sb.append(COLUMNS_SUMMARY);
		// Dump each thread's summary
		for (AbstractLoader thread : loaders) {
			String summary = thread.getSummary();
			sb.append("\n");
			sb.append(summary);
		}

		System.out.println(sb.toString());
		return sb.toString();
	}

	public synchronized void close() {
		log.debug("close()");
		consoleThread.interrupt();
		listener.close();

		// Print and Log each thread's summary
		for (AbstractLoader thread : loaders) {
			String summary = thread.getSummary();
			session.logSummary(summary);
		}
		session.close();
	}

	public void setFolderProfile(String folderProfile) {
		this.folderProfile = folderProfile;
	}

	/**
	 * Initializes resources and connects to the WebService
	 * 
	 * @throws Exception
	 */
	public void init() throws Exception {
		log.debug("init()");

		makeThreads();

		// Log the initial summaries
		String summary = session.getSummary();
		session.logSummary(summary);

		// Header the outputs
		session.logSummary(LoadSession.getLineEnding());
		session.logSummary(COLUMNS_SUMMARY);

		// Initialize the socket
		listener = new SocketListener(instance, Config.get().getInt("socket.port"));
		listener.start();
		log.info("Socket listening on port {}", Config.get().getInt("socket.port"));

		// Initialize control console
		initConsole();
		log.debug("init() completed");
	}

	private AbstractLoader[] makeThreads() throws Exception {

		ArrayList<AbstractLoader> allLoaders = new ArrayList<AbstractLoader>(3);
		StringTokenizer tokenizer = new StringTokenizer(Config.get().getProperty("loaders"), ",", false);
		ArrayList<String> ldrs = new ArrayList<String>();
		while (tokenizer.hasMoreTokens()) {
			String k = tokenizer.nextToken().trim();
			ldrs.add(k);
		}

		for (String loader : ldrs) {
			for (int i = 0; i < Config.get().getInt(loader + ".threads"); i++) {
				Class<?> clazz = Class.forName("com.logicaldoc.bm.loaders." + loader);
				// Try to instantiate the parser
				Object o = clazz.getDeclaredConstructor().newInstance();
				if (!(o instanceof AbstractLoader))
					throw new Exception("The specified loader " + loader + " is not a loader");

				AbstractLoader ld = (AbstractLoader) o;
				ld.setIterations(Long.parseLong(Config.get().getProperty(loader + ".iterations")));

				ld.init(session);
				allLoaders.add(ld);
			}
		}

		// Done
		AbstractLoader[] ret = new AbstractLoader[allLoaders.size()];
		log.debug("allLoaders.size(): {}", allLoaders.size());
		log.debug("makeThreads() completed");
		loaders = allLoaders.toArray(ret);
		return allLoaders.toArray(ret);
	}

	public void setSession(LoadSession session) {
		this.session = session;
	}

	public boolean isFinished() {
		return finished;
	}
}