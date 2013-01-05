package util.tor;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.songpeiyou.subprocess.SystemCommandExecutor;


public class Tor {
	private static Logger log = Logger.getLogger(Tor.class);
	public static int CAPACITY = 100;

	public static final String INFO_PORT = "port";
	private static final Random random = new Random();

	public static boolean portIsIdle(int port) {
		Socket ServerSok;
		try {
			ServerSok = new Socket("localhost", port);
			ServerSok.close();
			return false;
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			return true;
		}
	}

	public static String dataDir(int port) {
		return "/tmp/weibotor_" + port;
	}

	public static String dataDir(String port) {
		return dataDir(Integer.valueOf(port));
	}

	public static SystemCommandExecutor startTor(int port) {

		String[] cmdArray = new String[] { "tor", "--SocksPort", String.valueOf(port), "--DataDirectory", dataDir(port) };
		List<String> cmd = new ArrayList<String>();
		Collections.addAll(cmd, cmdArray);

		SystemCommandExecutor cmdExe = new SystemCommandExecutor(cmd);
		cmdExe.setPrintoutput(true);
		cmdExe.putInfo(INFO_PORT, Integer.valueOf(port));

		try {
			cmdExe.setTimeoutSec(0);
			cmdExe.executeCommand(true);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}
		return cmdExe;
	}

	/**
	 * Initiat <code>count</code> number of Tor instances. The port number are randomly generated between 40000 ~ 65535.
	 * 
	 * @param count
	 * @return
	 */
	public static Vector<SystemCommandExecutor> initTors(int count) {
		Vector<SystemCommandExecutor> exes = new Vector<SystemCommandExecutor>();

		int cnt = 0;
		while (cnt < count) {
			SystemCommandExecutor cmdExe = initTor();
			if (cmdExe == null) {
				log.warn(String.format("Initilized %d Tors instead of requested %d Tors.", cnt, count));
				return exes;
			}
			exes.add(cmdExe);
			cnt++;
		}
		return exes;
	}

	public static SystemCommandExecutor initTor() {
		int min = 40000;
		int max = 65535;

		int port;
		int trys = 0;
		while (trys < 10) {
			trys++;

			do {
				port = min + random.nextInt(max - min);
			} while (!portIsIdle(port));

			SystemCommandExecutor cmdExe = startTor(port);
			if (cmdExe != null) {
				return cmdExe;
			}
		}
		return null;
	}

	public static int getTorStatus(SystemCommandExecutor exe){
		final String checkpoint = "checkpoint";
		final String anchor = "[notice] Bootstrapped 100%: Done.";
		Object o = exe.getInfo(checkpoint);
		if (o==null){
			StringBuilder sb = exe.getStandardOutputFromCommand();
			if (sb.indexOf(anchor) >0) {
				exe.putInfo(checkpoint, "");
				return 0;
			} else{
				return -1;
			}
		} else {
			return 0;
		}
	}
	
	@Deprecated
	/**
	 * @param exe
	 * @return -1: bootstrapping, 0: good, 1: maybe good
	 */
	public static int getTorStatusx(SystemCommandExecutor exe){
		final String checkpoint = "checkpoint";
		final String anchor = "[notice] Bootstrapped 100%: Done.";
		StringBuilder sb = exe.getStandardOutputFromCommand();
		int currentLength = sb.length();
		Integer p = (Integer) exe.getInfo(checkpoint);
		if (p==null) {
			p = sb.indexOf(anchor);
			if (p>0){
				exe.putInfo(checkpoint, p+anchor.length());
				return 0;
			} else {
				return -1;// bootstrapping
			}
		}
		exe.putInfo(checkpoint, currentLength);
		
		if (currentLength == p || currentLength == p+1) return 0;
		
		String lines[] = sb.substring(p, currentLength).split("\\r?\\n");
		
		
		//Jun 29 20:29:53.955 [warn] Set buildtimeout to low value 1373.817622ms. Setting to 1500ms
		if (lines.length==0) return 0;
		if (lines.length==1){
			if (iswarn(lines[0])>=0) return 0;
			else {
				log.warn("tor "+ lines[0]);
				return 1;
			}
		}
		
		int lastline = iswarn(lines[lines.length-1]);
		if (lastline==-1) {
			log.warn("tor "+ lines[lines.length-1]);
			return 1;
		}
		if (lastline==2) return 0;
		if (lastline==1 || lastline==0) {
			if ( iswarn(lines[lines.length-2]) ==2) return 0;
			else {
				log.warn("tor "+ lines[lines.length-2]);
				return 1;
			}
		}
		
		log.warn(sb.toString());
		return 1; // maybe good.
	}
	
	private static int iswarn(String s){
		if (s==null) return 0;
		if (s.trim().equals("")) return 1;
		if (s.indexOf("Set buildtimeout to low value")>0){
			return 2;
		}
		return -1;
	}
}
