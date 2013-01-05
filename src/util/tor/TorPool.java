package util.tor;

import java.io.IOException;
import java.util.Random;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.songpeiyou.subprocess.SystemCommandExecutor;

import util.Util;

public class TorPool {
	public static int retry = 20;
	private static TorPool instance;
	private static Logger log = Logger.getLogger(TorPool.class);

	private static Random rand = new Random();
	private static Vector<SystemCommandExecutor> pool;

	private TorPool(int tornum) {
		pool = util.tor.Tor.initTors(tornum);
		attachShutDownHook();
		if (pool.size() == 0 && tornum != 0) {
			log.fatal("Could not initilize a single tor.");
			System.exit(weibo.WeiboConf.exitTorErr);
		}
	}

	public synchronized static TorPool getInstance(int tornum) {
		if (instance == null)
			instance = new TorPool(tornum);
		return instance;
	}

	public synchronized static TorPool getInstance() {
		if (instance == null)
			instance = new TorPool(0);
		return instance;
	}

	public synchronized static int getAPort(int retry) {
		if (retry < 0)
			return -1;

		if (pool.size() < 1)
			pool = util.tor.Tor.initTors(10);

		int idx = rand.nextInt(pool.size());
		SystemCommandExecutor exe = pool.get(idx);

		if (exe.exitValue() != SystemCommandExecutor.NotFinish) {
			SystemCommandExecutor exe2 = Tor.initTor();
			if (exe2 == null) {
				pool.remove(exe);
				return getAPort(retry - 1);
			}
			pool.set(idx, exe2);
			return getAPort(retry - 1);
		}

		if (Tor.getTorStatus(exe) < 0) {
			return getAPort(retry - 1);
		}

		return (int) exe.getInfo(util.tor.Tor.INFO_PORT);
	}

	private static SystemCommandExecutor findbyport(int port) {
		for (SystemCommandExecutor exe : pool) {
			if ((int) exe.getInfo(util.tor.Tor.INFO_PORT) == port) {
				return exe;
			}
		}
		return null;
	}
	
	public static void restartATor(int port) {
		SystemCommandExecutor exe = findbyport(port);
		restartATor(exe);
	}
	
	public static void restartATor(SystemCommandExecutor exe){
		try {
			synchronized (TorPool.class) {
				pool.remove(exe);
			}
			
			SystemCommandExecutor cmdExe = Tor.initTor();
			SystemCommandExecutor.kill(exe);

			String tordir = Tor.dataDir((int) exe.getInfo(util.tor.Tor.INFO_PORT));
			Util.deleteDir(tordir);

			if (cmdExe != null) {
				synchronized (TorPool.class) {
					pool.add(cmdExe);
				}
			}

		} catch (InterruptedException e) {
			log.error("",e);
		} catch (IOException e) {
			log.error("",e);
		}
	}

	public static void clean() {
		if (pool != null && pool.size() > 0) {
			try {

				SystemCommandExecutor.kill(pool);
				for (SystemCommandExecutor exe : pool) {
					String tordir = Tor.dataDir((Integer) exe.getInfo(Tor.INFO_PORT));
					Util.deleteDir(tordir);
				}

			} catch (InterruptedException | IOException e) {
				e.printStackTrace();
			} finally {
				pool = null;
				instance = null;
			}
		}
	}

	protected void finalize() throws Throwable {
		if (pool != null && pool.size() > 0) {
			SystemCommandExecutor.kill(pool);
		}
		super.finalize();
	}

	private static void attachShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				clean();
			}
		});
	}

	public int getTORNUM() {
		return pool.size();
	}
}
