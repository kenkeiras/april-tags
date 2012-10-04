package april.camera.calibrator;

import java.util.*;

import april.camera.*;
import april.tag.*;
import april.jmat.*;

public class SuggestUtil
{


    public static ArrayList<TagDetection> makeDetectionsFromExt(Calibration cal, DistortionFunctionVerifier verifier,
                                                                double mExtrinsics[],
                                                                int minTagId, int maxTagId,
                                                                TagMosaic tm)
    {
        TagFamily tf = tm.tf;

        int width = cal.getWidth();
        int height = cal.getHeight();

        ArrayList<TagDetection> detections = new ArrayList();

        int minRow = tm.getRow(minTagId);
        int minCol = tm.getColumn(minTagId);

        int maxRow = tm.getRow(maxTagId);
        int maxCol = tm.getColumn(maxTagId);

        for (int col = minCol; col <= maxCol; col++) {
          outer:
            for (int row = minRow; row <= maxRow; row++) {
                TagDetection det = new TagDetection();
                det.id = tm.getID(col, row); // XXX TagMosaic is col, row;
                assert(det.id < tf.codes.length);

                double world[] = tm.getPositionMeters(det.id);
                det.cxy = CameraMath.project(cal, verifier, LinAlg.xyzrpyToMatrix(mExtrinsics), world);

                // CameraMath.project() with a verifier will return null if the
                // point is outside of the valid range
                if (det.cxy == null)
                    continue;

                // XXX This check now redundant? XXX This "contains detection" check is not sufficient, since the distortion function
                // could be malformed
                if (det.cxy[0] < 0 || det.cxy[0] >= width ||
                    det.cxy[1] < 0 || det.cxy[1] >= height)
                    continue;

                // project the boundaries to make det.p???
                double metersPerPixel = tm.tagSpacingMeters / (tf.whiteBorder*2 + tf.blackBorder*2 + tf.d);
                double cOff = metersPerPixel * (tf.blackBorder + tf.d / 2.0);

                // Tag corners are listed bottom-left, bottom-right, top-right, top-left
                // Note: That the +x is to the right, +y is down (img-style coordinates)
                double world_corners[][] = {{world[0] - cOff, world[1] + cOff},
                                            {world[0] + cOff, world[1] + cOff},
                                            {world[0] + cOff, world[1] - cOff},
                                            {world[0] - cOff, world[1] - cOff}};


                det.p = new double[4][];
                for (int i = 0; i < 4; i++) {
                    det.p[i] = CameraMath.project(cal, verifier, LinAlg.xyzrpyToMatrix(mExtrinsics), world_corners[i]);
                    if (det.p[i] == null)
                        continue outer;
                }
                detections.add(det);
            }
        }
        return detections;
    }

}