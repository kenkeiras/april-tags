package april.camera;

import java.util.*;

import april.config.*;
import april.jmat.*;
import april.util.*;

/** Container class for camera calibration results. Includes both the
  * intrinsics and extrinsics of the calibrated cameras.
  */
public class CameraSet
{
    HashMap<String,Integer> nameMap = new HashMap<String,Integer>();
    ArrayList<Camera>   cameras = new ArrayList<Camera>();

    private static class Camera
    {
        public String       name;
        public Calibration  cal;
        public double[][]   extrinsics;
    }

    public CameraSet()
    {
    }

    /** Create a CameraSet from a config file block.
      */
    public CameraSet(Config config)
    {
        String names[] = config.requireStrings("names");

        for (String name : names) {
            Camera cam = new Camera();
            cam.name = name;

            Config child = config.getChild(name);

            // Calibration object
            String classname = child.requireString("class");

            Object obj = ReflectUtil.createObject(classname, child);
            assert(obj != null);
            assert(obj instanceof Calibration);

            Calibration cal = (Calibration) obj;
            cam.cal = cal;

            // Extrinsics
            //double ext[][] = ConfigUtil.getRigidBodyTransform(child, "extrinsics");
            double xyz[] = child.requireDoubles("extrinsics.position");
            double rpy[] = child.requireDoubles("extrinsics.rollpitchyaw_degrees");
            double ext[][] = LinAlg.xyzrpyToMatrix(new double[] { xyz[0]             ,
                                                                  xyz[1]             ,
                                                                  xyz[2]             ,
                                                                  rpy[0]*Math.PI/180 ,
                                                                  rpy[1]*Math.PI/180 ,
                                                                  rpy[2]*Math.PI/180 });
            cam.extrinsics = ext;

            // Add to list
            cameras.add(cam);

            // Hashmap
            int idx = cameras.size() - 1;
            nameMap.put(name, idx);
        }
    }

    public void addCamera(Calibration cal, double ext[][])
    {
        addCamera(cal, ext, null);
    }

    public void addCamera(Calibration cal, double ext[][], String name)
    {
        Camera cam = new Camera();

        cam.name = name;
        cam.cal = cal;
        cam.extrinsics = ext;

        cameras.add(cam);

        int idx = cameras.size() - 1;

        if (name != null)
            nameMap.put(name, idx);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Set internals
    public void setName(int idx, String name)
    {
        cameras.get(idx).name = name;
    }

    public void setCalibration(int idx, Calibration cal)
    {
        cameras.get(idx).cal = cal;
    }

    public void setExtrinsicsMatrix(int idx, double ext[][])
    {
        cameras.get(idx).extrinsics = ext;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Get data

    public int getSize()
    {
        return cameras.size();
    }

    public int getIndex(String name)
    {
        Integer idx = nameMap.get(name);
        if (idx == null)
            return -1;

        return idx;
    }

    public String getName(int idx)
    {
        return cameras.get(idx).name;
    }

    public Calibration getCalibration(String name)
    {
        Integer idx = nameMap.get(name);
        if (idx == null)
            return null;

        return getCalibration(idx);
    }

    public Calibration getCalibration(int idx)
    {
        return cameras.get(idx).cal;
    }

    public double[][] getExtrinsicsMatrix(String name)
    {
        Integer idx = nameMap.get(name);
        if (idx == null)
            return null;

        return getExtrinsicsMatrix(idx);
    }

    public double[][] getExtrinsicsMatrix(int idx)
    {
        return cameras.get(idx).extrinsics;
    }
}
