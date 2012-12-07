package april.camera.calibrator;

import java.awt.image.*;
import java.awt.Color;
import java.util.*;

import april.camera.*;
import april.graph.*;
import april.jmat.*;
import april.jmat.ordering.*;
import april.tag.*;
import april.vis.*;

public class RobustCameraCalibrator
{
    public static boolean verbose = true;

    CameraCalibrationSystem cal;
    CalibrationRenderer renderer;

    TagFamily tf;
    TagMosaic tm;
    double metersPerTag;

    public RobustCameraCalibrator(List<CalibrationInitializer> initializers,
                                  TagFamily tf, double metersPerTag, boolean gui)
    {
        this.tf = tf;
        this.tm = new TagMosaic(tf, metersPerTag);
        this.metersPerTag = metersPerTag;

        cal = new CameraCalibrationSystem(initializers, tf, metersPerTag);

        if (gui)
            renderer = new CalibrationRenderer(cal, this.tf, this.metersPerTag);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // add imagery

    public void addOneImageSet(List<BufferedImage> newImages,
                               List<List<TagDetection>> newDetections)
    {
        cal.addSingleImageSet(newImages, newDetections);

        if (renderer != null)
            renderer.updateMosaicDimensions(newDetections);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // graph optimization

    public class GraphStats
    {
        public int numObs;
        public double MRE; // mean reprojection error
        public double MSE; // mean-squared reprojection error
        public boolean SPDError;
    }

    public class GraphWrapper
    {
        public Graph g;

        public int rootNumber;

        public HashMap<CameraCalibrationSystem.CameraWrapper,Integer> cameraToIntrinsicsNodeIndex;
        public HashMap<CameraCalibrationSystem.CameraWrapper,Integer> cameraToExtrinsicsNodeIndex;
        public HashMap<CameraCalibrationSystem.MosaicWrapper,Integer> mosaicToExtrinsicsNodeIndex;
    }

    public GraphStats getGraphStats(Graph g)
    {
        GraphStats stats = new GraphStats();
        stats.numObs = 0;
        stats.MRE = 0;
        stats.MSE = 0;
        stats.SPDError = false;

        for (GEdge e : g.edges) {
            assert(e instanceof GTagEdge);
            GTagEdge edge = (GTagEdge) e;

            double res[] = edge.getResidualExternal(g);
            assert((res.length & 0x1) == 0);

            for (int i=0; i < res.length; i+=2) {
                double sqerr = res[i]*res[i] + res[i+1]*res[i+1];
                stats.MSE += sqerr;
                stats.MRE += Math.sqrt(sqerr);
            }

            stats.numObs += res.length / 2;
        }

        stats.MRE /= stats.numObs;
        stats.MSE /= stats.numObs;

        return stats;
    }

    public List<GraphStats> iterateUntilConvergence(List<GraphWrapper> graphs, double improvementThreshold,
                                                    int minConvergedIterations, int maxIterations)
    {
        List<GraphStats> stats = new ArrayList<GraphStats>();
        for (GraphWrapper gw : graphs) {
            GraphStats s = iterateUntilConvergence(gw, improvementThreshold,
                                                   minConvergedIterations, maxIterations);
            stats.add(s);
        }

        return stats;
    }

    public GraphStats iterateUntilConvergence(GraphWrapper gw, double improvementThreshold,
                                              int minConvergedIterations, int maxIterations)
    {
        if (gw == null)
            return null;

        GraphSolver solver = new CholeskySolver(gw.g, new MinimumDegreeOrdering());

        GraphStats lastStats = getGraphStats(gw.g);
        int convergedCount = 0;
        int iterationCount = 0;

        // back up intrinsic parameters, since these objects are changed by graph operations
        Map<CameraCalibrationSystem.CameraWrapper,double[]> intrinsicsBackups = new HashMap();
        Set<CameraCalibrationSystem.CameraWrapper> camera_intrinsics =
            gw.cameraToIntrinsicsNodeIndex.keySet();
        for (CameraCalibrationSystem.CameraWrapper cam : camera_intrinsics)
            intrinsicsBackups.put(cam, cam.cal.getParameterization());

        try {
            while (iterationCount < maxIterations && convergedCount < minConvergedIterations) {

                solver.iterate();
                GraphStats stats = getGraphStats(gw.g);

                double percentImprovement = (lastStats.MRE - stats.MRE) / lastStats.MRE;

                if (percentImprovement < improvementThreshold)
                    convergedCount++;
                else
                    convergedCount = 0;

                lastStats = stats;

                iterationCount++;
            }
        } catch (RuntimeException ex) {
            lastStats.SPDError = true;

            // reset ParameterizableCalibration objects (which are affected by graph optimization)
            for (CameraCalibrationSystem.CameraWrapper cam : camera_intrinsics) {
                double state[] = intrinsicsBackups.get(cam);
                assert(state != null);
                cam.cal.resetParameterization(state);
            }

            if (verbose)
                System.out.println("RobustCameraCalibrator: Caught SPD error during optimization");
        }

        return lastStats;
    }

    public List<GraphStats> iterateUntilConvergenceWithReinitalization(double improvementThreshold,
                                                                       int minConvergedIterations,
                                                                       int maxIterations)
    {
        return null;
    }

    public List<GraphWrapper> buildCalibrationGraphs()
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Integer> uniqueRoots = new TreeSet<Integer>();
        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
            uniqueRoots.add(cam.rootNumber);

        List<GraphWrapper> graphWrappers = new ArrayList<GraphWrapper>();
        for (int root : uniqueRoots)
            graphWrappers.add(buildCalibrationGraph(root));

        return graphWrappers;
    }

    /** Build a graph for the specified root camera. Returns null if the
      * graph cannot be built yet (e.g. intrinsics not initialized).
      */
    public GraphWrapper buildCalibrationGraph(int rootNumber)
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        GraphWrapper gw = new GraphWrapper();
        gw.g = new Graph();
        gw.rootNumber = rootNumber;
        gw.cameraToIntrinsicsNodeIndex = new HashMap();
        gw.cameraToExtrinsicsNodeIndex = new HashMap();
        gw.mosaicToExtrinsicsNodeIndex = new HashMap();

        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
        {
            // skip cameras with different roots
            if (cam.rootNumber != rootNumber)
                continue;

            // if a camera in this subsystem doesn't have intrinsics, this
            // graph cannot be constructed
            if (cam.cal == null)
                return null;

            GIntrinsicsNode intrinsics = new GIntrinsicsNode(cam.cal);

            gw.g.nodes.add(intrinsics);
            int intrinsicsNodeIndex = gw.g.nodes.size()-1;
            gw.cameraToIntrinsicsNodeIndex.put(cam, intrinsicsNodeIndex);

            if (verbose)
                System.out.printf("[Root %d] Added intrinsics for camera '%s'\n",
                                  rootNumber, cam.name);

            // has extrinsics
            if (cam.cameraNumber != cam.rootNumber) {
                double CameraToRoot[][] = LinAlg.xyzrpyToMatrix(cam.CameraToRootXyzrpy);

                GExtrinsicsNode extrinsics = new GExtrinsicsNode(CameraToRoot);

                gw.g.nodes.add(extrinsics);
                int extrinsicsNodeIndex = gw.g.nodes.size()-1;
                gw.cameraToExtrinsicsNodeIndex.put(cam, extrinsicsNodeIndex);

                if (verbose)
                    System.out.printf("[Root %d] Added extrinsics for camera '%s'\n",
                                      rootNumber, cam.name);
            }
        }

        for (int mosaicIndex = 0; mosaicIndex < mosaics.size(); mosaicIndex++)
        {
            CameraCalibrationSystem.MosaicWrapper mosaic = mosaics.get(mosaicIndex);

            double MosaicToRootXyzrpy[] = mosaic.MosaicToRootXyzrpys.get(rootNumber);

            // skip mosaics not rooted in this subsystem
            if (MosaicToRootXyzrpy == null)
                continue;

            double MosaicToRoot[][] = LinAlg.xyzrpyToMatrix(MosaicToRootXyzrpy);

            GExtrinsicsNode mosaicExtrinsics = new GExtrinsicsNode(MosaicToRoot);

            gw.g.nodes.add(mosaicExtrinsics);
            int mosaicExtrinsicsIndex = gw.g.nodes.size()-1;
            gw.mosaicToExtrinsicsNodeIndex.put(mosaic, mosaicExtrinsicsIndex);

            if (verbose)
                System.out.printf("[Root %d] Added extrinsics for mosaic %d\n",
                                  rootNumber, mosaicIndex);

            for (CameraCalibrationSystem.CameraWrapper cam : cameras) {
                // skip cameras with different roots
                if (cam.rootNumber != rootNumber)
                    continue;

                // get the detections for this camera
                List<TagDetection> detections = mosaic.detectionSet.get(cam.cameraNumber);

                // only add edges if there are enough constraints
                if (cal.detectionsUsable(detections) == false)
                    continue;

                ArrayList<double[]> xys_px = new ArrayList<double[]>();
                ArrayList<double[]> xyzs_m = new ArrayList<double[]>();

                for (TagDetection d : detections) {
                    xys_px.add(LinAlg.copy(d.cxy));
                    xyzs_m.add(LinAlg.copy(tm.getPositionMeters(d.id)));
                }

                // get the intrinsics and extrinsics indices for this camera
                Integer cameraIntrinsicsNodeIndex = gw.cameraToIntrinsicsNodeIndex.get(cam);
                Integer cameraExtrinsicsNodeIndex = gw.cameraToExtrinsicsNodeIndex.get(cam);

                GTagEdge edge;
                if (cameraExtrinsicsNodeIndex == null)
                    edge = new GTagEdge(cameraIntrinsicsNodeIndex,
                                        mosaicExtrinsicsIndex, xys_px, xyzs_m);
                else
                    edge = new GTagEdge(cameraIntrinsicsNodeIndex, cameraExtrinsicsNodeIndex,
                                        mosaicExtrinsicsIndex, xys_px, xyzs_m);

                gw.g.edges.add(edge);

                if (verbose)
                    System.out.printf("[Root %d] Added tag edge between mosaic %d and camera '%s'"+
                                      "(camera %s extrinsics)\n",
                                      rootNumber, mosaicIndex, cam.name,
                                      (cameraExtrinsicsNodeIndex == null) ? "doesn't have" : "has");
            }
        }

        return gw;
    }

