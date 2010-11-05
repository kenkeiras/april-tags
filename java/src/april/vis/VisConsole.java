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
    int commandPos = 0;

    PipedOutputStream pouts;
    PipedInputStream pins;
    PrintStream ppouts;

    public int drawOrder = 10;

    ArrayList<Listener> listeners = new ArrayList<Listener>();

    ArrayList<String> history = new ArrayList<String>();
    int historyIdx = -1;
    String historyUndo = null; // what was typed in before they started browsing history

    // how long to display stuff
    static final int DISPLAY_MS = 5000;

    static final String INPUT_STYLE = "<<blue, mono-large>>";
    static final String INPUT_CURSOR_STYLE = "<<#ff3333, mono-large>>";
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
        this(vc, vw, 100000);
    }

    public VisConsole(VisCanvas vc, VisWorld vw, int eventpriority)
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

        vc.addEventHandler(new MyCommandPromptHandler(), eventpriority);
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

        if (command != null) {
            String cmd0 = command.substring(0, commandPos);
            String cmd1 = "", cmd2 = "";
            if (command.length() > commandPos) {
                cmd1 = command.substring(commandPos, commandPos+1);
                cmd2 = command.substring(commandPos + 1);
            }

            buffer += INPUT_STYLE + ":" + cmd0 + INPUT_CURSOR_STYLE + cmd1 + INPUT_STYLE + cmd2;
        }

        VisWorld.Buffer vb = vw.getBuffer("command output");
        vb.setDrawOrder(drawOrder);
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

        public boolean keyTyped(VisCanvas vc, KeyEvent e)
        {
            // consume keyTyped events if we're in the middle of a command.
            if (command != null)
                return true;

            return false;
        }

        public boolean keyPressed(VisCanvas vc, KeyEvent e)
        {
            synchronized(VisConsole.this) {
                return keyPressedReal(vc, e);
            }
        }

        boolean keyPressedReal(VisCanvas vc, KeyEvent e)
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

            // abort entry. (escape or control-C)
            if (code == KeyEvent.VK_ESCAPE || c == 3) {
                command = null;
                commandPos = 0;
                redraw();
                return true;
            }

            // backspace
            if (code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE) {

                String cmd0 = command.substring(0, commandPos);
                String cmd1 = command.substring(commandPos);

                if (alt) {
                    // delete last word, plus any trailing spaces
                    while (cmd0.endsWith(" "))
                        cmd0 = command.substring(0, cmd0.length() - 2);
                    int idx = cmd0.lastIndexOf(" ");
                    if (idx < 0)
                        cmd0 = "";
                    else
                        cmd0 = cmd0.substring(0, idx+1); // keep the space

                } else {
                    // delete last char
                    if (cmd0.length() > 0)
                        cmd0 = cmd0.substring(0, cmd0.length()-1);
                }
                commandPos = cmd0.length();
                command = cmd0 + cmd1;
                redraw();
                return true;
            }

            // control-A
            if (c==1) {
                commandPos = 0;
                redraw();
                return true;
            }

            // control-D
            if (c==4) {
                if (command.length() > commandPos) {
                    String cmd0 = command.substring(0, commandPos);
                    String cmd1 = command.substring(commandPos);

                    command = cmd0 + cmd1.substring(1);
                }
                redraw();
                return true;
            }

            // control-E
            if (c==5) {
                commandPos = command.length();
                redraw();
                return true;
            }

            // control-K
            if (c==11) {
                command = command.substring(0, commandPos);
                redraw();
                return true;
            }

            // left arrow
            if (code == KeyEvent.VK_LEFT) {
                if (alt) {
                    int moved = 0;
                    while (commandPos > 0) {
                        if (command.charAt(commandPos-1)!=' ' || moved==0) {
                            commandPos--;
                            moved++;
                            continue;
                        }
                        break;
                    }
                } else {
                    commandPos = Math.max(0, commandPos - 1);
                }
                redraw();
                return true;
            }

            // right arrow
            if (code == KeyEvent.VK_RIGHT) {
                // todo: alt
                if (alt) {
                    int moved = 0;
                    while (commandPos+1 < command.length()) {
                        if (command.charAt(commandPos+1) != ' ' || moved==0) {
                            commandPos++;
                            moved++;
                            continue;
                        }
                        break;
                    }
                }

                commandPos = Math.min(command.length(), commandPos + 1);
                redraw();
                return true;
            }

            // up arrow
            if (code == KeyEvent.VK_UP) {
                if (historyIdx < 0) {
                    historyUndo = command;
                    historyIdx = history.size();
                }

                historyIdx = Math.max(0, historyIdx - 1);
                if (historyIdx >= 0) {
                    if (history.size() > 0)
                        command = history.get(historyIdx);
                }

                commandPos = command.length();
                redraw();
                return true;
            }

            // down arrow
            if (code == KeyEvent.VK_DOWN) {
                if (historyIdx >= 0) {
                    historyIdx = Math.min(history.size(), historyIdx + 1);
                    if (historyIdx == history.size())
                        command = historyUndo;
                    else
                        command = history.get(historyIdx);
                }

                commandPos = command.length();
                redraw();
                return true;
            }

            // todo: alt-D
            if ((c == 'D' || c=='d') && alt) {
                String cmd0 = command.substring(0, commandPos);
                String cmd1 = command.substring(commandPos);

                int removed = 0;
                while (cmd1.length() > 0) {
                    if (cmd1.charAt(0) != ' ' || removed == 0) {
                        cmd1 = cmd1.substring(1);
                        removed++;
                    } else {
                        break;
                    }
                }

                command = cmd0 + cmd1;
                redraw();
                return true;
            }

            // end of line
            if (c=='\n' || c=='\r') {
                output(OLD_INPUT_STYLE + ":" + command);
                handleCommand(command);
                if (history.size() == 0 || !history.get(history.size()-1).equals(command))
                    history.add(command);
                historyIdx = -1;
                command = null;
                commandPos = 0;
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
            if (c >=32 && c < 176) {
                String cmd0 = command.substring(0, commandPos);
                String cmd1 = command.substring(commandPos);
                command = cmd0 + c + cmd1;
                commandPos++;
            }

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
