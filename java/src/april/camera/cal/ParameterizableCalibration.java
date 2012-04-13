package april.camera.cal;

public interface ParameterizableCalibration extends Calibration
{
    public double[] getParameterization();
    public void     resetParameterization(double params[]);
}
