package april.dynamixel;

import java.io.*;

import april.jmat.MathUtil;

// Some of the methods below (e.g. setBaud) should really be split
// into servo-specific implementations from a "style" perspective, but
// the servos implement compatible commands. We'll split them when we
// encounter a servo that can't be handled generically.

public abstract class AbstractServo
{
    protected AbstractBus      bus;
    protected int              id;
    private Boolean rotationMode = false;

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

        /** Continuous rotation mode (true = wheel; false = joint) **/
        public boolean continuous;

        /** Error flags--- see ERROR constants. **/
        public int    errorFlags;

        public String toString()
        {
            return String.format("pos=%6.3f, speed=%6.3f, load=%6.3f, volts=%4.1f, "+
                                 "temp=%4.1f, mode=%s, err=%s",
                                 positionRadians, speed, load, voltage, temperature,
                                 continuous ? "wheel" : "joint",
                                 getErrorString(errorFlags, "OK"));
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

        rotationMode = readRotationMode();
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

        byte resp[] = ensureEEPROM(new byte[] { 0x03, (byte) newid });
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

        byte resp[] = ensureEEPROM(new byte[] { 0x04, (byte) code });
        if (resp == null || resp.length < 1 || resp[0] != 0) {
            System.out.printf("setBaud failed for %d. Aborting in order to avoid EEPROM wear-out.", id);
            System.exit(-1);
        }

    }

    public int getFirmwareVersion()
    {
        byte resp[] = read(new byte[] { 0x2, 8 }, true);

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
     *            [0, 1]  in joint mode
     *            [-1, 1] in wheel mode (must call setContinuousMode(true) first)
     * @param torquefrac
     *            [0, 1]
     **/
    public void setGoal(double radians, double speedfrac, double torquefrac)
    {
        if (!rotationMode && speedfrac < 0) {
            System.out.println("WRN: Ignoring speed direction for non-continuous servo "+id);
            speedfrac *= -1;
        }
        // ensure proper ranges
        radians = MathUtil.mod2pi(radians);
        speedfrac = Math.max(-1, Math.min(1, speedfrac));
        torquefrac = Math.max(0, Math.min(1, torquefrac));

        setGoal(radians, speedfrac, torquefrac, rotationMode);
    }

    /**
     * @param radians
     *            [-pi, pi]
     * @param speedfrac
     *            [0, 1]
     * @param torquefrac
     *            [0, 1]
     * @param continuous
     *            true only in continuous mode
     **/
    protected abstract void setGoal(double radians, double speedfrac,
                                    double torquefrac, boolean continuous);


    public void setContinuousGoal(double speedfrac, double torquefrac)
    {
        int speedv = (int)(Math.abs(speedfrac * 0x3ff)) | 0x400;
        int torquev = (int) (0x3ff * torquefrac);

        writeToRAM(new byte[] { 0x20,
                                (byte) (speedv & 0xff), (byte) (speedv >> 8),
                                (byte) (torquev & 0xff), (byte) (torquev >> 8) }, true);
    }

    public void idle()
    {
        setGoal(0, 0, 0);
    }

    /** Get servo status **/
    public abstract Status getStatus();

    /* Wrapper functions so users do not need to worry about bus-level
     * sendCommand calls for the most used operations.**/

    /**
     * read data from specified address
     * @param parameters of read command.  First byte is address in
     * servo control table, followed by number of bytes to read,
     * beginning at that address.  parameters.length should be 2
     * @param retry  if true, retry on non-fatal errors
     *
     * @return servo response from bus
     **/
    public byte[] read(byte parameters[], boolean retry)
    {
        if (parameters.length != 2)
            System.out.println("WRN: Invalid read command length");
        return bus.sendCommand(id, AbstractBus.INST_READ_DATA, parameters, retry);
    }
    public byte[] read(byte parameters[], byte numBytes) { return read(parameters, false); }

    /**
     * write data to specified RAM address
     * @param parameters of write command.  First byte is address in
     * servo control table, followed by data to write, beginning at
     * that address.
     * @param retry  if true, retry on non-fatal errors
     *
     * @return servo response from bus
     **/
    public byte[] writeToRAM(byte parameters[], boolean retry)
    {
        if (isAddressEEPROM(0xFF & parameters[0])) {
            System.out.println("WRN: Write failed because RAM address given is in EEPROM area");
            return null;
        }
        return bus.sendCommand(id, AbstractBus.INST_WRITE_DATA, parameters, retry);
    }
    public byte[] writeToRAM(byte parameters[]) { return writeToRAM(parameters, false); }

    /**
     * ensure data at specified EEPROM address
     *
     * First read EEPROM bytes and write if different from desired
     * @param parameters of write command.  First byte is address in
     * servo control table, followed by data to write, beginning at
     * that address.  No retry allowed.
     *
     * @return servo response from bus
     **/
    public byte[] ensureEEPROM(byte parameters[])
    {
        if (!isAddressEEPROM(0xFF & parameters[0])) {
            System.out.println("WRN: Write failed because EEPROM address given is in RAM area");
            return null;
        }

        int numBytes = parameters.length - 1;
        byte resp[] = read(new byte[]{ parameters[0], (byte)numBytes }, false);

        if (resp == null || resp.length != (numBytes + 2) || resp[0] != 0) {
            System.out.println("WRN: Invalid EEPROM read: ");
            dump(resp);
            return resp;
        } else {
            boolean differ = false;
            for (int i = 1; i <= numBytes && !differ; i++)
                differ |= (parameters[i] != resp[i]);
            if (!differ)
                return new byte[]{0};  // as if no error write occurred (w/o checksum)
            System.out.printf("WRN: Writing to EEPROM (address %d)\n", (0xFF & parameters[0]));
        }

        resp = bus.sendCommand(id, AbstractBus.INST_WRITE_DATA, parameters, false);
        if (resp == null || resp.length != 2 || resp[0] != 0) {
            System.out.println("WRN: Error occured while writing to EEPROM");
            dump(resp);
        }
        return resp;
    }

    /**
     * returns true if address is EEPROM address
     **/
    protected abstract boolean isAddressEEPROM(int address);

    /** returns true if servo in continuous mode (no angle limits) **/
    public boolean getRotationMode()
    {
        return rotationMode;
    }

    /** read rotation mode from servo **/
    public boolean readRotationMode()
    {
        boolean mode = true;
        synchronized(rotationMode) {
            byte limits[] = read(new byte[]{0x06, 4}, true);

            if (limits == null || limits.length != 6) {
                System.out.println("WRN: Invalid read of continous state: " +
                                   ((limits == null) ? "null" : "len=" + limits.length));
                return rotationMode;   // best guess
            }
            for (int i = 1; i < 5; i++)
                if (limits[i] != 0)
                    mode = false;
            rotationMode = mode;
        }
        return mode;
    }

    /** Set Rotation Mode
     * true = wheel (continuous)
     * false = joint
     **/
    protected abstract void setRotationMode(boolean mode);

    public void setContinuousMode(boolean mode)
    {
        System.out.printf("NFO: Setting rotation mode for servo %d to %b (%s)\n", id, mode,
                          (mode ? "wheel" : "joint"));

        synchronized(rotationMode) {
            setRotationMode(mode);
            rotationMode = mode;
        }
    }

}
