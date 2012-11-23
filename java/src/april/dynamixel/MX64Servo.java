package april.dynamixel;

public class MX64Servo extends MXSeriesServo
{
    public MX64Servo(AbstractBus bus, int id)
    {
        super(bus, id);
    }


    public void setSpeed(double speedfrac)
    {
        int speedv = (int) (0x3ff * speedfrac);

        writeToRAM(new byte[] { 0x20, (byte) (speedv & 0xff), (byte) (speedv >> 8)}, true);
    }
}

