package april.util;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.io.*;
import java.util.*;

/** A slider that has a "current" value, and a "goal" value. The user
    controls the goal value, but has only indirect control over the
    current value. **/

public class FeedbackSlider extends JComponent
{
    int barheight=8;

    double minvalue;
    double maxvalue;

    double goalvalue, actualvalue;

    int goalknobsize=6;
    int actualknobsize=10;

    int totalheight=actualknobsize+4;

    int marginx=6;

    ArrayList<Listener> gsls=new ArrayList<Listener>();

    boolean copyactual=false;

    boolean showactual=true;

    String goalFormatString = "%.0f";
    String actualFormatString = "%.0f";

    public interface Listener
    {
        public void goalValueChanged(FeedbackSlider ss, double goalvalue);
    }

    public FeedbackSlider(double min, double max, double goalvalue, double actualvalue, boolean showactual)
    {
        this.minvalue = min;
        this.maxvalue = max;

        this.goalvalue = goalvalue;
        this.actualvalue = actualvalue;
        this.showactual = showactual;

        FeedbackSliderMouseMotionListener listener = new FeedbackSliderMouseMotionListener();

        addMouseMotionListener(listener);
        addMouseListener(listener);
    }

    /** a printf format specification. **/
    public void setFormatStrings(String goalFormat, String actualFormat)
    {
        goalFormatString = goalFormat;
        actualFormatString = actualFormat;
    }

    public void addListener(Listener gsl)
    {
        gsls.add(gsl);
    }

    public void setMaximum(double v)
    {
        maxvalue = v;
        repaint();
    }

    public void setMinimum(double v)
    {
        minvalue = v;
        repaint();
    }

    public synchronized void setActualValue(double v)
    {
        if (copyactual)
	    {
            goalvalue=v;
            copyactual=false;
	    }

        actualvalue=v;
        repaint();
    }

    /** Only call this during initialization; it should be under user control only! **/
    public void setGoalValue(double v)
    {
        goalvalue = v;
        repaint();
    }

    public double getGoalValue()
    {
        return goalvalue;
    }

    public double getActualValue()
    {
        return actualvalue;
    }

    public Dimension getMinimumSize()
    {
        return new Dimension(40, totalheight);
    }

    public Dimension getPreferredSize()
    {
        return getMinimumSize();
    }

    public synchronized void paint(Graphics gin)
    {
        Graphics2D g = (Graphics2D) gin;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int height = getHeight();
        int width = getWidth() - 2 * marginx;
        double cy = height / 2.0;
        double cx = width / 2.0;

        g.translate(marginx, 0);

        g.setColor(getParent().getBackground());
        g.fillRect(0, 0, width, height);

        /////// the bar
        RoundRectangle2D.Double barr = new RoundRectangle2D.Double(0, cy - barheight/2.0,
                                                                 width, barheight,
                                                                 barheight, barheight);

        g.setColor(Color.white);
        g.fill(barr);
        g.setColor(Color.black);
        g.draw(barr);

        ////// goal knob
        if (true) {
            double x = width * (goalvalue - minvalue) / (maxvalue - minvalue);
            Ellipse2D.Double goalknob = new Ellipse2D.Double(x - goalknobsize / 2.0,
                                                             cy - goalknobsize / 2.0,
                                                             goalknobsize, goalknobsize);
            g.setColor(Color.green);
            g.fill(goalknob);
            g.setStroke(new BasicStroke(1.0f));
            g.setColor(Color.black);
            g.draw(goalknob);
            g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        }

        if (showactual) {
            /////// actual knob
            double x = width * (actualvalue - minvalue) / (maxvalue - minvalue);
            g.setColor(Color.black);
            g.setStroke(new BasicStroke(1.0f));
            Ellipse2D.Double actualknob = new Ellipse2D.Double(x - actualknobsize / 2.0,
                                                               cy - actualknobsize / 2.0,
                                                               actualknobsize, actualknobsize);

            g.draw(actualknob);
	    }

        if (true) {
            g.setColor(Color.black);
            String s = String.format(goalFormatString, goalvalue);
            g.drawString(s, (int) (width - s.length()*8), (int) (cy + 16));
	    }

        if (true) {
            g.setColor(Color.black);
            String s = String.format(actualFormatString, actualvalue);
            g.drawString(s, (int) 0, (int) (cy + 16));
	    }
    }

    void handleClick(int x)
    {
        goalvalue = minvalue + (maxvalue - minvalue) * (x - marginx) / (getWidth() - 2 * marginx);

        if (goalvalue < minvalue)
            goalvalue = minvalue;
        if (goalvalue > maxvalue)
            goalvalue = maxvalue;

        for (Listener gsl: gsls)
            gsl.goalValueChanged(this, goalvalue);

        repaint();
    }

    class FeedbackSliderMouseMotionListener implements MouseMotionListener, MouseListener
    {
        boolean grabbed = false;

        public void mouseDragged(MouseEvent e)
        {
            if (!grabbed)
                return;

            handleClick(e.getX());
        }

        public void mouseMoved(MouseEvent e)
        {
        }

        public void mouseClicked(MouseEvent e)
        {
            if (e.getButton() != 1)
                return;

            handleClick(e.getX());
        }

        public void mouseEntered(MouseEvent e)
        {
        }

        public void mouseExited(MouseEvent e)
        {
        }

        public void mousePressed(MouseEvent e)
        {
            if (e.getButton() != 1)
                return;

            grabbed = true;

            handleClick(e.getX());
        }

        public void mouseReleased(MouseEvent e)
        {
            grabbed = false;
        }

    }

}
