package april.dynamixel;

import java.io.*;

// Some of the methods below (e.g. setBaud) should really be split
// into servo-specific implementations from a "style" perspective, but
// the servos implement compatible commands. We'll split them when we
// encounter a servo that can't be handled generically.

public abstract class AbstractServo
{
    protected AbstractBus      bus;
    protected int              id;

    public static final int ERROR_INSTRUCTION = (1 << 6);
    public static final int ERROR_OVERLOAD    = (1 << 5);
    public static final int ERROR_CHECKSUM    = (1 << 4);
    public static final int ERROR_RANGE       = (1 << 3);
    public static final int ERROR_OVERHEAT    = (1 << 2);
    public static final int ERROR_ANGLE_LIMIT = (1 << 1);
    public static final int ERROR_VOLTAGE     = (1 << 0);

    public static class Status
    {
        static final String errors[] = { "Input Voltage", "Angle Limit", "Overheating",
                                         "Range", "Checksum", "Overload", "Instruction", "" };


        /** Radians **/
        public double positionRadians;

        /** Speed [0,1] **/
        public double speed;

        /** Current load [-1, 1] **/
        public double load;

        /** Voltage (volts) **/
        public double voltage;

        /** Temperature (Deg celsius) **/
        public double temperature;

        /** Error flags--- see ERROR constants. **/
        public int    errorFlags;

        public String toString()
        {
            return String.format("pos=%6.3f, speed=%6.3f, load=%6.3f, voltage=%4.1f, temp=%4.1f, err=%s",
                                 positionRadians, speed, load, voltage, temperature, getErrorString(errorFlags, "OK"));
        }

        public static String getErrorString(int error, String defaultString)
        {
            if (error == 0)
                return defaultString;

            StringBuffer sb = new StringBuffer();

            for (int e = 0; e <= 8; e++) {
                if ((error & (1 << e)) != 0) {
                    sb.append(String.format("%s", errors[e]));
                    e = e & (~(1<<e));
                    if (e != 0)
                        sb.append(", ");
                }
            }
            return sb.toString();
        }
    }

    protected AbstractServo(AbstractBus bus, int id)
    {
        this.bus = bus;
        this.id = id;

        assert(id >= 0 && id < 254); // note 254 = broadcast address.
    }


    static void dump(byte buf[])
    {
        if (buf == null) {
            System.out.println("WRN: Null Buffer");
            return;
        }
        for (int i = 0; i < buf.length; i++)
            System.out.printf("%02x ", buf[i] & 0xff);

        System.out.printf("\n");
    }

    public abstract double getMinimumPositionRadians();
    public abstract double getMaximumPositionRadians();

    public void setID(int newid)
    {
        assert(newid >= 0 && newid < 254);

        byte resp[] = bus.sendCommand(id, AbstractBus.INST_WRITE_DATA, new byte[] { 0x03, (byte) newid }, false);
        if (resp == null || resp.length < 1 || resp[0] != 0) {
            System.out.printf("setID failed for %d. Aborting in order to avoid EEPROM wear-out.", id);
            System.exit(-1);
        }
    }

    public void setBaud(int baud)
    {
        int code = 0;

        switch(baud) {
            case 1000000:
                code = 1;
                break;
            case 500000:
                code = 3;
                break;
            case 115200:
                code = 16;
                break;
            case 57600:
                code = 34;
                break;
            default:
                // unknown baud rate
                assert(false);
        }

        byte resp[] = bus.sendCommand(id, AbstractBus.INST_WRITE_DATA, new byte[] { 0x04, (byte) code }, false);
        if (resp == null || resp.length < 1 || resp[0] != 0) {
            System.out.printf("setBaud failed for %d. Aborting in order to avoid EEPROM wear-out.", id);
            System.exit(-1);
        }

    }

    public int getFirmwareVersion()
    {
        byte resp[] = bus.sendCommand(id,
                                      AbstractBus.INST_READ_DATA,
                                      new byte[] { 0x2, 8 },
                                      true);

        return resp[1]&0xff;
    }

    /** Returns true if the servo seems to be on the bus. **/
    public boolean ping()
    {
        byte resp[] = bus.sendCommand(id, AbstractBus.INST_PING, null, false);
        if (resp == null || resp.length != 2)
            return false;
        return true;
    }

    /**
     * @param radians
     *            [-pi, pi]
     * @param speedfrac
     *            [0, 1]
     * @param torquefrac
     *            [0, 1]
     **/
    public abstract void setGoal(double radians, double speedfrac, double torquefrac);

    public void idle()
    {
        setGoal(0, 0, 0);
    }

    /** Get servo status **/
    public abstract Status getStatus();
}