    public void updateFromGraphs(List<GraphWrapper> graphWrappers,
                                 List<GraphStats> stats)
    {
        assert(graphWrappers.size() == stats.size());

        for (int i = 0; i < graphWrappers.size(); i++)
        {
            GraphWrapper gw = graphWrappers.get(i);
            GraphStats s = stats.get(i);

            updateFromGraph(gw, s);
        }
    }

    public void updateFromGraph(GraphWrapper gw, GraphStats s)
    {
        updateCameraIntrinsicsFromGraph(gw, s);
        updateCameraExtrinsicsFromGraph(gw, s);
        updateMosaicExtrinsicsFromGraph(gw, s);
    }

    public void updateCameraIntrinsicsFromGraph(GraphWrapper gw, GraphStats s)
    {
        if (gw == null || s == null || s.SPDError == true)
            return;

        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Map.Entry<CameraCalibrationSystem.CameraWrapper,Integer>> camera_intrinsics =
            gw.cameraToIntrinsicsNodeIndex.entrySet();

        for (Map.Entry<CameraCalibrationSystem.CameraWrapper,Integer> entry : camera_intrinsics)
        {
            CameraCalibrationSystem.CameraWrapper cam = entry.getKey();
            Integer cameraIntrinsicsNodeIndex = entry.getValue();
            assert(cam != null);
            assert(cameraIntrinsicsNodeIndex != null);

            GNode node = gw.g.nodes.get(cameraIntrinsicsNodeIndex);
            assert(node != null);
            assert(node instanceof GIntrinsicsNode);
            GIntrinsicsNode intrinsics = (GIntrinsicsNode) node;

            assert(cam.cal != null);
            cam.cal.resetParameterization(intrinsics.state);
        }
    }

