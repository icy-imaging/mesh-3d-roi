package plugins.adufour.roi.mesh;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.swing.filechooser.FileFilter;

import icy.common.exception.UnsupportedFormatException;
import icy.file.FileUtil;
import icy.gui.frame.progress.FileFrame;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.plugin.abstract_.PluginFileImporter;
import icy.sequence.Sequence;
import icy.system.thread.ThreadUtil;
import icy.type.DataType;
import icy.type.rectangle.Rectangle3D;
import plugins.adufour.roi.mesh.polygon.ROI3DPolygonalMesh;
import plugins.kernel.canvas.VtkCanvasPlugin;

public class VTKMeshImporter extends PluginFileImporter
{
    @Override
    public boolean acceptFile(String path)
    {
        if (FileUtil.isDirectory(path)) return false;
        return path.endsWith("vtk") || path.endsWith("vtk.txt");
    }
    
    @Override
    public List<FileFilter> getFileFilters()
    {
        FileFilter vtkFilter = new FileFilter()
        {
            @Override
            public String getDescription()
            {
                return "VTK mesh files";
            }
            
            @Override
            public boolean accept(File f)
            {
                return f.isDirectory() || acceptFile(f.getPath());
            }
        };
        
        return Arrays.asList(vtkFilter);
    }
    
    @Override
    public boolean load(String path, FileFrame loadingFrame) throws UnsupportedFormatException, IOException
    {
        Sequence sequence = getActiveSequence();
        boolean newSequence = false;
        
        if (sequence == null)
        {
            // Create a fake Sequence
            sequence = new Sequence(FileUtil.getFileName(path, false));
            addSequence(sequence);
            newSequence = true;
        }
        
        if (loadingFrame != null) loadingFrame.setTitle("Importing " + FileUtil.getFileName(path) + "...");
        
        final ROI3DMesh<?> mesh = new ROI3DPolygonalMesh();

//        mesh.setPixelSize(new Point3d(sequence.getPixelSizeX(), sequence.getPixelSizeY(), sequence.getPixelSizeZ()));
        mesh.loadFromVTK(new File(path));
        
        if (newSequence)
        {
            Rectangle3D.Integer bounds = mesh.getBounds();
            // Add a fake image
            sequence.addImage(new IcyBufferedImage(bounds.sizeX, bounds.sizeY, 1, DataType.UBYTE));
            
            final Sequence theSequence = sequence;
            ThreadUtil.invokeLater(new Runnable()
            {
                @Override
                public void run()
                {
                    Viewer viewer = theSequence.getFirstViewer();
                    if (viewer != null) viewer.setCanvas(VtkCanvasPlugin.class.getName());
                }
            }, true);
        }
        
        sequence.addROI(mesh);
        
        return true;
    }
}
