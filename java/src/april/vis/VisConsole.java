package april.vis;

import java.awt.event.*;
import java.io.*;
import java.util.*;

public class VisConsole
{
    VisCanvas vc;
    VisWorld vw;

    ArrayList<Line> lines = new ArrayList<Line>();

    String command = null;

    PipedOutputStream pouts;
    PipedInputStream pins;
    PrintStream ppouts;

    ArrayList<Listener> listeners = new ArrayList<Listener>();

    // how long to display stuff
    static final int DISPLAY_MS = 5000;

    static final String INPUT_STYLE = "<<blue, mono-large>>";
    static final String OLD_INPUT_STYLE = "<<gray, mono-large>>";
    static final String OUTPUT_STYLE = "<<black, mono-large>>";
    static final String COMPLETION_STYLE = "<<#000077, mono-large>>";

    static class Line
    {
        long createTime;
        String s;
    }

    public VisConsole(VisCanvas vc, VisWorld vw)
    {
        this.vc = vc;
        this.vw = vw;

        try {
            pouts = new PipedOutputStream();
            pins = new PipedInputStream(pouts);
            ppouts = new PrintStream(new BufferedOutputStream(pouts));

        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }

        vc.addEventHandler(new MyCommandPromptHandler());
        new UpdateThread().start();
        new OutputThread().start();
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    synchronized void output(String s)
    {
        Line line = new Line();
        line.createTime = System.currentTimeMillis();
        line.s = s;

        lines.add(line);
        redraw();
    }

    public interface Listener
    {
        /** Return true if the command was valid. **/
        public boolean consoleCommand(VisConsole vc, PrintStream out, String command);

        /** Return commands that start with prefix. (You can return
         * non-matching completions; VisConsole will filter them
         * out.) You may return null. **/
        public ArrayList<String> consoleCompletions(VisConsole vc, String prefix);
    }

    synchronized void redraw()
    {
        while (lines.size() > 0) {
            Line line = lines.get(0);
            if (System.currentTimeMillis() > line.createTime + DISPLAY_MS)
                lines.remove(0);
            else
                break;
        }

        String buffer = "";
        for (Line line : lines)
            buffer += line.s + "\n";

        if (command != null)
            buffer += INPUT_STYLE + ":" + command;

        VisWorld.Buffer vb = vw.getBuffer("command output");
        vb.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, VisText.JUSTIFICATION.LEFT, buffer));
        vb.switchBuffer();
    }

    class OutputThread extends Thread
    {
        public void run()
        {
            BufferedReader ins = new BufferedReader(new InputStreamReader(pins));

            while (true) {
                try {
                    String line = ins.readLine();
                    output(OUTPUT_STYLE + line);
                    redraw();
                } catch (IOException ex) {
                    System.out.println("VisConsole ex: "+ex);
                }
            }
        }
    }

    // trigger redraws often enough that we remove stale lines.
    class UpdateThread extends Thread
    {
        public void run()
        {
            while (true) {
                Line line = null;

                // get the oldest line
                synchronized(VisConsole.this) {
                    if (lines.size() > 0)
                        line = lines.get(0);
                }

                // nothing is displayed; we won't need to update
                // anything for at least this long...
                if (line == null) {
                    try {
                        Thread.sleep(DISPLAY_MS);
                    } catch (InterruptedException ex) {
                    }
                    continue;
                }

                // wait long enough for the oldest line to expire.
                try {
                    long ms = line.createTime + DISPLAY_MS - System.currentTimeMillis();
                    if (ms > 0)
                        Thread.sleep(ms);
                } catch (InterruptedException ex) {
                }
                redraw();
            }
        }
    }

    class MyCommandPromptHandler extends VisCanvasEventAdapter
    {
        public String getName()
        {
            return "Command Prompt";
        }

        public boolean keyPressed(VisCanvas vc, KeyEvent e)
        {
            char c = e.getKeyChar();
            int code = e.getKeyCode();

            int mods = e.getModifiersEx();
            boolean shift = (mods&KeyEvent.SHIFT_DOWN_MASK) > 0;
            boolean ctrl = (mods&KeyEvent.CTRL_DOWN_MASK) > 0;
            boolean alt = (mods&KeyEvent.ALT_DOWN_MASK) > 0;

            // starting a new command?
            if (command == null) {
                if (c == ':') {
                    command = "";
                    redraw();
                    return true;
                }
                return false;
            }

            // abort entry.
            if (code == KeyEvent.VK_ESCAPE) {
                command = null;
                redraw();
                return true;
            }

            // backspace
            if (code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE) {

                if (alt) {
                    // delete last word, plus any trailing spaces
                    while (command.endsWith(" "))
                        command = command.substring(0, command.length() - 2);
                    int idx = command.lastIndexOf(" ");
                    if (idx < 0)
                        command = "";
                    else
                        command = command.substring(0, idx+1); // keep the space

                } else {
                    // delete last char
                    if (command.length() > 0)
                        command = command.substring(0, command.length()-1);
                }
                redraw();
                return true;
            }

            // end of line
            if (c=='\n' || c=='\r') {
                output(OLD_INPUT_STYLE + ":" + command);
                handleCommand(command);
                command = null;
                redraw();
                return true;
            }

            if (c=='\t') {
                ArrayList<String> completions = new ArrayList<String>();

                for (Listener listener : listeners) {
                    ArrayList<String> cs = listener.consoleCompletions(VisConsole.this, command);
                    if (cs != null)
                        completions.addAll(cs);
                }

                // this will do alphabetical, plus shortest ones first (in ties)
                Collections.sort(completions);

                ArrayList<String> goodCompletions = new ArrayList<String>();
                for (int sidx = 0; sidx < completions.size(); sidx++) {
                    String s = completions.get(sidx);

                    // this one can't be a completion
                    if (s.length() < command.length())
                        continue;

                    // don't complete past the next space. I.e., if a
                    // completion is "dijkstra on" and they've entered
                    // "dij", we only complete to "dijkstra". If they hit tab again,
                    // we'll expand farther.
                    if (s.startsWith(command)) {
                        String t = command;
                        int offset = command.length();

                        for (int p = 0; offset+p < s.length(); p++) {
                            char x = s.charAt(offset + p);

                            if (p > 0 && x == ' ')
                                break;

                            t = t + x;
                        }
                        s = t;

                        // eliminate duplicate completions
                        if (goodCompletions.size()== 0 || !goodCompletions.get(goodCompletions.size()-1).equals(s))
                            goodCompletions.add(s);
                    }
                }

                if (goodCompletions.size() == 1) {
                    command = goodCompletions.get(0);
                    redraw();
                } else if (goodCompletions.size() > 1) {
                    String commonPrefix = goodCompletions.get(0);
                    StringBuffer line = new StringBuffer();

                    for (String s : goodCompletions) {
                        commonPrefix = commonPrefix(commonPrefix, s);
                        line.append(s+"  ");
                        if (line.length() > 80) {
                            output(COMPLETION_STYLE + line.toString());
                            line = new StringBuffer();
                        }
                    }

                    command = commonPrefix;
                    output(COMPLETION_STYLE + line.toString());
                }

                return true;
            }

            // add new character
            if (c >=32 && c < 176)
                command += c;
            redraw();

            vc.drawNow(); // make text input very responsive
            return true;
        }
    }

    /** Return the longest string that 'a' and 'b' that they both start with. **/
    static String commonPrefix(String a, String b)
    {
        StringBuffer sb = new StringBuffer();

        for (int pos = 0; pos < a.length() && pos < b.length(); pos++) {
            if (a.charAt(pos) != b.charAt(pos))
                break;

            sb.append(a.charAt(pos));
        }

        return sb.toString();
    }

    void handleCommand(String s)
    {
        for (Listener listener : listeners)
            listener.consoleCommand(this, ppouts, s);
        ppouts.flush();
    }
}
