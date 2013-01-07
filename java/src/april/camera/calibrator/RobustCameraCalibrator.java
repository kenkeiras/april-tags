package april.camera.calibrator;

import java.awt.image.*;
import java.awt.Color;
import java.io.*;
import java.util.*;
import javax.imageio.*;

import april.camera.*;
import april.graph.*;
import april.jmat.*;
import april.jmat.ordering.*;
import april.tag.*;
import april.vis.*;

public class RobustCameraCalibrator
{
    public static boolean verbose = true;

    List<CalibrationInitializer> initializers;
    CameraCalibrationSystem cal;
    CalibrationRenderer renderer;

    TagFamily tf;
    TagMosaic tm;
    double metersPerTag;

    public RobustCameraCalibrator(List<CalibrationInitializer> initializers,
                                  TagFamily tf, double metersPerTag, boolean gui)
    {
        this.initializers = initializers;
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

    public static class GraphStats
    {
        public int numObs;       // number of tags used
        public double MRE;       // mean reprojection error
        public double MSE;       // mean-squared reprojection error
        public boolean SPDError; // did we catch an SPD error from the graph solver?
    }

    public static class GraphWrapper
    {
        // The graph that was created (all graph state can be changed without affecting the camera system)
        public Graph g;

        // The root camera number for this graph
        public int rootNumber;

        // Maps that connect a camera or mosaic wrapper to the GNode index in the graph
        // Note: The keys in these maps are from the original CameraCalibrationSystem
        //  -- changing their values will modify the original system
        public HashMap<CameraCalibrationSystem.CameraWrapper,Integer> cameraToIntrinsicsNodeIndex;
        public HashMap<CameraCalibrationSystem.CameraWrapper,Integer> cameraToExtrinsicsNodeIndex;
        public HashMap<CameraCalibrationSystem.MosaicWrapper,Integer> mosaicToExtrinsicsNodeIndex;
    }

    /** Compute MRE and MSE for a graph.
      */
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

    /** Iterate each graph until convergence. See the single-graph method for details.
      */
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

    /** Iterate graph until it converges. Catch SPD errors and set the field in the
      * GraphStats object that is returned.
      */
    public GraphStats iterateUntilConvergence(GraphWrapper gw, double improvementThreshold,
                                              int minConvergedIterations, int maxIterations)
    {
        if (gw == null)
            return null;

        GraphSolver solver = new CholeskySolver(gw.g, new MinimumDegreeOrdering());
        CholeskySolver.verbose = false;

        GraphStats lastStats = getGraphStats(gw.g);
        int convergedCount = 0;
        int iterationCount = 0;

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

            if (verbose)
                System.out.println("RobustCameraCalibrator: Caught SPD error during optimization");
        }

        return lastStats;
    }

    public List<GraphStats> iterateUntilConvergenceWithReinitalization(double reinitMREThreshold, double improvementThreshold,
                                                                       int minConvergedIterations, int maxIterations)
    {
        // iterate the usual way
        List<GraphWrapper> origGraphWrappers = this.buildCalibrationGraphs();
        List<GraphStats> origStats = this.iterateUntilConvergence(origGraphWrappers, improvementThreshold,
                                                                  minConvergedIterations, maxIterations);

        boolean origError = false;
        double origJointMRE = 0;
        int origJointNumObs = 0;
        for (GraphStats s : origStats) {
            if (s == null || s.SPDError == true) {
                origError = true;
                continue;
            }

            origJointMRE += s.MRE*s.numObs;
            origJointNumObs += s.numObs;
        }
        origJointMRE = origJointMRE / origJointNumObs;

        // is it acceptable?
        if (!origError && origJointMRE < reinitMREThreshold) {
            this.updateFromGraphs(origGraphWrappers, origStats);
            System.out.printf("ITERATE WITH REINIT: Skipped reinitialization, using original (orig %b/%8.3f)\n",
                              origError, origJointMRE);
            return origStats;
        }

        // build and optimize the new system
        CameraCalibrationSystem copy = this.cal.copyWithBatchReinitialization();
        List<GraphWrapper> newGraphWrappers = this.buildCalibrationGraphs(copy);
        List<GraphStats> newStats = this.iterateUntilConvergence(newGraphWrappers, improvementThreshold,
                                                                 minConvergedIterations, maxIterations);

        boolean newError = false;
        double newJointMRE = 0;
        int newJointNumObs = 0;
        for (GraphStats s : newStats) {
            if (s == null || s.SPDError == true) {
                newError = true;
                continue;
            }

            newJointMRE += s.MRE*s.numObs;
            newJointNumObs += s.numObs;
        }
        newJointMRE = newJointMRE / newJointNumObs;

        // decide which system to use
        boolean useNew = false;
        if (!origError && !newError && (newJointMRE + 0.001 < origJointMRE)) // both are good but new has lower error
            useNew = true;
        else if (origError)
            useNew = true;

        if (!useNew) {
            this.updateFromGraphs(origGraphWrappers, origStats);
            System.out.printf("ITERATE WITH REINIT: Attempted reinitialization, using original (orig %b/%8.3f new %b/%8.3f)\n",
                              origError, origJointMRE, newError, newJointMRE);
            return origStats;
        }

        this.updateFromGraphs(newGraphWrappers, newStats);
        this.cal = copy;
        this.renderer.replaceCalibrationSystem(copy);
        System.out.printf("ITERATE WITH REINIT: Attempted reinitialization, using new (orig %b/%8.3f new %b/%8.3f)\n",
                          origError, origJointMRE, newError, newJointMRE);
        return newStats;
    }

