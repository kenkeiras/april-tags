package april.jcam;

import april.util.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.imageio.*;

import java.io.*;
import java.util.*;

/** JCam example application. **/
public class JCamView
{
    JFrame jf;
    JImage jim;
    JList  cameraList;
    JList  formatList;
    JPanel featurePanel;
    JSlider whitebalance_b;
    JSlider whitebalance_r;

    ImageSource isrc;

    JLabel infoLabel = new JLabel("(please wait)");

    ArrayList<String> urls;
    RunThread runThread;

    MyMouseListener mouseListener;

    JPanel mainPanel;
    JPanel leftPanel;

    RecordPanel  recordPanel = new RecordPanel();
    PrintPanel   printPanel = new PrintPanel();
    JPanel     triggerPanel = makeChoicePanel("Software trigger thread", new TriggerPanel());

    GetOpt gopt;

    public JCamView(ArrayList<String> urls, GetOpt gopt)
    {
        this.urls = urls;
        this.gopt = gopt;

        jf = new JFrame("JCamView");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setLayout(new BorderLayout());

        jim = new JImage();
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(jim, BorderLayout.CENTER);

        mouseListener = new MyMouseListener();
        jim.addMouseMotionListener(mouseListener);

        cameraList = new JList(urls.toArray(new String[0]));
        formatList = new JList(new String[0]);
        featurePanel = new JPanel();

        cameraList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                cameraChanged();
            }
	    });

        formatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        formatList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                formatChanged();
            }
	    });

        cameraList.setSelectedIndex(0); // will trigger call to cameraChanged().

        infoLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

        leftPanel = new JPanel();
        int vspace = 15;
        leftPanel.setLayout(new VFlowLayout());
        cameraList.setFixedCellWidth(40); // prevent long camera urls from
                                          // screwing up the panel width
        leftPanel.add(makeChoicePanel("Cameras", cameraList));
        leftPanel.add(Box.createVerticalStrut(vspace));
        leftPanel.add(makeChoicePanel("Formats", formatList));
        leftPanel.add(Box.createVerticalStrut(vspace));

        leftPanel.add(Box.createVerticalStrut(vspace));
        leftPanel.add(makeChoicePanel("Controls", featurePanel));
        leftPanel.add(Box.createVerticalStrut(vspace));
        leftPanel.add(makeChoicePanel("Record", recordPanel));
        leftPanel.add(Box.createVerticalStrut(vspace));
        leftPanel.add(makeChoicePanel("Camera Info", printPanel));

        leftPanel.add(triggerPanel);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout());
        bottomPanel.add(infoLabel);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(leftPanel), mainPanel);
        jsp.setDividerLocation(30 + (int) leftPanel.getPreferredSize().getWidth());
        jsp.setResizeWeight(0.2);

        jf.add(jsp, BorderLayout.CENTER);

        int width = Math.min(1000,
                             800 + (int) leftPanel.getPreferredSize().getWidth() + 30);
        jf.setSize(width,600);
        jf.setVisible(true);
    }

    JPanel indentPanel(JComponent c)
    {
        JPanel jp = new JPanel();
        jp.setLayout(new BorderLayout());
        jp.add(c, BorderLayout.CENTER);
        jp.add(Box.createHorizontalStrut(20), BorderLayout.WEST);
        return jp;
    }

    JPanel makeChoicePanel(String title, JComponent c)
    {
        JPanel jp = new JPanel();
        jp.setLayout(new BorderLayout());
        jp.add(c, BorderLayout.CENTER);
        jp.setBorder(BorderFactory.createTitledBorder(title));
        return jp;
    }

    class TriggerPanel extends JPanel implements ActionListener, ChangeListener
    {
        JButton toggleButton = new JButton("Start thread");
        JSlider fpsSlider = new JSlider(1, 100, 30);
        JLabel valueLabel = new JLabel(fpsSlider.getValue()+" FPS");

        TriggerThread tThread = null;

        public TriggerPanel()
        {
            setLayout(new BorderLayout());

            add(fpsSlider, BorderLayout.CENTER);
            add(valueLabel, BorderLayout.EAST);
            add(toggleButton, BorderLayout.SOUTH);

            fpsSlider.addChangeListener(this);
            toggleButton.addActionListener(this);
        }

        void updateLabel()
        {
            valueLabel.setText(fpsSlider.getValue()+" FPS");
        }

        int computeDelay()
        {
            return (int) Math.round(1000.0/fpsSlider.getValue());
        }

        public synchronized void actionPerformed(ActionEvent e)
        {
            if (e.getSource() == toggleButton) {
                if (tThread == null) {
                    tThread = new TriggerThread(computeDelay());
                    tThread.start();
                    toggleButton.setText("Stop thread");
                } else {
                    tThread.stopRequest();
                    tThread = null;
                    toggleButton.setText("Start thread");
                }
            }
        }

        public synchronized void stateChanged(ChangeEvent e)
        {
            if (e.getSource() == fpsSlider) {
                if (tThread != null)
                    tThread.setDelay(computeDelay());

                updateLabel();
            }
        }
    }
    class PrintPanel extends JPanel
    {
        JButton printButton = new JButton("Print details");
        JButton printURLButton = new JButton("Print camera URL");

        public PrintPanel()
        {
            setLayout(new VFlowLayout());

            add(printButton);
            add(printURLButton);

            printButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    isrc.printInfo();
                }});

            printURLButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {

                    String camera = urls.get(cameraList.getSelectedIndex());

                    String url = String.format("%s?fidx=%d", camera, isrc.getCurrentFormatIndex());

                    for (int i=0; i < isrc.getNumFeatures(); i++) {
                        String key = isrc.getFeatureName(i);
                        double value = isrc.getFeatureValue(i);

                        if (value == (int) value)
                            url = String.format("%s&%s=%.0f", url,
                                                key, value);
                        else
                            url = String.format("%s&%s=%.2f", url,
                                                key, value);
                    }

                    System.out.printf("Camera url: \"%s\"\n", url);
                }});
        }
    }

    class RecordPanel extends JPanel implements ActionListener, ChangeListener
    {
        JTextField pathBox = new JTextField("/tmp/jcam-capture/camera.islog");

        JButton startStopButton = new JButton("Record");

        boolean recording = false;

        JSlider fpsSlider = new JSlider(1, 100, 10);
        JLabel fpsLabel = new JLabel("");

        long firstms;
        long lastms = System.currentTimeMillis();
        int framesWritten = 0;
        JLabel framesWrittenLabel = new JLabel("");

        JComboBox formatComboBox = new JComboBox(new String[] { "Log file" , "Individual PNGs" });

        ISLog logWriter;

        public RecordPanel()
        {
            setLayout(new VFlowLayout());

            add(new JLabel("Maximum frame rate: "));
            JPanel fpsPanel = new JPanel();
            fpsPanel.setLayout(new BorderLayout());
            fpsPanel.add(fpsSlider, BorderLayout.CENTER);
            fpsPanel.add(fpsLabel, BorderLayout.EAST);
            fpsSlider.addChangeListener(this);
            add(indentPanel(fpsPanel));

            add(new JLabel("Format: "));
            add(indentPanel(formatComboBox));

            add(new JLabel("Destination path: "));
            add(indentPanel(pathBox));


            add(Box.createVerticalStrut(10));
            add(startStopButton);

            add(framesWrittenLabel);

            stateChanged(null);
            startStopButton.addActionListener(this);
        }

        public void stateChanged(ChangeEvent e)
        {
            fpsLabel.setText(String.format("%4d", fpsSlider.getValue()));
        }

        public synchronized void actionPerformed(ActionEvent e)
        {
            recording ^= true;

            if (recording) {
                framesWritten = 0;
                firstms = System.currentTimeMillis();

                if (formatComboBox.getSelectedIndex() == 0) {
                    new File(pathBox.getText()).getParentFile().mkdirs();

                    // log format
                    try {
                        logWriter = new ISLog(pathBox.getText(), "rw");
                    } catch (IOException ex) {
                        recording = false;
                        System.out.println("ex: "+ex);
                        return;
                    }

                } else {
                    new File(pathBox.getText()).mkdirs();

                    // directory of PNGs

                    // nothing to do here.
                }

                startStopButton.setText("Stop");
                pathBox.setEnabled(false);
                formatComboBox.setEnabled(false);
            } else {
                if (formatComboBox.getSelectedIndex() == 0) {
                    try {
                        logWriter.close();
                    } catch (IOException ex) {
                        System.out.println("ex: "+ex);
                    }
                } else {
                }

                startStopButton.setText("Record");
                formatComboBox.setEnabled(true);
                pathBox.setEnabled(true);
            }
        }

        public synchronized void handleImage(BufferedImage im, ImageSourceFormat ifmt, byte imbuf[])
        {
            if (!recording)
                return;

            long nowms = System.currentTimeMillis();
            long dms = 1000 / fpsSlider.getValue();

            if (nowms >= lastms + dms) {
                lastms = nowms;

                try {
                    if (formatComboBox.getSelectedIndex() == 0) {
                        logWriter.write(ifmt, imbuf);
                    } else {
                        String path = String.format("%s/image_%06d.png", pathBox.getText(), nowms - firstms);
                        ImageIO.write(im, "png", new File(path));
                        framesWritten++;
                        framesWrittenLabel.setText(String.format("%d frames written", framesWritten));
                    }

                } catch (IOException ex) {
                    System.out.println("Ex: "+ex);
                }
            }
        }
    }

    class MyMouseListener implements MouseMotionListener
    {
        int imx, imy;

        public void mouseMoved(MouseEvent e)
        {
            Point2D xy = jim.componentToImage(e.getPoint());
            BufferedImage im = jim.getImage();

            imx = (int) xy.getX();
            imy = (int) xy.getY();
        }

        public void mouseDragged(MouseEvent e)
        {
        }
    }

    class FeatureControl extends JPanel implements ChangeListener, ActionListener
    {
        String name;
        double min, max, value;
        double scale = 100;
        ImageSource isrc;
        int idx;

        JLabel valueLabel = new JLabel();
        JSlider js;
        JCheckBox jcb;

        public FeatureControl(ImageSource isrc, int idx)
        {
            this.isrc = isrc;
            this.idx = idx;

            min = isrc.getFeatureMin(idx);
            max = isrc.getFeatureMax(idx);
            value = isrc.getFeatureValue(idx);

            if (gopt.getBoolean("verbose"))
                System.out.printf("Feature %2d : %30s (%10.4f, %10.4f, %10.4f)\n", idx, isrc.getFeatureName(idx), min, max, value);

            setLayout(new BorderLayout());

            if (min==0 && max==1) {
                jcb = new JCheckBox(isrc.getFeatureName(idx), value==1);
                jcb.addActionListener(this);
                add(jcb, BorderLayout.CENTER);
            } else {
                double safeValue = Math.min(Math.max(value, min), max);
                js = new JSlider((int) (min*scale), (int) (max*scale), (int) (safeValue*scale));
                updateSlider();

                js.addChangeListener(this);
                add(new JLabel(isrc.getFeatureName(idx)), BorderLayout.NORTH);
                add(js, BorderLayout.CENTER);
                add(valueLabel, BorderLayout.EAST);
            }

            updateLabel();
        }

        void updateLabel()
        {
            value = isrc.getFeatureValue(idx);
            if (value == (int) value)
                valueLabel.setText(""+value);
            else
                valueLabel.setText(String.format("%.2f", value));
            valueLabel.setVerticalAlignment(SwingConstants.NORTH);
        }

        public void actionPerformed(ActionEvent e)
        {
            if (jcb != null) {
                if (gopt.getBoolean("verbose"))
                    System.out.printf("isSelected: %s\n", jcb.isSelected() ? "yes" : "no");
                int res = isrc.setFeatureValue(idx, jcb.isSelected() ? 1 : 0);
                jcb.setSelected(isrc.getFeatureValue(idx) == 1 ? true : false);
                if (res != 0)
                    System.out.println("Error setting feature");
            }
            updateLabel();
        }

        private void updateSlider()
        {
            // extents
            js.setMinimum((int) (min*scale));
            js.setMaximum((int) (max*scale));

            // label table
            Hashtable labelTable = new Hashtable();

            JLabel minLabel = new JLabel(String.format("%.2f", min));
            JLabel maxLabel = new JLabel(String.format("%.2f", max));

            Font f = minLabel.getFont();
            f = new Font( f.getName(), f.getStyle(), f.getSize() - 3);
            minLabel.setFont(f);
            maxLabel.setFont(f);

            labelTable.put(new Integer((int) (min*scale)), minLabel);
            labelTable.put(new Integer((int) (max*scale)), maxLabel);

            js.setLabelTable(labelTable);
            js.setPaintLabels(true);
        }

        public void stateChanged(ChangeEvent e)
        {
            if (js != null) {
                int res = isrc.setFeatureValue(idx, js.getValue()/scale);

                min = isrc.getFeatureMin(idx);
                max = isrc.getFeatureMax(idx);

                updateSlider();

                if (gopt.getBoolean("verbose"))
                    System.out.printf("%s: value: desired %f actual %f min/max: (%f, %f)\n",
                                      isrc.getFeatureName(idx),
                                      js.getValue()/scale,
                                      isrc.getFeatureValue(idx),
                                      min, max);
                if (res != 0)
                    System.out.println("Error setting feature");
            }

            updateLabel();
        }
    }

    synchronized void cameraChanged()
    {
        stopRunThread();

        String url = urls.get(cameraList.getSelectedIndex());
        System.out.println("set camera: "+url);

        if (isrc != null)
            isrc.close();

        try {
            isrc = ImageSource.make(url);
        } catch (IOException ex) {
            System.out.println("Ex: "+ex);
            return;
        }

        if (true) {
            featurePanel.removeAll();
            int nfeatures = isrc.getNumFeatures();
            featurePanel.setLayout(new GridLayout(nfeatures, 1));

            for (int i = 0; i < nfeatures; i++) {
                featurePanel.add(new FeatureControl(isrc, i));
            }

            if (nfeatures == 0)
                featurePanel.add(new JLabel("No features available"));


            // show the trigger panel iff the camera supports software triggering.
            boolean triggering = false;
            for (int i = 0; i < nfeatures; i++) {
                if (isrc.getFeatureName(i).equals("software-trigger"))
                    triggering = true;
            }

            triggerPanel.setVisible(triggering);
        }

        // update the list of formats.
        ArrayList<String> fmts = new ArrayList<String>();
        for (int i = 0; i < isrc.getNumFormats(); i++) {
            ImageSourceFormat fmt = isrc.getFormat(i);
            fmts.add(String.format("%d x %d (%s)", fmt.width, fmt.height, fmt.format));
        }

        formatList.setListData(fmts.toArray(new String[0]));

        formatList.setSelectedIndex(isrc.getCurrentFormatIndex()); // will trigger call to formatChanged.
    }

    synchronized void formatChanged()
    {
        stopRunThread();

        int idx = formatList.getSelectedIndex();

        if (gopt.getBoolean("verbose"))
            System.out.println("set format "+idx);

        if (idx < 0)
            return;

        isrc.setFormat(idx);

        runThread = new RunThread();
        runThread.start();
    }

    synchronized void stopRunThread()
    {
        if (runThread != null)  {
            runThread.stopRequest = true;
            try {
                runThread.join();
            } catch (InterruptedException ex) {
                System.out.println("ex: "+ex);
            }
            runThread = null;
        }
    }

    class TriggerThread extends Thread
    {
        int delay;
        int idx = -1;
        boolean stop = false;

        public TriggerThread(int delay)
        {
            this.delay = delay;

            for (int i=0; i < isrc.getNumFeatures(); i++) {
                if (isrc.getFeatureName(i).equals("software-trigger"))
                    idx = i;
            }

            if (idx == -1)
                System.out.println("Software trigger not supported for image source");
        }

        public void setDelay(int delay)
        {
            this.delay = delay;
        }

        public void stopRequest()
        {
            stop = true;
        }

        public void run()
        {
            if (idx == -1)
                return;

            long t0, t1;
            t0 = TimeUtil.utime();
            t1 = TimeUtil.utime();
            while (!stop) {
                t1 = TimeUtil.utime();
                int ms = (int) (delay - (t1 - t0)/1e3);
                TimeUtil.sleep(ms);

                t0 = TimeUtil.utime();
                int res = isrc.setFeatureValue(idx, 1);
            }
        }
    }

    class RunThread extends Thread
    {
        boolean stopRequest = false;

        public void run()
        {
            isrc.start();
            // use seconds per frame, not frames per second, because
            // linearly blending fps measures gives funny
            // results. E.g., suppose frame 1 takes 1 second, frame 2
            // takes 0 seconds. Averaging fps would give INF,
            // averaging spf would give 0.5. As a (related) plus,
            // there's no risk of a divide-by-zero.
            double spf = 0;
            double spfAlpha = 0.95;

            ImageSourceFormat ifmt = isrc.getCurrentFormat();
            long last_frame_mtime = System.currentTimeMillis();

            long last_info_mtime = System.currentTimeMillis();

            while (!stopRequest) {
                byte imbuf[] = null;
                BufferedImage im = null;

                imbuf = isrc.getFrame();
                if (imbuf == null) {
                    System.out.println("getFrame() failed");
                    continue;
                }

                im = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, imbuf);
                if (im == null)
                    continue;

                jim.setImage(im);
                recordPanel.handleImage(im, ifmt, imbuf);

                if (true) {
                    long frame_mtime = System.currentTimeMillis();
                    double dt = (frame_mtime - last_frame_mtime)/1000.0;
                    spf = spf*spfAlpha + dt*(1.0-spfAlpha);
                    last_frame_mtime = frame_mtime;

                    int rgb = 0;
                    if (mouseListener != null && mouseListener.imx >= 0 && mouseListener.imx < im.getWidth() && mouseListener.imy >= 0 && mouseListener.imy < im.getHeight())
                        rgb = im.getRGB(mouseListener.imx, mouseListener.imy) & 0xffffff;

                    if (frame_mtime - last_info_mtime > 100) {
                        infoLabel.setText(String.format("fps: %6.2f, RGB: %02x %02x %02x (%3d, %3d, %3d)",
                                                        1.0/(spf+0.00001),
                                                        (rgb>>16)&0xff, (rgb>>8)&0xff, rgb&0xff,
                                                        (rgb>>16)&0xff, (rgb>>8)&0xff, rgb&0xff));
                        last_info_mtime = frame_mtime;
                    }

                    last_frame_mtime = frame_mtime;
                }

                if (imbuf == null) {
                    System.out.println("get frame failed");
                    continue;
                }

                Thread.yield();
            }

            isrc.stop();
        }
    }

    public static void main(String args[])
    {
        GetOpt gopt = new GetOpt();

        gopt.addBoolean('v', "verbose", false, "Be verbose");
        gopt.addBoolean('h', "help", false, "Show this help");

        if (!gopt.parse(args) || gopt.getBoolean("help")) {
            System.out.println("Usage: [options] [camera-url]");
            gopt.doHelp();
            System.exit(0);
        }

        if (gopt.getBoolean("verbose"))
            System.out.println("java.library.path: "+System.getProperty("java.library.path"));

        // make a list of URLs that are available.
        ArrayList<String> urls = new ArrayList<String>();

        // Any manually specified URLs get added to the list of available URLs.
        for (String url : gopt.getExtraArgs()) {
            urls.add(url);
        }

        // Search for other camera devices that might be available and add them.
        for (String s : ImageSource.getCameraURLs()) {
            if (!urls.contains(s))
                urls.add(s);
        }

        if (urls.size() == 0) {
            System.out.println("No image sources found or specified on command line.");
            return;
        }

        new JCamView(urls, gopt);
    }
}
