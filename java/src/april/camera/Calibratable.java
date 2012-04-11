package april.camera;

public interface Calibratable extends Calibration
{
    public double[] getCalibratableParameters();
    public void     resetCalibratableParameters(double params[]);
}