    /** Convenience method to build graphs for all connected subsystems. Finds
      * all unique rootNumbers in the camera system and calls buildCalibrationGraph()
      * to build a graph for each subsystem. See buildCalibrationGraph() for details.
      */
    public List<GraphWrapper> buildCalibrationGraphs()
    {
        return buildCalibrationGraphs(this.cal);
    }

    public static List<GraphWrapper> buildCalibrationGraphs(CameraCalibrationSystem cal)
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Integer> uniqueRoots = new TreeSet<Integer>();
        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
            uniqueRoots.add(cam.rootNumber);

        List<GraphWrapper> graphWrappers = new ArrayList<GraphWrapper>();
        for (int root : uniqueRoots)
            graphWrappers.add(buildCalibrationGraph(cal, root));

        return graphWrappers;
    }

    /** Build a graph for the specified root camera. Returns null if the graph
      * cannot be built yet (e.g. intrinsics not initialized). All state in the
      * contained graph is safe to change, as it is copied or regenerated from
      * the underlying CameraCalibrationSystem. This means you can optimize the
      * graph safely and ensure that it has suceeded before updating the
      * underlying CameraCalibrationSystem. See updateFromGraph() and related
      * methods
      */
    public static GraphWrapper buildCalibrationGraph(CameraCalibrationSystem cal, int rootNumber)
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

            // make a new camera model
            double params[] = cam.cal.getParameterization();
            ParameterizableCalibration pcal =
                cam.initializer.initializeWithParameters(cam.width, cam.height, params);

            GIntrinsicsNode intrinsics = new GIntrinsicsNode(pcal);

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
                    xyzs_m.add(LinAlg.copy(cal.tm.getPositionMeters(d.id)));
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

    /** Update the CameraCalibrationSystem from the graphs provided. If an
      * entry in either argument is null or the GraphStats.SPDError field is
      * true, the camera system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
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

        if (verbose)
            cal.printSystem();
    }

    /** Update the CameraCalibrationSystem from the graph provided. If either
      * argument is null or the GraphStats.SPDError field is true, the camera
      * system will <b>not</b> be updated from the corresponding GraphWrapper.
      */
    public void updateFromGraph(GraphWrapper gw, GraphStats s)
    {
        updateCameraIntrinsicsFromGraph(gw, s);
        updateCameraExtrinsicsFromGraph(gw, s);
        updateMosaicExtrinsicsFromGraph(gw, s);
    }

    /** Update the CameraCalibrationSystem's camera intrinsics from the graph
      * provided. If either * argument is null or the GraphStats.SPDError field
      * is true, the camera * system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
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

    /** Update the CameraCalibrationSystem's camera extrinsics from the graph
      * provided. If either * argument is null or the GraphStats.SPDError field
      * is true, the camera * system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
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

    /** Update the CameraCalibrationSystem's mosaic extrinsics from the graph
      * provided. If either * argument is null or the GraphStats.SPDError field
      * is true, the camera * system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
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

    /** Return a reference to the CalibrationRenderer's VisCanvas, if it exists.
      */
    public VisCanvas getVisCanvas()
    {
        if (renderer == null)
            return null;

        return renderer.vc;
    }

    /** Tell the CalibrationRenderer to draw(), if it exists.
      */
    public void draw()
    {
        if (renderer == null)
            return;

        renderer.draw();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // file io code

    /** Print the camera calibration string to the terminal. Uses the
      * getCalibrationBlockString() method.
      */
    public void printCalibrationBlock()
    {
        System.out.printf(getCalibrationBlockString());
    }

    /** Get the camera calibration string. Intrinsics that aren't initialized
      * result in a comment (thus an invalid calibration block).
      */
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
                str += "        // Error: intrinsics not initialized\n";

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

    /** Save the calibration to a file.
      */
    public synchronized void saveCalibration()
    {
        saveCalibration("/tmp/cameraCalibration");
    }

    /** Save the calibration to a file.
      */
    public synchronized void saveCalibration(String basepath)
    {
        // find unused name
        int calNum = -1;
        String calName = null;
        File outputConfigFile = null;
        do {
            calNum++;
            calName = String.format("%s/calibration%04d.config/", basepath, calNum);
            outputConfigFile = new File(calName);
        } while (outputConfigFile.exists());

        try {
            BufferedWriter outs = new BufferedWriter(new FileWriter(outputConfigFile));
            outs.write(getCalibrationBlockString());
            outs.flush();
            outs.close();
        } catch (Exception ex) {
            System.err.printf("RobustCameraCalibrator: Failed to output calibration to '%s'\n", calName);
            return;
        }
    }

    /** Save the calibration to a file and all images.
      */
    public synchronized void saveCalibrationAndImages()
    {
        saveCalibrationAndImages("/tmp/cameraCalibration");
    }

    /** Save the calibration to a file and all images.
      */
    public synchronized void saveCalibrationAndImages(String basepath)
    {
        // create directory for image dump
        int dirNum = -1;
        String dirName = null;
        File dir = null;
        do {
            dirNum++;
            dirName = String.format("%s/imageSet%d/", basepath, dirNum);
            dir = new File(dirName);
        } while (dir.exists());

        if (dir.mkdirs() != true) {
            System.err.printf("RobustCameraCalibrator: Failure to create directory '%s'\n", dirName);
            return;
        }

        String configpath = String.format("%s/calibration.config", dirName);
        try {
            BufferedWriter outs = new BufferedWriter(new FileWriter(new File(configpath)));
            outs.write(getCalibrationBlockString());
            outs.flush();
            outs.close();
        } catch (Exception ex) {
            System.err.printf("RobustCameraCalibrator: Failed to output calibration to '%s'\n", configpath);
            return;
        }

        // save images
        List<List<BufferedImage>> imageSets = this.cal.getAllImageSets();
        for (int cameraIndex = 0; cameraIndex < this.initializers.size(); cameraIndex++) {

            String subDirName = dirName;

            // make a subdirectory if we have multiple cameras
            if (this.initializers.size() > 1) {
                subDirName = String.format("%scamera%d/", dirName, cameraIndex);
                File subDir = new File(subDirName);

                if (subDir.mkdirs() != true) {
                    System.err.printf("RobustCameraCalibrator: Failure to create subdirectory '%s'\n", subDirName);
                    return;
                }
            }

            for (int imageSetIndex = 0; imageSetIndex < imageSets.size(); imageSetIndex++) {

                List<BufferedImage> images = imageSets.get(imageSetIndex);
                BufferedImage im = images.get(cameraIndex);

                String fileName = String.format("%simage%04d.png", subDirName, imageSetIndex);
                File imageFile = new File(fileName);

                System.out.printf("Filename '%s'\n", fileName);

                try {
                    ImageIO.write(im, "png", imageFile);

                } catch (IllegalArgumentException ex) {
                    System.err.printf("RobustCameraCalibrator: Failed to output images to '%s'\n", subDirName);
                    return;
                } catch (IOException ex) {
                    System.err.printf("RobustCameraCalibrator: Failed to output images to '%s'\n", subDirName);
                    return;
                }
            }
        }

        System.out.printf("Successfully saved calibration and images to '%s'\n", dirName);
    }
}
