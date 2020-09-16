package plugins.adufour.roi.mesh.polyhedron;

import java.io.File;

import javax.vecmath.Tuple3d;

import icy.roi.BooleanMask3D;
import icy.vtk.IcyVtkPanel;
import icy.vtk.VtkUtil;
import plugins.adufour.roi.mesh.ROI3DMesh;
import plugins.adufour.roi.mesh.polygon.ROI3DPolygonalMesh;
import vtk.CellType;
import vtk.vtkCell;
import vtk.vtkCellArray;
import vtk.vtkDataSetMapper;
import vtk.vtkDataSetSurfaceFilter;
import vtk.vtkIdList;
import vtk.vtkImageData;
import vtk.vtkMapper;
import vtk.vtkPointSet;
import vtk.vtkPoints;
import vtk.vtkPolyData;
import vtk.vtkUnstructuredGrid;
import vtk.vtkUnstructuredGridReader;
import vtk.vtkXMLUnstructuredGridReader;

/**
 * <h2>WARNING: Polyhedral meshes are still under development, do *not* use for
 * production work!</h2>
 * <p>
 * 3D <u>r</u>egion <u>o</u>f <u>i</u>nterest (ROI) defined as a polyhedral mesh
 * (i.e. a connected set of 3D polyhedrons defining the interior of the ROI).
 * <p>
 * <p>
 * This data structure is the dual of {@link ROI3DPolygonalMesh polygon meshes}
 * which only defines the contour of the 3D ROI.
 * 
 * @author Alexandre Dufour
 */
public class ROI3DPolyhedralMesh extends ROI3DMesh<Polyhedron3D>
{
    public class PolyhedralMeshPainter extends MeshPainter
    {
        @Override
        protected vtkMapper createVTKMapper()
        {
            return new vtkDataSetMapper();
        }
    }

    /**
     * <h1>DO NOT USE! This constructor is for XML loading purposes only</h1>
     */
    public ROI3DPolyhedralMesh()
    {
        // throw new IcyHandledException("3D Polyhedron ROI are under development");
    }

    @Override
    protected ROIPainter createPainter()
    {
        return new PolyhedralMeshPainter();
    }

    @Override
    protected vtkPointSet createVTKMesh()
    {
        return new vtkUnstructuredGrid();
    }

    protected vtkUnstructuredGrid getVtkGrid()
    {
        return (vtkUnstructuredGrid) getVTKMesh();
    }

    /**
     * {@inheritDoc}
     * <h3>Currently supported cell types:</h3>
     * <ul>
     * <li>{@link Tetrahedron3D}</li>
     * <li>{@link Wedge3D}</li>
     * <li>{@link Pyramid3D}</li>
     * </ul>
     */
    @Override
    public Polyhedron3D createCell(final CellType type, final int... vertexIndices)
    {
        if (type == CellType.POLYGON)
            throw new IllegalArgumentException("Cannot create a polygon for a polyhedral mesh");

        switch (type)
        {
            case PYRAMID:
                return new Pyramid3D(vertexIndices);
            case WEDGE:
                return new Wedge3D(vertexIndices);
            case TETRA:
                return new Tetrahedron3D(vertexIndices);
            default:
                throw new UnsupportedOperationException(
                        "Cannot insert a cell of type: " + type + " into a polyhedral ROI");
        }
    }

    @Override
    protected void updateVTKMesh(final vtkPointSet vtkPointSet, final Tuple3d pixelSize)
    {
        // we have vtkUnstructuredGrid here
        final vtkUnstructuredGrid vtkGrid = (vtkUnstructuredGrid) vtkPointSet;

        // build new points and new cells
        final vtkPoints newPoints = getVtkPoints(pixelSize);
        final vtkCellArray newCells = getVtkCells();

        // accessing shared VTK objects
        final IcyVtkPanel vtkPanel = getMeshPainter().getVtkPanel();

        if (vtkPanel != null)
            vtkPanel.lock();
        try
        {
            synchronized (vtkInternalLock)
            {
                // lock vtkGrid as well
                synchronized (vtkGrid)
                {
                    // set new points
                    final vtkPoints oldPoints = vtkGrid.GetPoints();
                    vtkGrid.SetPoints(newPoints);
                    if (oldPoints != null)
                        oldPoints.Delete();

                    // get VTK cell type
                    int cellType = -1;
                    if (!cells.isEmpty())
                        cellType = cells.get(0).createVTKCell().GetCellType();

                    // set new cells
                    final vtkCellArray oldCells = vtkGrid.GetCells();
                    vtkGrid.SetCells(cellType, newCells);
                    if (oldCells != null)
                        oldCells.Delete();

                    // notify changes
                    vtkGrid.Modified();
                }
            }
        }
        finally
        {
            if (vtkPanel != null)
                vtkPanel.unlock();
        }
    }

