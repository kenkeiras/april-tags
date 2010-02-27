package april.vis;

/** Interface for listening to changes in the VisView **/
public interface VisViewListener
{
    public void viewBufferEnabledChanged(VisCanvas vc, String bufferName, boolean enabled);

    public void viewCameraChanged(VisCanvas vc);
}
