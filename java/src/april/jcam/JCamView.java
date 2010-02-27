package april.jcam;

import april.util.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

import java.io.*;
import java.util.*;

/** JCam example application. **/
public class JCamView
{
    JFrame jf;
    JImage jim;
    JList  cameraList;
    JList  formatList;
    JSlider whitebalance_b;
    JSlider whitebalance_r;

    ImageSource isrc;

    JLabel infoLabel = new JLabel("(please wait)");

    ArrayList<String> urls;
    RunThread runThread;

    MyMouseListener mouseListener;

    public JCamView(ArrayList<String> urls)
    {
        this.urls = urls;

        jf = new JFrame("JCamView");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setLayout(new BorderLayout());

        jim = new JImage();
        jf.add(jim, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout());
        bottomPanel.add(infoLabel);

        mouseListener = new MyMouseListener();
        jim.addMouseMotionListener(mouseListener);

        cameraList = new JList(urls.toArray(new String[0]));
        formatList = new JList(new String[0]);

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

        // white balance sliders and labels
        JLabel wbbLabel = new JLabel("White balance: blue");
        JLabel wbrLabel = new JLabel("White balance: red ");

        whitebalance_b = new JSlider(JSlider.HORIZONTAL,
                                     0, 1023, 512);
        whitebalance_r = new JSlider(JSlider.HORIZONTAL,
                                     0, 1023, 512);

        whitebalance_b.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e){
                whiteBalanceChanged();
            }
        });
        whitebalance_r.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e){
                whiteBalanceChanged();
            }
        });

        cameraList.setSelectedIndex(0); // will trigger call to cameraChanged().

        JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(cameraList), new JScrollPane(formatList));
        jsp.setDividerLocation(0.25);
        jsp.setResizeWeight(0.25);

        infoLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        jf.add(jsp, BorderLayout.WEST);

        JSplitPane jsp_wbb = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, wbbLabel, whitebalance_b);
        JSplitPane jsp_wbr = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, wbrLabel, whitebalance_r);

        JSplitPane jsp_wb = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jsp_wbb, jsp_wbr);
        JSplitPane jsb_south = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jsp_wb, bottomPanel);
        jf.add(jsb_south, BorderLayout.SOUTH);

        jf.setSize(600,400);
        jf.setVisible(true);
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

        int r, b;
        r = isrc.getWhiteBalance('r');
        b = isrc.getWhiteBalance('b');
        if(r != -1 && b != -1)
        {
            whitebalance_r.setValue(r);
            whitebalance_b.setValue(b);
        }

        // update the list of formats.
        ArrayList<String> fmts = new ArrayList<String>();
        for (int i = 0; i < isrc.getNumFormats(); i++) {
            ImageSourceFormat fmt = isrc.getFormat(i);
            fmts.add(String.format("%d x %d (%s)", fmt.width, fmt.height, fmt.format));
        }

        formatList.setListData(fmts.toArray(new String[0]));

        formatList.setSelectedIndex(0); // will trigger call to formatChanged.
    }

    synchronized void formatChanged()
    {
        stopRunThread();

        int idx = formatList.getSelectedIndex();

        System.out.println("set format "+idx);
        if (idx < 0)
            return;

        isrc.setFormat(idx);

        runThread = new RunThread();
        runThread.start();
    }

    synchronized void whiteBalanceChanged()
    {
        int r = whitebalance_r.getValue();
        int b = whitebalance_b.getValue();
        isrc.setWhiteBalance(r, b);
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
            // averaging spf woudl give 0.5. As a (related) plus,
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

                if (true) {
                    long frame_mtime = System.currentTimeMillis();
                    double dt = (frame_mtime - last_frame_mtime)/1000.0;
                    spf = spf*spfAlpha + dt*(1.0-spfAlpha);
                    last_frame_mtime = frame_mtime;

                    int rgb = 0;
                    if (mouseListener != null && mouseListener.imx >= 0 && mouseListener.imx < im.getWidth() && mouseListener.imy >= 0 && mouseListener.imy < im.getHeight())
                        rgb = im.getRGB(mouseListener.imx, mouseListener.imy) & 0xffffff;

                    if (frame_mtime - last_info_mtime > 100) {
                        infoLabel.setText(String.format("fps: %6.2f, RGB: %02x %02x %02x, WB b: %d r: %d", 1.0/(spf+0.00001), (rgb>>16)&0xff, (rgb>>8)&0xff, rgb&0xff,
                                                        isrc.getWhiteBalance('b'), isrc.getWhiteBalance('r')));
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
        System.out.println("java.library.path: "+System.getProperty("java.library.path"));

        System.out.println("Found cameras: ");
        ArrayList<String> urls = ImageSource.getCameraURLs();
        for (String s : urls)
            System.out.println("  "+s);

        if (urls.size() == 0) {
            System.out.println("No cameras found.");
            return;
        }

        for (String arg : args)
            urls.add(arg);

        new JCamView(urls);
    }
}
