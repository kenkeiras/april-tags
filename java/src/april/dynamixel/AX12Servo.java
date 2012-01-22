package april.dynamixel;

import april.jmat.*;

public class AX12Servo extends AbstractServo
{
    public AX12Servo(AbstractBus bus, int id)
    {
        super(bus, id);

        // Anlge limits
        // byte resp[] = bus.sendCommand(id,
        //                               AbstractBus.INST_READ_DATA,
        //                               new byte[] { 6, 4 },
        //                               true);
        // System.out.println("Angle limits: " + id);
        // dump(resp);
    }

    public double getMinimumPositionRadians()
    {
        return Math.toRadians(-150);
    }

    public double getMaximumPositionRadians()
    {
        return Math.toRadians(150);
    }

    public void setGoal(double radians, double speedfrac, double torquefrac)
    {
        int posv = ((int) ((radians+Math.toRadians(150))/Math.toRadians(300)*1024)) & 0x3ff;
        int speedv = (int) (0x3ff * speedfrac);
        int torquev = (int) (0x3ff * torquefrac);

        bus.sendCommand(id,
                        AbstractBus.INST_WRITE_DATA,
                        new byte[] { 0x1e,
                                     (byte) (posv & 0xff), (byte) (posv >> 8),
                                     (byte) (speedv & 0xff), (byte) (speedv >> 8),
                                     (byte) (torquev & 0xff), (byte) (torquev >> 8) },
                        true);
    }

    /** Get servo status **/
    public Status getStatus()
    {
        // read 8 bytes beginning with register 0x24
        byte resp[] = bus.sendCommand(id,
                                      AbstractBus.INST_READ_DATA,
                                      new byte[] { 0x24, 8 },
                                      true);
        if (resp == null)
            return null;

        Status st = new Status();
        st.positionRadians = ((resp[1] & 0xff) + ((resp[2] & 0x3f) << 8)) * Math.toRadians(300) / 1024.0 - Math.toRadians(150);

        int tmp = ((resp[3] & 0xff) + ((resp[4] & 0x3f) << 8));
        if (tmp < 1024)
            st.speed = tmp / 1023.0;
        else
            st.speed = -(tmp - 1024) / 1023.0;

        // load is signed, we scale to [-1, 1]
        tmp = (resp[5] & 0xff) + ((resp[6] & 0xff) << 8);
        if (tmp < 1024)
            st.load = tmp / 1023.0;
        else
            st.load = -(tmp - 1024) / 1023.0;

        st.voltage = (resp[7] & 0xff) / 10.0; // scale to voltage
        st.temperature = (resp[8] & 0xff); // deg celsius
        st.errorFlags = resp[0];

        return st;
    }
}