    // @Override
    // public double getNumberOfContourPoints()
    // {
    // double surface = Double.NaN;
    //
    // // TODO find the outer surface of this polyhedral mesh, and calculate its surface area
    //
    // return surface;
    // }
    //
    // @Override
    // public double getNumberOfPoints()
    // {
    // double volume = 0;
    //
    // for (Polyhedron3D polyhedron : cells)
    // volume += polyhedron.computeVolume(vertices);
    //
    // return volume;
    // }

    @Override
    public void loadFromVTK(final File vtkFile, final boolean useLegacyReader)
    {
        final vtkUnstructuredGrid gridData;

        if (useLegacyReader)
        {
            final vtkUnstructuredGridReader legacyReader = new vtkUnstructuredGridReader();
            legacyReader.SetFileName(vtkFile.getPath());
            legacyReader.Update();
            legacyReader.GetOutput();
            gridData = legacyReader.GetOutput();
            legacyReader.Delete();
        }
        else
        {
            final vtkXMLUnstructuredGridReader reader = new vtkXMLUnstructuredGridReader();
            reader.SetFileName(vtkFile.getPath());
            reader.Update();
            gridData = reader.GetOutput();
            reader.Delete();
        }

        loadFromVTK(gridData);
    }

    @Override
    protected void addVtkCell(final vtkCell cell)
    {
        final CellType cellType = CellType.GetCellType(cell.GetCellType());
        final vtkIdList ids = cell.GetPointIds();
        final int nId = ids.GetNumberOfIds();
        final int[] vertexIndices = new int[nId];

        for (int ind = 0; ind < nId; ind++)
            vertexIndices[ind] = ids.GetId(ind);

        addCell(createCell(cellType, vertexIndices));
    }

    // private boolean load(vtkUnstructuredGrid gridData)
    // {
    // // load points (generically)
    // setVertexData(gridData.GetPoints());
    //
    // // load cells
    // vtkCellArray gridCells = gridData.GetCells();
    //
    // int[] indexBuffer = VtkUtil.getArray(gridCells.GetData());
    //
    // int nCells = gridCells.GetNumberOfCells();
    // for (int cellID = 0, idx = 0; cellID < nCells; cellID++)
    // {
    // vtkCell cell = gridData.GetCell(cellID);
    //
    // int nPointsPerFace = indexBuffer[idx++];
    // int[] vertexIndices = new int[nPointsPerFace];
    // System.arraycopy(indexBuffer, idx, vertexIndices, 0, nPointsPerFace);
    // addCell(createCell(CellType.GetCellType(cell.GetCellType()), vertexIndices));
    // idx += nPointsPerFace;
    // }
    //
    // return true;
    // }

    @Override
    public BooleanMask3D buildMask()
    {
        final vtkPointSet mesh = getVTKMesh();

        // transform VTK mesh to polydata first
        final vtkDataSetSurfaceFilter surfaceFilter = new vtkDataSetSurfaceFilter();
        final vtkPolyData polyData;
        final vtkImageData binaryImage;

        // prevent modification of mesh during filtering
        synchronized (mesh)
        {
            surfaceFilter.SetInputData(mesh);
            surfaceFilter.Update();
            polyData = surfaceFilter.GetOutput();
        }

        try
        {
            // get mask from polydata (may fail if the polyData is too large)
            binaryImage = VtkUtil.getBinaryImageData(polyData, null);
        }
        catch (final Exception e)
        {
            // cleanup
            polyData.Delete();
            surfaceFilter.Delete();

            // return empty mask 3D
            return new BooleanMask3D();
        }

        final BooleanMask3D result = VtkUtil.getBooleanMaskFromBinaryImage(binaryImage, false);

        // cleanup
        binaryImage.Delete();
        polyData.Delete();
        surfaceFilter.Delete();

        return result;
    }
}
