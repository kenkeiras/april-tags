package april.procman;

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.*;
import javax.swing.text.*;

import lcm.lcm.*;
import april.lcmtypes.*;

import april.util.*;

/**
 * Issues:
 * 1. Auto-scrolling is in a funny state, and must be done manually
 * because it is hard to find out if the pane isn't at the end because
 * a user wants to scroll to the middle, or because new content was
 * added. Particularly, adding output to a Document doesnt seem to be
 * reflected immediately in the JScrollBar, so before adding input, if
 * the view is not at the end, it may be because of content which was
 * added last time, yet we didn't find out about it soon enough to
 * update the run that time, vs. if the change was due to the user.
 * 2. Document styling changes appear to occur when a buffer becomes
 * the selected one.  -JHS
 */
class Spy implements LCMSubscriber
{
    public static final int WIN_WIDTH = 1024;
    public static final int WIN_HEIGHT = 600;
    public static final int HOST_WIDTH = 250;

    JFrame    jf;
    JTable    proctable, hosttable;
    JTextPane textSelected, textError;
    JScrollPane textSelectedScroll, textErrorScroll;

    JButton   startSelectedButton, stopSelectedButton, clearButton;
    JCheckBox autoScrollBox;

    ProcGUIDocument outputSummary = new ProcGUIDocument();

    ProcMan proc;

    ArrayList<ProcRecordG> processes;
    HashMap<Integer, ProcRecordG> processesMap;
    ArrayList<HostRecord> hosts = new ArrayList<HostRecord>();

    static LCM lcm = LCM.getSingleton();

    ProcessTableModel processTableModel = new ProcessTableModel();
    HostTableModel hostTableModel = new HostTableModel();

    boolean scrollToEnd;

    Spy(ProcMan _proc)
    {
        proc = _proc;
        init();
    }

    Spy()
    {
        proc = null;
        init();
    }