    public void updateCameraExtrinsicsFromGraph(GraphWrapper gw, GraphStats s)
    {
        if (gw == null || s == null || s.SPDError == true)
            return;

        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Map.Entry<CameraCalibrationSystem.CameraWrapper,Integer>> camera_extrinsics =
            gw.cameraToExtrinsicsNodeIndex.entrySet();

        for (Map.Entry<CameraCalibrationSystem.CameraWrapper,Integer> entry : camera_extrinsics)
        {
            CameraCalibrationSystem.CameraWrapper cam = entry.getKey();
            Integer cameraExtrinsicsNodeIndex = entry.getValue();
            assert(cam != null);
            assert(cameraExtrinsicsNodeIndex != null);

            GNode node = gw.g.nodes.get(cameraExtrinsicsNodeIndex);
            assert(node != null);
            assert(node instanceof GExtrinsicsNode);
            GExtrinsicsNode extrinsics = (GExtrinsicsNode) node;

            cam.CameraToRootXyzrpy = LinAlg.copy(extrinsics.state);
        }
    }

    public void updateMosaicExtrinsicsFromGraph(GraphWrapper gw, GraphStats s)
    {
        if (gw == null || s == null || s.SPDError == true)
            return;

        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Map.Entry<CameraCalibrationSystem.MosaicWrapper,Integer>> mosaic_extrinsics =
            gw.mosaicToExtrinsicsNodeIndex.entrySet();

        for (Map.Entry<CameraCalibrationSystem.MosaicWrapper,Integer> entry : mosaic_extrinsics)
        {
            CameraCalibrationSystem.MosaicWrapper mosaic = entry.getKey();
            Integer mosaicExtrinsicsNodeIndex = entry.getValue();
            assert(mosaic != null);
            assert(mosaicExtrinsicsNodeIndex != null);

            GNode node = gw.g.nodes.get(mosaicExtrinsicsNodeIndex);
            assert(node != null);
            assert(node instanceof GExtrinsicsNode);
            GExtrinsicsNode extrinsics = (GExtrinsicsNode) node;

            double MosaicToRootXyzrpy[] = mosaic.MosaicToRootXyzrpys.get(gw.rootNumber);
            assert(MosaicToRootXyzrpy != null);

            mosaic.MosaicToRootXyzrpys.put(gw.rootNumber, LinAlg.copy(extrinsics.state));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // rendering code

    public VisCanvas getVisCanvas()
    {
        if (renderer == null)
            return null;

        return renderer.vc;
    }

    public void draw()
    {
        if (renderer == null)
            return;

        renderer.draw();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // file io code

    public void printCalibrationBlock()
    {
        System.out.printf(getCalibrationBlockString());
    }

    public String getCalibrationBlockString()
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();

        String str = "";

        // start block
        str += "aprilCameraCalibration {\n";
        str += "\n";

        // Comment about MRE, MSE for this calibration
        /*
        if (true) {
            str += String.format("    // MRE: %.5f\n",getMRE());
            str += String.format("    // MSE: %.5f\n",getMSE());
            str += "\n";
        }
        */

        // print name list
        String names = "    names = [";
        for (int i=0; i+1 < cameras.size(); i++) {
            CameraCalibrationSystem.CameraWrapper cam = cameras.get(i);
            names = String.format("%s %s,", names, cam.name);
        }
        names = String.format("%s %s ];\n", names, cameras.get(cameras.size()-1).name);
        str += names;

        // print cameras
        for (int i=0; i < cameras.size(); i++) {

            CameraCalibrationSystem.CameraWrapper cam = cameras.get(i);

            str += "\n";
            str += String.format("    %s {\n", cam.name);

            // make sure ParameterizableCalibration is up to date and print it
            if (cam.cal != null)
                str += cam.cal.getCalibrationString();
            else
                str += "        // intrinsics not initialized\n";

            // RootToCamera
            double state[] = LinAlg.xyzrpyInverse(cam.CameraToRootXyzrpy);

            String s;
            s = String.format(  "        extrinsics {\n");
            s = String.format("%s            // Global-To-Camera coordinate transformation\n", s);
            s = String.format("%s            position = [%11.6f,%11.6f,%11.6f ];\n", s, state[0], state[1], state[2]);
            s = String.format("%s            rollpitchyaw_degrees = [%11.6f,%11.6f,%11.6f ];\n",
                              s, state[3]*180/Math.PI, state[4]*180/Math.PI, state[5]*180/Math.PI);
            s = String.format("%s        }\n", s);

            str += s;
            str += "    }\n";
        }

        // end block
        str += "}\n";

        return str;
    }
    public void saveCalibrationAndImages(String basepath)
    {
    }
}
