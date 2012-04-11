package april.camera.calibrator;

import java.io.*;
import java.util.*;

import april.camera.*;
import april.graph.*;
import april.jmat.*;
import april.util.*;

public class GIntrinsicsNode extends GNode
{
    // inherited: init, state, truth, attributes

    private ParameterizableCalibration cal;

    public GIntrinsicsNode()
    {
    }

    public GIntrinsicsNode(ParameterizableCalibration cal)
    {
        this(cal, null);
    }

    public GIntrinsicsNode(ParameterizableCalibration cal, double truth[])
    {
        this.cal = cal;

        this.init = cal.getParameterization();
        this.state = LinAlg.copy(this.init);

        if (truth != null)
            this.truth = LinAlg.copy(truth);
    }

    public void updateIntrinsics()
    {
        cal.resetParameterization(this.state);
    }

    public double[] project(double xyz_camera[])
    {
        return cal.project(xyz_camera);
    }

    public int getDOF()
    {
        return init.length;
    }

    public double[] toXyzRpy(double s[])
    {
        assert(false);
        return new double[6];
    }

    public GNode copy()
    {
        GIntrinsicsNode node = new GIntrinsicsNode();
        node.init = LinAlg.copy(this.init);
        node.state = LinAlg.copy(this.state);
        if (this.truth != null)
            node.truth = LinAlg.copy(this.truth);
        node.attributes = Attributes.copy(this.attributes);

        node.cal = this.cal; // XXX need a copy method

        return node;
    }
}