    public void init()
    {
        processes = new ArrayList<ProcRecordG>();
        processesMap = new HashMap<Integer, ProcRecordG>();

        proctable = new JTable(processTableModel);
        proctable.setRowSorter(new TableRowSorter(processTableModel));

        // allow section of processes via mouse or keyboard (up and down) keys
        ListSelectionModel rowSM = proctable.getSelectionModel();
        rowSM.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    if (e.getValueIsAdjusting())
                        return;
                    updateTableSelection();
                }
            });

        hosttable = new JTable(hostTableModel);
        hosttable.setRowSorter(new TableRowSorter(hostTableModel));

        textSelected = new JTextPane();
        textSelected.setEditable(false);
        textSelected.setDocument(outputSummary);

        textError = new JTextPane();
        textError.setEditable(false);
        textError.setDocument(outputSummary);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1,5));
        startSelectedButton = new JButton("Start Selected");
        stopSelectedButton = new JButton("Stop Selected");
        clearButton = new JButton("Clear Output");
        scrollToEnd = true;
        autoScrollBox = new JCheckBox("Auto Scroll", scrollToEnd);

        startSelectedButton.setEnabled(false);
        stopSelectedButton.setEnabled(false);

        buttonPanel.add(startSelectedButton);
        buttonPanel.add(stopSelectedButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(autoScrollBox);

        if (proc != null) {
            startSelectedButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        int []rows = proctable.getSelectedRows();
                        for (int i = 0; i < rows.length; i++) {
                            rows[i] = proctable.convertRowIndexToModel(rows[i]);
                            proc.setRunStatus(processes.get(rows[i]).procid, true);
                        }
                        updateStartStopText();
                    }
                });
            stopSelectedButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        int []rows = proctable.getSelectedRows();
                        for (int i = 0; i < rows.length; i++) {
                            rows[i] = proctable.convertRowIndexToModel(rows[i]);
                            proc.setRunStatus(processes.get(rows[i]).procid, false);
                        }
                        updateStartStopText();
                    }
                });
        }

        clearButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    synchronized(Spy.this)
                    {
                        clear();
                    }
                }
            });

        autoScrollBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    scrollToEnd = !scrollToEnd;
                }
            });

        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                        new JScrollPane(proctable),
                                        new JScrollPane(hosttable));
        jsp.setDividerLocation(WIN_WIDTH - HOST_WIDTH);
        jsp.setResizeWeight(1);

        JPanel jp = new JPanel();
        jp.setLayout(new BorderLayout());
        jp.add(jsp, BorderLayout.CENTER);
        jp.add(buttonPanel, BorderLayout.SOUTH);

        textSelectedScroll = new JScrollPane(textSelected);
        textSelectedScroll.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener(){
                public void adjustmentValueChanged(AdjustmentEvent e){
                    JScrollBar jsb = textSelectedScroll.getVerticalScrollBar();
                    if (scrollToEnd)
                        jsb.setValue(jsb.getMaximum());
                }
            });

        textErrorScroll = new JScrollPane(textError);
        textErrorScroll.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener(){
                public void adjustmentValueChanged(AdjustmentEvent e){
                    JScrollBar jsb = textErrorScroll.getVerticalScrollBar();
                    if (scrollToEnd)
                        jsb.setValue(jsb.getMaximum());
                }
            });
        JSplitPane textJsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                            textErrorScroll, textSelectedScroll);
        JSplitPane leftJsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jp, textJsp);

        textJsp.setDividerLocation(0.4);
        textJsp.setResizeWeight(0.5);

        Dimension minimumSize = new Dimension(300, 100);
        textErrorScroll.setMinimumSize(minimumSize);
        textSelectedScroll.setMinimumSize(minimumSize);

        leftJsp.setDividerLocation(0.3);
        leftJsp.setResizeWeight(0.3);

        jf = new JFrame("ProcMan Spy " +
                        (proc == null ? "(Read Only)" : "(Privileged)"));
        jf.setLayout(new BorderLayout());
        jf.add(leftJsp, BorderLayout.CENTER);

        TableColumnModel tcm = proctable.getColumnModel();
        tcm.getColumn(0).setPreferredWidth(50);
        tcm.getColumn(1).setPreferredWidth(500);
        tcm.getColumn(2).setPreferredWidth(100);
        tcm.getColumn(3).setPreferredWidth(100);
        tcm.getColumn(4).setPreferredWidth(100);
        tcm.getColumn(5).setPreferredWidth(80);

        jf.setSize(WIN_WIDTH, WIN_HEIGHT);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        lcm.subscribe("PROCMAN_STATUS_LIST", this);
        lcm.subscribe("PROCMAN_OUTPUT", this);
        lcm.subscribe("PROCMAN_PROCESS_LIST", this);
    }

    public synchronized void updateTableSelection()
    {
        int []rows = proctable.getSelectedRows();

        // currently only display process output if exactly 1 process selected
        if (rows.length == 1) {
            int row = rows[0];
            row = proctable.convertRowIndexToModel(row);
            textSelected.setDocument(processes.get(row).output);
            textSelected.setCaretPosition(processes.get(row).output.getLength());
        } else {
            textSelected.setDocument(outputSummary);
            textSelected.setCaretPosition(outputSummary.getLength());
        }
        updateStartStopText();
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            messageReceivedEx(lcm, channel, ins);
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }

    public void messageReceivedEx(LCM lcm, String channel, LCMDataInputStream ins) throws IOException
    {
        if (channel.equals("PROCMAN_OUTPUT")) {
            procman_output_t po = new procman_output_t(ins);

            ProcRecordG pr = ensureProcRecord(po.procid); // processesMap.get(po.procid);

            if (pr == null)
                return;

            if (po.stream == 0) {
                pr.output.appendDefault(po.data + "\n");
            } else {
                pr.output.appendError(po.data + "\n");
                outputSummary.appendError(po.data + "\n");
            }
        }else if (channel.equals("PROCMAN_STATUS_LIST")) {
            procman_status_list_t psl = new procman_status_list_t(ins);

            /////////// Update Process Statistics
            for (int i = 0; i < psl.nprocs; i++) {

                procman_status_t ps = psl.statuses[i];

                ProcRecordG pr = ensureProcRecord(ps.procid); // processesMap.get(ps.procid);
                if (pr == null) {
                    System.out.println("unknown procid "+ps.procid);
                    continue;
                }

                pr.lastStatus = ps;
                pr.restartCount = ps.restarts;
                pr.lastStatusUtime = psl.utime;

                // If process returned with exit_code = 0 AND this is
                // a privileged module, then set send status as running=false
                if (!ps.running && ps.last_exit_code == 0)
                    if (proc != null && proc.getRunStatus(pr.procid) && pr.wasDaemonRunning())
                        proc.setRunStatus(pr.procid, false);
                pr.setDaemonRunning(ps.running);

                processTableModel.fireTableRowsUpdated(pr.pridx, pr.pridx);
                updateStartStopText();
            }

            ////////// Update Host Statistics
            HostRecord hr = ensureHost(psl.host);
            hr.skew = psl.utime - TimeUtil.utime();
            hr.rtt = TimeUtil.utime() - psl.received_utime;

            hostTableModel.fireTableCellUpdated(hr.hridx,1);
            hostTableModel.fireTableCellUpdated(hr.hridx,2);
        } else if(channel.equals("PROCMAN_PROCESS_LIST")){
            procman_process_list_t proc_list = new procman_process_list_t(ins);
            for(int i = 0; i < proc_list.nprocs; i++){
                procman_process_t p = proc_list.processes[i];
                ProcRecordG pr = ensureProcRecord(p.procid);
                pr.cmdline = p.cmdline;
                pr.host = p.host;
                pr.name = p.name;
                pr.cmdRunning = p.running;
                processTableModel.fireTableRowsUpdated(pr.pridx, pr.pridx);
            }
        }
    }


    class HostRecord
    {
        String host;

        // how long did it take for it to reply to our last send
        // command? (usecs)
        long   rtt;

        // what is the difference between the utime on dameon and
        // master?  (includes latency) (usecs)
        long   skew;

        int hridx;
    }


    class ProcessTableModel extends AbstractTableModel
    {
        String columnNames[] = { "ProcID", "Command", "Name", "Host", "Status" , "Controller Status" };

        public int getColumnCount()
        {
            return columnNames.length;
        }

        public String getColumnName(int col)
        {
            return columnNames[col];
        }

        public int getRowCount()
        {
            return processes.size();
        }

        public Object getValueAt(int row, int col)
        {
            ProcRecordG pr = processes.get(row);

            switch (col) {
                case 0:
                    return pr.procid;
                case 1:
                    return pr.cmdline;
                case 2:
                    return pr.name;
                case 3:
                    return pr.host;
                case 4:
                    if (pr.lastStatus == null)
                        return "Unknown";
                    else {
                        int exitCode = (pr.lastStatus != null) ? pr.lastStatus.last_exit_code : 0;
                        return pr.lastStatus.running ? "Running" : "Stopped ("+exitCode+")";
                    }
                case 5:
                    return pr.cmdRunning ? "Running" : "Stopped";
            }

            return "??";
        }
    }

    class HostTableModel extends AbstractTableModel
    {
        String columnNames[] = { "Host", "RTT", "Skew" };

        public int getColumnCount()
        {
            return columnNames.length;
        }

        public String getColumnName(int col)
        {
            return columnNames[col];
        }

        public int getRowCount()
        {
            return hosts.size();
        }

        public Object getValueAt(int row, int col)
        {
            HostRecord hr = hosts.get(row);

            switch (col) {
                case 0:
                    return hr.host;
                case 1:
                    return String.format("%.1f ms", hr.rtt/1000.0);
                case 2:
                    return String.format("%.1f ms", hr.skew/1000.0);
            }
            return "??";
        }
    }

    synchronized void clear()
    {
        // XXX need to fire some events here...
        int removedProc  = processes.size();
        if (removedProc == 0)
            return;
        processes.clear();
        processesMap.clear();

        int removedHost  = hosts.size();
        hosts.clear();

        processTableModel.fireTableRowsDeleted(0,removedProc - 1);
        hostTableModel.fireTableRowsDeleted(0,removedHost - 1);
    }

    synchronized ProcRecordG ensureProcRecord(int procid)
    {
        ProcRecordG pr = processesMap.get(procid);
        if (pr != null)
            return pr;

        // otherwise, we've got some data to create and fill in
        pr = new ProcRecordG();
        pr.procid = procid;
        pr.cmdline = "???";
        pr.host = "???";
        pr.name = "???";
        pr.pridx = processes.size();
        pr.cmdRunning = false;

        processes.add(pr);
        processesMap.put(procid, pr);
        processTableModel.fireTableRowsInserted(processes.size() - 1,
                                                processes.size() - 1);

        return pr;
    }

    synchronized HostRecord ensureHost(String hostStr)
    {
        for (HostRecord host : hosts)
            if (host.host.equals(hostStr))
                return host;

        HostRecord hr = new HostRecord();
        hr.host = hostStr;
        hr.hridx = hosts.size();
        hosts.add(hr);
        hostTableModel.fireTableRowsInserted(hr.hridx, hr.hridx);
        return hr;
    }

    /**
     * This method should ideally be called whenever the GUI state of
     * running is changed for a process in row 'row'
     */
    void updateStartStopText()
    {
        if (proc == null)
            return;

        // check all selected rows
        int []rows = proctable.getSelectedRows();
        int numRunning = 0;
        int numStopped = 0;

        for (int i = 0; i < rows.length; i++) {
            int procid =  processes.get(proctable.convertRowIndexToModel(rows[i])).procid;
            if (proc.getRunStatus(procid))
                numRunning++;
            else
                numStopped++;
        }
        // if all selected processes are running then disable setRunning button
        startSelectedButton.setEnabled(numRunning < rows.length);

        // if all selected processes are stopped then disable setStopped button
        stopSelectedButton.setEnabled(numStopped < rows.length);
    }

    class ProcRecordG extends ProcRecord
    {
        ProcGUIDocument output;
        int pridx;

        procman_status_t lastStatus;
        long lastStatusUtime;

        boolean cmdRunning;   // commanded running state from controller (different from status)

        // was process running on last status message.  Used to handle exit code = 0
        boolean daemonIsRunning;

        ProcRecordG()
        {
            output = new ProcGUIDocument();
        }

        void setDaemonRunning(boolean daemonIsRunning)
        {
            this.daemonIsRunning = daemonIsRunning;
        }

        boolean wasDaemonRunning()
        {
            return daemonIsRunning;
        }
    }

    class ProcGUIDocument extends DefaultStyledDocument
    {
        Style defaultStyle, errorStyle, summaryStyle;

        static final int MAX_LENGTH = 128*1024;

        ProcGUIDocument()
        {
            defaultStyle = getStyle(StyleContext.DEFAULT_STYLE);
            StyleConstants.setFontFamily(defaultStyle, "Monospaced");
            StyleConstants.setFontSize(defaultStyle, 10);

            errorStyle   = addStyle("ERROR", defaultStyle);
            StyleConstants.setFontFamily(errorStyle, "Monospaced");
            StyleConstants.setFontSize(errorStyle, 10);
            StyleConstants.setForeground(errorStyle, Color.red);

            summaryStyle = addStyle("SUMMARY", defaultStyle);
            StyleConstants.setFontFamily(summaryStyle, "Monospaced");
            StyleConstants.setFontSize(summaryStyle, 10);
            StyleConstants.setForeground(summaryStyle, Color.blue);
        }

        void insertStringEx(int pos, String s, Style style)
        {
            // avoid synchrony with UpdateTableSelection, which causes an exception.
            synchronized(Spy.this) {

                try {
                    if (getLength() > MAX_LENGTH) {
                        remove(0, MAX_LENGTH / 10);
                    }

                    insertString(getLength(), s, style);
                } catch (Exception ex) {
                    System.out.print("caught: ");
                    ex.printStackTrace();
                }
            }
        }

        void appendDefault(String s)
        {
            insertStringEx(getLength(), s, defaultStyle);
        }

        void appendError(String s)
        {
            insertStringEx(getLength(), s, errorStyle);
        }

        void appendSummary(String s)
        {
            insertStringEx(getLength(), s, summaryStyle);
        }

    }

    public static void main(String args[])
    {
        Spy pg = new Spy();
    }

}
