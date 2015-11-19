
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.main;

import java.io.EOFException;
import java.io.File;
import java.util.ArrayList;

import org.gnu.readline.Readline;
import org.gnu.readline.ReadlineLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support superclass for command line classes.
 */
public abstract class MainClass {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected MainClass() {
    }

    /**
     * Subclass main implementation. This method is free to throw exceptions; these will
     * be displayed on standard error and converted into non-zero exit values.
     *
     * @param args command line arguments
     * @return process exit value
     * @throws Exception if an error occurs; will result in the process exiting with an exit value of one
     */
    public abstract int run(String[] args) throws Exception;

    /**
     * Enter command loop. Commands are read using GNU libreadline and handed off
     * to {@link #handleCommand} for processing.
     *
     * @param appName application name
     * @param prompt command prompt
     * @throws Exception if an error occurs
     */
    protected void commandLoop(String appName, String prompt) throws Exception {

        // Setup readline
        try {
            Readline.load(ReadlineLibrary.GnuReadline);
        } catch (UnsatisfiedLinkError e) {
            // ignore
        }
        Readline.initReadline(appName);
        Readline.setThrowExceptionOnUnsupportedMethod(false);

        // Read init file(s)
        ArrayList<String> files = new ArrayList<String>(3);
        String var = System.getenv("INPUTRC");
        if (var != null)
            files.add(var);
        String home = System.getProperty("user.home");
        if (home != null)
            files.add(new File(new File(home), ".inputrc").getAbsolutePath());
        files.add("/etc/.inputrc");
        for (String file : files) {
            try {
                Readline.readInitFile(file);
            } catch (Exception e) {
                // ignore
            }
        }

        // Read history file
        String historyFile = new File(new File(System.getProperty("user.home")), "." + appName + "_history").getAbsolutePath();
        try {
            Readline.readHistoryFile(historyFile);
        } catch (Exception e) {
            // ignore
        }

        // Main loop
        try {
            while (true) {

                // Read next line
                String line;
                try {
                    line = Readline.readline(prompt);
                } catch (EOFException e) {
                    System.out.println();
                    break;
                }

                // No input?
                if (line == null)
                    continue;

                // Update history
                Readline.addToHistory(line);

                // Execute line
                if (!this.handleCommand(line))
                    break;
            }
        } finally {

            // Save history
            try {
                Readline.writeHistoryFile(historyFile);
            } catch (Exception e) {
                // ignore
            }

            // Clean up
            Readline.cleanup();
        }
    }

    /**
     * Callback used by {@link #commandLoop}.
     *
     * <p>
     * The implementation in {@link MainClass} just returns {@code false}.
     *
     * @param line command line input
     * @return true to continue reading the next command, false to exit
     * @throws Exception if an error occurs
     */
    protected boolean handleCommand(String line) throws Exception {
        return false;
    }

    /**
     * Display the usage message to standard error.
     */
    protected abstract void usageMessage();

    /**
     * Print the usage message and exit with exit value 1.
     */
    protected void usageError() {
        usageMessage();
        System.exit(1);
    }

    /**
     * Emit an error message an exit with exit value 1.
     *
     * @param message error message
     */
    protected final void errout(String message) {
        System.err.println(getClass().getSimpleName() + ": " + message);
        System.exit(1);
    }

    /**
     * Parse command line flags of the form {@code -Dname=value} and set the corresponding system properties.
     * Parsing stops at the first argument not starting with a dash (or {@code --}).
     *
     * @param args command line arguments
     * @return {@code args} with the {@code -Dname=value} flags removed
     */
    protected String[] parsePropertyFlags(String[] args) {
        ArrayList<String> list = new ArrayList<String>(args.length);
        boolean done = false;
        for (String arg : args) {
            if (done) {
                list.add(arg);
                continue;
            }
            if (arg.equals("--") || arg.length() == 0 || arg.charAt(0) != '-') {
                list.add(arg);
                done = true;
                continue;
            }
            if (arg.startsWith("-D")) {
                int eq = arg.indexOf('=');
                if (eq < 3)
                    usageError();
                System.setProperty(arg.substring(2, eq), arg.substring(eq + 1));
                continue;
            }
            list.add(arg);
        }
        return list.toArray(new String[list.size()]);
    }

    /**
     * Invokes {@link #run}, catching any exceptions thrown and exiting with a non-zero
     * value if and only if an exception was caught.
     *
     * <p>
     * The concrete class' {@code main()} method should invoke this method.
     *
     * @param args command line arguments
     */
    protected void doMain(String[] args) {
        int exitValue = 1;
        try {
            exitValue = run(args);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        } finally {
            System.exit(exitValue);
        }
    }
}

