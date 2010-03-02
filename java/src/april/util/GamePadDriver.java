package april.util;

import april.util.*;
import lcm.lcm.*;
import april.lcmtypes.*;

public class GamePadDriver implements Runnable
{
    GamePad gp;
    LCM lcm;

    public final static int RIGHT_VERT_AXIS = 3;
    public final static int RIGHT_HORZ_AXIS = 2;

    public final static int LEFT_VERT_AXIS = 1;
    public final static int LEFT_HORZ_AXIS = 0;

    public final static int DPAD_VERT_AXIS = 5;
    public final static int DPAD_HORZ_AXIS = 4;

    public static void main(String args[])
    {
        new GamePadDriver().run();
    }

    public GamePadDriver()
    {
        gp = new GamePad(true);
        lcm = LCM.getSingleton();
    }
    public void run(){
        boolean gotPress = false;
        long oldTime = 0;
        while (true) {

            TimeUtil.sleep(25);

            if(gp.reset())
                gotPress = false;

            gamepad_t msg = new gamepad_t();
            msg.utime = TimeUtil.utime();
            if(! gp.isActive()){
                continue;
            }

            msg.naxes = 6;
            msg.axes = new double[msg.naxes];
            for (int i = 0; i < msg.naxes; i++)
                msg.axes[i] = gp.getAxis(i);

            // the immediate for loop is to make output more linear
            for (int i = 0; i < msg.naxes; i++)
                msg.axes[i] *= Math.abs(msg.axes[i]);

            // this next for loops make the robot more controllable (natural)
            for (int i = 0; i < msg.naxes; i++){
                msg.axes[i] += msg.axes[i];
                msg.axes[i] /= 2;
                //System.out.printf(" Axes %d is (%g)\n",i,msg.axes[i]);
            }
            msg.buttons = 0;
            for (int i = 0; i < 32; i++)
                if (gp.getButton(i))
                    msg.buttons |= (1<<i);

            if (msg.buttons > 0)
                gotPress = true;

            if (gotPress)
                lcm.publish("GAMEPAD", msg);
        }
    }
}
