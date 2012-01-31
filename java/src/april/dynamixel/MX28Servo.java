package april.dynamixel;

import april.jmat.MathUtil;

/////////// MX-28 Servo Control Table ///////////
// Bytes 0x00 - 0x18: EEPROM                   //
// Bytes 0x19 - 0x31: RAM                      //
/////////////////////////////////////////////////
public class MX28Servo extends AbstractServo
{
    public MX28Servo(AbstractBus bus, int id)
    {
        super(bus, id);

        // Return delay time
        byte delay = 0x02;  // each unit = 2 usec
        ensureEEPROM(new byte[] { 0x5, delay });

        // Set Alarm Shutdown (EEPROM)
        ensureEEPROM(new byte[] { 18, 36 } );

        // PID (RAM)
        // byte p = 16;
        // byte i = 10;
        // byte d = 0;
        // writeToRAM(new byte[] { 26, p, i, d }, true );
    }

    public boolean isAddressEEPROM(int address)
    {
        return address < 0x19;
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
        radians = MathUtil.mod2pi(radians);
        int posv = ((int) ((radians + Math.PI) / (2 * Math.PI) * 4096)) & 0xfff;
        int speedv = (int) (0x3ff * speedfrac);
        int torquev = (int) (0x3ff * torquefrac);

        writeToRAM(new byte[] { 0x1e,
                                (byte) (posv & 0xff), (byte) (posv >> 8),
                                (byte) (speedv & 0xff), (byte) (speedv >> 8),
                                (byte) (torquev & 0xff), (byte) (torquev >> 8) }, true);
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

