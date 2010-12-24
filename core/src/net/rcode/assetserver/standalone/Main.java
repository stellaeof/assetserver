package net.rcode.assetserver.standalone;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.TreeMap;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * Main class.  Does overall command line parsing and dispatch.
 * <p>
 * The assetserver command line is of the pattern:
 * <pre>
 *   assetserver [overall options] command [command options]
 * <pre>
 * 
 * 
 * @author stella
 *
 */
public class Main {
	private static boolean inited=false;
	private static final Map<String, String> commandClassNames=new TreeMap<String, String>();
	private static OptionParser overallParser;
	
	private static void initOnce() {
		if (inited) return;
		inited=true;
		
		// Add commands
		String CP="net.rcode.assetserver.standalone.";
		commandClassNames.put("help", CP + "HelpCommand");
		commandClassNames.put("version", CP + "VersionCommand");
		
		overallParser=new OptionParser();
	}
	
	public static void main(String[] args) throws Throwable {
		// Initialize the overall option parser
		initOnce();
		
		// First split the command line into overall, command and command options
		int i;
		for (i=0; i<args.length; i++) {
			if (args[i].startsWith("-")) continue;
			else break;
		}
		
		if (i>=args.length) {
			overallUsage("Expected a command");
			System.exit(1);
		}
		
		String commandStr=args[i];
		String commandClassName=commandClassNames.get(commandStr);
		if (commandClassName==null) {
			overallUsage("Unrecognied command '" + commandStr + "'");
			System.exit(1);
		}
		
		String[] overallArgs=sliceArray(args, 0, i);
		String[] commandArgs=sliceArray(args, i+1, args.length);
		
		// Parse the overall args
		OptionSet overallOptions;
		try {
			overallOptions=overallParser.parse(overallArgs);
		} catch (OptionException e) {
			overallUsage(e.getMessage());
			System.exit(1);
		}
		
		// Load the command class
		Class<?> commandClass=Class.forName(commandClassName, true, Main.class.getClassLoader());
		MainCommand command=(MainCommand) commandClass.newInstance();
		command.invoke(commandArgs);
	}

	static Class<?> getCommandClass(String commandName) throws ClassNotFoundException {
		String commandClassName=commandClassNames.get(commandName);
		if (commandClassName==null) return null;
		return Class.forName(commandClassName, true, Main.class.getClassLoader());
	}
	
	static String getCommandDescription(String commandName) {
		try {
			return (String) getCommandClass(commandName).getField("DESCRIPTION").get(null);
		} catch (Exception e) {
			return "";
		}
	}
	
	private static String[] sliceArray(String[] args, int start, int end) {
		if (start>=args.length) return new String[0];
		if (end>=args.length) end=args.length;
		String[] ret=new String[end-start];
		for (int i=start; i<end; i++) {
			ret[i-start]=args[start];
		}
		
		return ret;
	}

	static void overallUsage(String syntaxError) throws IOException {
		PrintStream out=System.err;
		if (syntaxError!=null) out.println("Syntax error: " + syntaxError);
		out.println("Usage: assetserver [overall options] command [command options]");
		out.println();
		out.println("Commands:");
		
		for (String commandName: commandClassNames.keySet()) {
			out.print("\t");
			out.print(commandName);
			out.print("\t\t");
			out.print(getCommandDescription(commandName));
			out.println();
		}
		
		out.println();
		out.println("Overall options:");
		overallParser.printHelpOn(out);
		out.println();
	}
}