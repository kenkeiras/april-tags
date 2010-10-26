package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.io.*;

import april.util.*;

public class VisGeoImageSet implements VisObject
{
    GPSLinearization gpslin;
    ArrayList<Tile> tiles = new ArrayList<Tile>();
    String dirpath;
    Color  modulateColor;

    static class Tile
    {
        VisImage vim;
        double M[][];
    }

    public VisGeoImageSet(String dirpath, GPSLinearization gpslin, boolean asyncLoad) throws IOException
    {
        this.gpslin = gpslin;
        this.dirpath = dirpath;
        this.modulateColor = Color.white;

        if (!asyncLoad)
            load();
        else
            new LoadThread().start();
    }

    public void setModulateColor(Color c)
    {
        this.modulateColor = c;
    }

    public GPSLinearization getGPSLinearization()
    {
        return gpslin;
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        synchronized(tiles) {
            for (Tile tile : tiles) {
                gl.glPushMatrix();
                VisUtil.multiplyMatrix(gl, tile.M);
                tile.vim.modulateColor = modulateColor;
                tile.vim.render(vc, gl, glu);
                gl.glPopMatrix();
            }
        }
    }

    class LoadThread extends Thread
    {
        public void run()
        {
            try {
                load();
            } catch (IOException ex) {
                System.out.println("ex: "+ex);
            }
        }
    }

    void load() throws IOException
    {
        File dir = new File(dirpath);
        if (!dir.exists()) {
            System.out.println("VisGeoImageSet: path not found: "+dirpath);
            return;
        }

        File files[] = null;

        if (dir.isDirectory())
            files = dir.listFiles();
        else
            files = new File[] { dir };

        Arrays.sort(files);

        for (File file : files) {
            if (file.getName().endsWith(".png")) {
                GeoImage geoim = new GeoImage(file.getPath(), gpslin);

                BufferedImage im = geoim.getImage();
                VisTexture vt = new VisTexture(im);
                vt.lock();
                VisImage vim = new VisImage(vt, new double[2], new double[] { im.getWidth(), im.getHeight() });

                Tile tile = new Tile();
                tile.vim = vim;
                tile.M = geoim.getMatrix();

                synchronized(tiles) {
                    tiles.add(tile);
                }
            }
        }
    }

}
