package april.dynamixel;

public class MX28Servo extends AbstractServo
{
    public MX28Servo(AbstractBus bus, int id)
    {
        super(bus, id);

        bus.sendCommand(id,
                        AbstractBus.INST_WRITE_DATA,
                        new byte[] { 18, 4 },
                        true );

/*
  // PID
  bus.sendCommand(id,
                        AbstractBus.INST_WRITE_DATA,
                        new byte[] { 26, 16, 10 },
                        true );

                        // punch
        bus.sendCommand(id,
                        AbstractBus.INST_WRITE_DATA,
                        new byte[] { 48, 64, 0 },
                        true );
*/
    }


    public double getMinimumPositionRadians()
    {
        return -Math.PI;
    }

    public double getMaximumPositionRadians()
    {
        return Math.PI;
    }

    public void setGoal(double radians, double speedfrac, double torquefrac)
    {
        int posv = ((int) ((radians+Math.PI)/(2*Math.PI)*4096)) & 0xfff;
        int speedv = (int) (0x3ff * speedfrac);
        int torquev = (int) (0x3ff * torquefrac);

        bus.sendCommand(id,
                        AbstractBus.INST_WRITE_DATA,
                        new byte[] { 0x1e,
                                     (byte) (posv & 0xff), (byte) (posv >> 8),
                                     (byte) (speedv & 0xff), (byte) (speedv >> 8),
                                     (byte) (torquev & 0xff), (byte) (torquev >> 8) },
                        true);

/*
        byte resp[] = bus.sendCommand(id,
                                      AbstractBus.INST_READ_DATA,
                                      new byte[] { 0x1e, 6 },
                                      true);

        resp = bus.sendCommand(id,
                                      AbstractBus.INST_READ_DATA,
                                      new byte[] { 14, 6 },
                                      true);

        dump(resp);
        assert(false);

*/
    }
    static void dump(byte buf[])
    {
        for (int i = 0; i < buf.length; i++)
            System.out.printf("%02x ", buf[i] & 0xff);

        System.out.printf("\n");
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
        st.positionRadians = ((resp[1] & 0xff) + ((resp[2] & 0x3f) << 8)) * 2 * Math.PI / 0xfff - Math.PI;

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
