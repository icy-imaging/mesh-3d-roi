package plugins.adufour.roi.mesh;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix3d;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.canvas.IcyCanvas;
import icy.painter.VtkPainter;
import icy.roi.BooleanMask2D;
import icy.roi.BooleanMask3D;
import icy.roi.ROI3D;
import icy.roi.ROIUtil;
import icy.sequence.Sequence;
import icy.type.point.Point5D;
import icy.type.rectangle.Rectangle3D;
import icy.util.XMLUtil;
import icy.vtk.IcyVtkPanel;
import icy.vtk.VtkUtil;
import plugins.adufour.roi.mesh.polygon.Polygon3D;
import plugins.adufour.roi.mesh.polygon.ROI3DPolygonalMesh;
import plugins.adufour.roi.mesh.polyhedron.ROI3DPolyhedralMesh;
import plugins.kernel.canvas.VtkCanvas;
import plugins.kernel.roi.roi2d.ROI2DArea;
import plugins.kernel.roi.roi2d.ROI2DArea.ROI2DAreaPainter;
import plugins.kernel.roi.roi3d.ROI3DArea;
import vtk.CellType;
import vtk.vtkActor;
import vtk.vtkCell;
import vtk.vtkCellArray;
import vtk.vtkMapper;
import vtk.vtkOutlineFilter;
import vtk.vtkPointSet;
import vtk.vtkPoints;
import vtk.vtkPolyDataMapper;
import vtk.vtkProp;
import vtk.vtkUnsignedCharArray;
import vtk.vtkXMLDataSetWriter;

/**
 * Generic class defining a 3D region of interest using a discrete 3D geometry representation. This
 * library provides the two major types of mesh representation:
 * <ul>
 * <li>Polygonal meshes (defining the region via its surface, see {@link ROI3DPolygonalMesh})</li>
 * <li>Polyhederal meshes (defining the region by its interior, see {@link ROI3DPolyhedralMesh})
 * </li>
 * </ul>
 * Be aware than ROI in Icy <b>should not</b> consider pixel size (except for 3D rendering part) so it's important than 3D ROI are always expressed in
 * <b>voxel</b>.<br>
 * The VTK library provided with Icy already contains all the necessary tools to manipulate and
 * visualize 3D data sets, yet its low-levelness can be complex for beginners, while potential bugs
 * caused in the C++ back-end may cause the entire application to crash instead of generating
 * elegant bug reports. The main benefits of this library are listed below: <br/>
 * <br/>
 * <ul>
 * <li>Intermediate Java API, much simpler to use than the VTK back-end</li>
 * <li>Native integration with Icy's Sequence / Viewer / ROI system</li>
 * <li>Eliminates most of the boiler-plate to simplify user code as much as possible</li>
 * </ul>
 * 
 * @author Alexandre Dufour
 */
public abstract class ROI3DMesh<C extends Cell3D> extends ROI3D
{
    // single lock for VTK access
    protected final static Object vtkInternalLock = new Object();

    public static Point3d vecMul(Point3d source, Tuple3d scale, Point3d dest)
    {
        final Point3d result = (dest == null) ? new Point3d() : dest;

        if (scale == null)
            result.set(source);
        else
            result.set(source.x * scale.x, source.y * scale.y, source.z * scale.z);

        return result;
    }

    // /**
    // * Returns a native java array from the specified {@link vtkDataArray}.
    // */
    // public static Object getJavaArray(vtkDataArray dataArray)
    // {
    // switch (dataArray.GetDataType())
    // {
    // case VtkUtil.VTK_UNSIGNED_CHAR:
    // return ((vtkUnsignedCharArray) dataArray).GetJavaArray();
    // case VtkUtil.VTK_SHORT:
    // return ((vtkShortArray) dataArray).GetJavaArray();
    // case VtkUtil.VTK_UNSIGNED_SHORT:
    // return ((vtkUnsignedShortArray) dataArray).GetJavaArray();
    // case VtkUtil.VTK_INT:
    // return ((vtkIntArray) dataArray).GetJavaArray();
    // case VtkUtil.VTK_UNSIGNED_INT:
    // return ((vtkUnsignedIntArray) dataArray).GetJavaArray();
    // case VtkUtil.VTK_LONG:
    // return ((vtkLongArray) dataArray).GetJavaArray();
    // case VtkUtil.VTK_UNSIGNED_LONG:
    // return ((vtkUnsignedLongArray) dataArray).GetJavaArray();
    // case VtkUtil.VTK_FLOAT:
    // return ((vtkFloatArray) dataArray).GetJavaArray();
    // case VtkUtil.VTK_DOUBLE:
    // return ((vtkDoubleArray) dataArray).GetJavaArray();
    // default:
    // return null;
    // }
    // }

    // /**
    // * Returns a {@link BooleanMask3D} from a binary (0/1 values) {@link vtkImageData}.
    // */
    // public static BooleanMask3D getBooleanMaskFromBinaryImage(vtkImageData image)
    // {
    // final vtkDataArray data = image.GetPointData().GetScalars();
    // final double[] origin = image.GetOrigin();
    // final int[] dim = image.GetDimensions();
    // final int sizeX = dim[0];
    // final int sizeY = dim[1];
    // final int sizeZ = dim[2];
    // final int sizeXY = sizeX * sizeY;
    // final Rectangle bounds2D = new Rectangle((int) origin[0], (int) origin[1], sizeX, sizeY);
    // final BooleanMask2D[] masks = new BooleanMask2D[sizeZ];
    //
    // // more than 200M ? use simple iterator (slower but consume less memory)
    // if ((sizeXY * sizeZ) > (200 * 1024 * 1024))
    // {
    // int off = 0;
    // for (int z = 0; z < sizeZ; z++)
    // {
    // final boolean[] mask = new boolean[sizeXY];
    //
    // for (int xy = 0; xy < sizeXY; xy++)
    // mask[xy] = (data.GetTuple1(off++) != 0d);
    //
    // masks[z] = new BooleanMask2D(new Rectangle(bounds2D), mask);
    // }
    // }
    // else
    // {
    // final Object javaArray = getJavaArray(data);
    // int off = 0;
    //
    // switch (ArrayUtil.getDataType(javaArray))
    // {
    // case BYTE:
    // final byte[] javaByteArray = (byte[]) javaArray;
    //
    // for (int z = 0; z < sizeZ; z++)
    // {
    // final boolean[] mask = new boolean[sizeXY];
    //
    // for (int xy = 0; xy < mask.length; xy++)
    // mask[xy] = (javaByteArray[off++] != 0);
    //
    // masks[z] = new BooleanMask2D(new Rectangle(bounds2D), mask);
    // }
    // break;
    //
    // case SHORT:
    // final short[] javaShortArray = (short[]) javaArray;
    //
    // for (int z = 0; z < sizeZ; z++)
    // {
    // final boolean[] mask = new boolean[sizeXY];
    //
    // for (int xy = 0; xy < mask.length; xy++)
    // mask[xy] = (javaShortArray[off++] != 0);
    //
    // masks[z] = new BooleanMask2D(new Rectangle(bounds2D), mask);
    // }
    // break;
    //
    // case INT:
    // final int[] javaIntArray = (int[]) javaArray;
    //
    // for (int z = 0; z < sizeZ; z++)
    // {
    // final boolean[] mask = new boolean[sizeXY];
    //
    // for (int xy = 0; xy < mask.length; xy++)
    // mask[xy] = (javaIntArray[off++] != 0);
    //
    // masks[z] = new BooleanMask2D(new Rectangle(bounds2D), mask);
    // }
    // break;
    //
    // case LONG:
    // final long[] javaLongArray = (long[]) javaArray;
    //
    // for (int z = 0; z < sizeZ; z++)
    // {
    // final boolean[] mask = new boolean[sizeXY];
    //
    // for (int xy = 0; xy < mask.length; xy++)
    // mask[xy] = (javaLongArray[off++] != 0L);
    //
    // masks[z] = new BooleanMask2D(new Rectangle(bounds2D), mask);
    // }
    // break;
    //
    // case FLOAT:
    // final float[] javaFloatArray = (float[]) javaArray;
    //
    // for (int z = 0; z < sizeZ; z++)
    // {
    // final boolean[] mask = new boolean[sizeXY];
    //
    // for (int xy = 0; xy < mask.length; xy++)
    // mask[xy] = (javaFloatArray[off++] != 0f);
    //
    // masks[z] = new BooleanMask2D(new Rectangle(bounds2D), mask);
    // }
    // break;
    //
    // case DOUBLE:
    // final double[] javaDoubleArray = (double[]) javaArray;
    //
    // for (int z = 0; z < sizeZ; z++)
    // {
    // final boolean[] mask = new boolean[sizeXY];
    //
    // for (int xy = 0; xy < mask.length; xy++)
    // mask[xy] = (javaDoubleArray[off++] != 0d);
    //
    // masks[z] = new BooleanMask2D(new Rectangle(bounds2D), mask);
    // }
    // break;
    //
    // default:
    // // nothing to do here
    // break;
    // }
    // }
    //
    // // don't optimize bounds (we prefer plain image size for the mask here)
    // return new BooleanMask3D(new Rectangle3D.Integer(bounds2D.x, bounds2D.y, (int) origin[2], sizeX, sizeY, sizeZ),
    // masks);
    // }

    public abstract class MeshPainter extends ROIPainter implements VtkPainter
    {
        protected boolean showBoundingBoxIn2D;

        /**
         * The size of a pixel in real units.<br>
         * USed to detect change in pixel size for VTK correct aspect rendering.
         */
        protected final Tuple3d pixelSize;

        protected final vtkActor mainActor;
        protected final vtkActor outlineActor;
        protected final vtkMapper mapper;

        /**
         * Internal VTK mesh pixel size scaled for rendering
         */
        protected final vtkPointSet vtkRenderingMesh;
        /**
         * Filter used to create the bounding box of the ROI (shown when the ROI is selected)
         */
        protected final vtkOutlineFilter vtkOutlineFilter;
        /**
         * Custom coloring
         */
        protected vtkUnsignedCharArray vtkColors;

        /**
         * <ul>
         * <li><code>true</code> mesh vertices are colored according to the {@link #updateVTKColor()}
         * method</li>
         * <li><code>false</code> mesh vertices are colored uniformly (at the VTK actor level) using the
         * ROI color</li>
         * </ul>
         */
        protected boolean vtkCustomColoring;

        protected boolean needRebuild;
        protected boolean needColorUpdate;

        protected WeakReference<VtkCanvas> canvas3d;

        public MeshPainter()
        {
            super();

            pixelSize = new Point3d();
            showBoundingBoxIn2D = false;

            mainActor = new vtkActor();
            outlineActor = new vtkActor();

            vtkColors = new vtkUnsignedCharArray();
            vtkColors.SetNumberOfComponents(3);

            // cells used for rendering (scaled using pixel size)
            vtkRenderingMesh = createVTKMesh();

            vtkOutlineFilter = new vtkOutlineFilter();
            vtkOutlineFilter.SetInputData(vtkRenderingMesh);

            vtkCustomColoring = false;

            // create a new mapper for the mesh data
            mapper = createVTKMapper();
            mapper.SetInputDataObject(vtkRenderingMesh);
            mainActor.SetMapper(mapper);
            mainActor.PickableOn();

            // outline (shown when the ROI is selected)
            final vtkPolyDataMapper outlineMapper = new vtkPolyDataMapper();
            outlineMapper.SetInputConnection(vtkOutlineFilter.GetOutputPort());
            outlineActor.SetMapper(outlineMapper);
            outlineActor.PickableOff();
            outlineActor.VisibilityOff();

            needRebuild = true;
            needColorUpdate = true;
            canvas3d = new WeakReference<VtkCanvas>(null);
        }

        @Override
        protected void finalize() throws Throwable
        {
            super.finalize();

            // release allocated VTK resources
            if (mainActor != null)
                mainActor.Delete();
            // release allocated VTK resources
            if (mapper != null)
                mapper.Delete();
            if (vtkRenderingMesh != null)
            {
                vtkRenderingMesh.GetPointData().GetScalars().Delete();
                vtkRenderingMesh.GetPointData().Delete();
                vtkRenderingMesh.Delete();
            }
            if (outlineActor != null)
            {
                if (outlineActor.GetMapper() != null)
                    outlineActor.GetMapper().Delete();
                outlineActor.SetPropertyKeys(null);
                outlineActor.Delete();
            }
            if (vtkOutlineFilter != null)
                vtkOutlineFilter.Delete();
            if (vtkColors != null)
                vtkColors.Delete();
        };

        public IcyVtkPanel getVtkPanel()
        {
            final VtkCanvas canvas = canvas3d.get();
            // canvas not yet open or closed
            if (canvas == null)
                return null;

            return canvas.getVtkPanel();
        }

        /**
         * @return a new VTK mapper that will be used to map the mesh data on screen
         */
        protected abstract vtkMapper createVTKMapper();

        /**
         * This method performs custom coloring of the mesh vertices. It is used only if
         * {@link #vtkCustomColoring} is set to <code>true</code>.<br/>
         * NOTE: the default implementation will color the mesh uniformly using the ROI's color (i.e. it
         * is equivalent to setting {@link #vtkCustomColoring} to <code>false</code>) and therefore has
         * limited interest. However, this method can be overridden (and used as a sample code) to
         * provide custom vertex coloring along the mesh.
         */
        protected void updateVTKRenderingColor()
        {
            // Color color = getColor();
            // int r = color.getRed(), g = color.getGreen(), b = color.getBlue();
            //
            // int nVerts = vtkCells.GetNumberOfPoints();
            // vtkColors.SetNumberOfTuples(nVerts);
            //
            // for (int i = 0; i < nVerts; i++)
            // vtkColors.SetTuple3(i, r, g, b);

            final IcyVtkPanel vtkPanel = getVtkPanel();

            if (vtkPanel != null)
                vtkPanel.lock();
            try
            {
                synchronized (vtkInternalLock)
                {
                    synchronized (vtkRenderingMesh)
                    {
                        vtkRenderingMesh.GetPointData().SetScalars(vtkColors);
                    }
                }
            }
            finally
            {
                if (vtkPanel != null)
                    vtkPanel.unlock();
            }
        }

        @Override
        public void setColor(Color color)
        {
            outlineActor.GetProperty().SetColor(color.getRed() / 255., color.getGreen() / 255., color.getBlue() / 255.);
            mainActor.GetProperty().SetColor(color.getRed() / 255., color.getGreen() / 255., color.getBlue() / 255.);

            vtkCustomColoring = false;

            super.setColor(color);
        }

        public void setColorPerVertex(Color[] colors)
        {
            final IcyVtkPanel vtkPanel = getVtkPanel();

            if (vtkPanel != null)
                vtkPanel.lock();
            try
            {
                synchronized (vtkInternalLock)
                {
                    if (vtkColors.GetNumberOfTuples() != colors.length)
                        vtkColors.SetNumberOfTuples(colors.length);

                    for (int i = 0; i < colors.length; i++)
                    {
                        final Color color = colors[i];
                        vtkColors.SetTuple3(i, color.getRed(), color.getGreen(), color.getBlue());
                    }
                }
            }
            finally
            {
                if (vtkPanel != null)
                    vtkPanel.unlock();
            }

            vtkCustomColoring = true;
            needColorUpdate = true;
        }

        public void setPixelSize(Tuple3d value)
        {
            pixelSize.set(value);

            needRebuild = true;
        }

        @Override
        public vtkProp[] getProps()
        {
            return new vtkProp[] {mainActor, outlineActor};
        }

        @Override
        public void mouseClick(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            if (e.isConsumed())
                return;
            // not active here
            if (!isActiveFor(canvas))
                return;

            if (canvas instanceof VtkCanvas)
            {
                final VtkCanvas vtk = (VtkCanvas) canvas;
                // pick
                final vtkProp prop = vtk.pickProp(e.getX(), e.getY());

                if (prop == this.mainActor)
                    setSelected(!isSelected());
            }

            super.mouseClick(e, imagePoint, canvas);
        }

        @Override
        public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
        {
            super.paint(g, sequence, canvas);

            if (canvas instanceof VtkCanvas)
            {
                // 3D canvas
                final VtkCanvas cnv = (VtkCanvas) canvas;

                // update reference if needed
                if (canvas3d.get() != cnv)
                    canvas3d = new WeakReference<VtkCanvas>(cnv);

                final IcyVtkPanel vtkPanel = cnv.getVtkPanel();
                if (vtkPanel != null)
                    vtkPanel.lock();
                try
                {
                    synchronized (vtkInternalLock)
                    {
                        if (!isActiveFor(canvas))
                        {
                            // hide all
                            outlineActor.VisibilityOff();
                            mainActor.PickableOff();
                            mainActor.VisibilityOff();
                            // nothing more to do
                            return;
                        }

                        // set mesh visible
                        mainActor.VisibilityOn();
                        mainActor.PickableOn();

                        if (isSelected())
                            outlineActor.VisibilityOn();
                        else
                            outlineActor.VisibilityOff();

                        // FIXME : need a better implementation
                        final Point3d newPixelSize = new Point3d(cnv.getVolumeScale());

                        if (!pixelSize.equals(newPixelSize))
                            setPixelSize(newPixelSize);

                        // need to rebuild rendering mesh ?
                        if (needRebuild)
                        {
                            // do it in the rendering thread (slow and laggy but easier than using canvas locking)
                            updateVTKMesh(vtkRenderingMesh, pixelSize);
                            // update outline
                            vtkOutlineFilter.Update();
                            // mapper.Update();
                            needRebuild = false;
                            needColorUpdate = true;
                        }
                        if (vtkCustomColoring && needColorUpdate)
                        {
                            // update color information if needed
                            updateVTKRenderingColor();
                            needColorUpdate = false;
                        }
                    }
                }
                finally
                {
                    if (vtkPanel != null)
                        vtkPanel.unlock();
                }
            }
            else // 2D viewer
            {
                // only paint detections on the current frame
                if (!isActiveFor(canvas))
                    return;

                final int posZ = canvas.getPositionZ();
                final Rectangle3D bounds = getBounds3D();

                if (posZ >= bounds.getMinZ() && posZ <= bounds.getMaxZ())
                {
                    if (showBoundingBoxIn2D)
                    {
                        setStroke(3.0);
                        g.setStroke(new BasicStroke((float) getAdjustedStroke(canvas)));
                        g.setColor(getColor());

                        g.draw(new Rectangle2D.Double(bounds.getMinX(), bounds.getMinY(), bounds.getSizeX(),
                                bounds.getSizeY()));
                    }
                    else
                    {
                        final ROI2DArea slice = getROIMask().getSlice(posZ);

                        if (slice != null)
                        {
                            final ROI2DAreaPainter maskPainter = slice.getOverlay();

                            maskPainter.setColor(getColor());
                            maskPainter.paint(g, sequence, canvas);
                        }
                    }
                }
            }
        }

        @Override
        public void remove()
        {
            final IcyVtkPanel vtkPanel = getVtkPanel();

            if (vtkPanel != null)
                vtkPanel.lock();
            try
            {
                synchronized (vtkInternalLock)
                {
                    mainActor.VisibilityOff();
                    outlineActor.VisibilityOff();
                }
            }
            finally
            {
                if (vtkPanel != null)
                    vtkPanel.unlock();
            }

            super.remove();
        }
    }

    // /** Multi-thread processor used for various mesh operations */
    // protected final static Processor processor = new Processor(SystemUtil.getNumberOfCPUs());

    /**
     * The list of cells forming this mesh. Note that any change brought to this list should be
     * followed by a call to {@link #roiChanged()} to update visualization
     */
    protected final List<C> cells;

    /**
     * The list of mesh vertices. Note that any change brought to this list should be followed by a
     * call to {@link #roiChanged()} to update visualization
     */
    protected List<Vertex3D> vertices;

    /**
     * cached mask representation of this ROI (useful for 2D display and intensity measurements)
     */
    private BooleanMask3D mask;
    private ROI3DArea roiMask;
    /**
     * cached mesh in VTK format (useful for some operations)
     */
    private vtkPointSet vtkMesh;
    /**
     * cached mass center
     */
    private final Point3d massCenter;

    protected boolean maskNeedUpdate;
    protected boolean vtkMeshNeedUpdate;
    protected boolean propertiesNeedUpdate;

    public ROI3DMesh()
    {
        super();

        cells = new ArrayList<C>();
        vertices = new ArrayList<Vertex3D>();

        // default mask
        mask = new BooleanMask3D();
        roiMask = new ROI3DArea();
        // internal VTK mesh object
        vtkMesh = createVTKMesh();
        massCenter = new Point3d();

        vtkMeshNeedUpdate = false;
        maskNeedUpdate = false;
        propertiesNeedUpdate = false;
    }

    @SuppressWarnings("unchecked")
    public MeshPainter getMeshPainter()
    {
        return (MeshPainter) painter;
    }

    /**
     * Creates the VTK structure that will contain the mesh data
     * 
     * @return the VTK data holding the mesh contents
     * @see ROI3DPolygonalMesh#createVTKMesh()
     */
    protected abstract vtkPointSet createVTKMesh();

    // /**
    // * Updates the internal VTK structure for display purposes
    // */
    // protected void updateVTK()
    // {
    // // 1) Update the vertex buffer first
    // int nVerts = vertices.size();
    //
    // // take the opportunity to update the mass center
    // int nRealVerts = 0;
    // Point3d center = new Point3d();
    //
    // vtkPoints vtkPoints = new vtkPoints();
    // vtkPoints.SetNumberOfPoints(nVerts);
    //
    // for (int i = 0; i < nVerts; i++)
    // {
    // Vertex3D v = vertices.get(i);
    //
    // if (v == null)
    // {
    // vtkPoints.SetPoint(i, Double.NaN, Double.NaN, Double.NaN);
    // }
    // else
    // {
    // nRealVerts++;
    //
    // vtkPoints.SetPoint(i, v.position.x, v.position.y, v.position.z);
    //
    // center.add(v.position);
    // }
    // }
    //
    // massCenter.scale(1.0 / nRealVerts, center);
    //
    // // 2) Update the cell data
    // updateVTKCells(vtkPoints);
    //
    // // release VTK object when done
    // vtkPoints.Delete();
    // }

    /**
     * Get VTK points from internal vertices data
     */
    protected vtkPoints getVtkPoints(Tuple3d pixelSize)
    {
        // final vtkPoints result = new vtkPoints();
        final double sx = (pixelSize != null) ? pixelSize.x : 1d;
        final double sy = (pixelSize != null) ? pixelSize.y : 1d;
        final double sz = (pixelSize != null) ? pixelSize.z : 1d;
        final double[] points;
        final int nVerts;

        synchronized (vertices)
        {
            nVerts = vertices.size();
            points = new double[nVerts * 3];

            int off = 0;
            for (Vertex3D v : vertices)
            {
                if (v == null)
                {
                    points[off++] = Double.NaN;
                    points[off++] = Double.NaN;
                    points[off++] = Double.NaN;
                }
                else
                {
                    points[off++] = v.position.x * sx;
                    points[off++] = v.position.y * sy;
                    points[off++] = v.position.z * sz;
                }
            }
        }

        return VtkUtil.getPoints(points);

        // synchronized (vertices)
        // {
        // nVerts = vertices.size();
        //
        // result.SetNumberOfPoints(nVerts);
        //
        // for (int i = 0; i < nVerts; i++)
        // {
        // final Vertex3D v = vertices.get(i);
        //
        // if (v == null)
        // result.SetPoint(i, Double.NaN, Double.NaN, Double.NaN);
        // else
        // result.SetPoint(i, v.position.x * sx, v.position.y * sy, v.position.z * sz);
        // }
        // }
        //
        // return result;
    }

    /**
     * Get VTK cells from internal cells data
     */
    protected vtkCellArray getVtkCells()
    {
        final int cellCount;
        final int[] indexBuffer;

        synchronized (cells)
        {
            cellCount = cells.size();

            // calculate the total number of indices in the index buffer
            int indexBufferSize = 0;
            for (C c : cells)
                indexBufferSize += c.size + 1;

            indexBuffer = new int[indexBufferSize];

            // build the new cells
            int vertexID = 0;
            for (C c : cells)
            {
                indexBuffer[vertexID++] = c.size;
                for (int idx : c.vertexIndices)
                    indexBuffer[vertexID++] = idx;
            }
        }

        return VtkUtil.getCells(cellCount, indexBuffer);
    }

    /**
     * Update the given VTK structure from the mesh cells data
     */
    protected abstract void updateVTKMesh(vtkPointSet vtkPointSet, Tuple3d pixelSize);

    /**
     * Get the local internal VTK mesh structure
     */
    protected vtkPointSet getVTKMesh()
    {
        if (vtkMeshNeedUpdate)
        {
            updateVTKMesh(vtkMesh, null);
            vtkMeshNeedUpdate = false;
        }

        return vtkMesh;
    }

    @Override
    public boolean saveToXML(Node node)
    {
        if (!super.saveToXML(node))
            return false;

        try
        {
            // XMLUtil.setAttributeDoubleValue((Element) node, "PixelSizeXY", pixelSize.x);
            // XMLUtil.setAttributeDoubleValue((Element) node, "PixelSizeZ", pixelSize.z);

            // write to a temporary (VTK XML) file and re-read from it
            File file = File.createTempFile("mesh", ".vtk");
            file.deleteOnExit();

            saveToVTK(file);

            Document doc = XMLUtil.loadDocument(file);
            Node vtk = node.getOwnerDocument().importNode(doc.getDocumentElement(), true);
            node.appendChild(vtk);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean saveToVTK(File vtkFile)
    {
        optimizeVertexBuffer();

        vtkXMLDataSetWriter wr = new vtkXMLDataSetWriter();
        wr.SetInputData(getVTKMesh());
        wr.SetFileName(vtkFile.getPath());
        wr.Update();
        wr.Delete();

        return true;
    }

    @Override
    public boolean loadFromXML(Node node)
    {
        if (!super.loadFromXML(node))
            return false;

        try
        {
            Element e = (Element) node;
            // pixelSize.x = pixelSize.y = XMLUtil.getAttributeDoubleValue(e, "PixelSizeXY", 1.0);
            // pixelSize.z = XMLUtil.getAttributeDoubleValue(e, "PixelSizeZ", 1.0);

            // General strategy: store the data into a temp file, and let VTK read it
            File file = File.createTempFile("mesh", ".vtkfile");
            file.deleteOnExit();

            // legacy formats may store an ASCII representation of the file
            String vtkString = XMLUtil.getAttributeValue(e, "VTK", null);

            boolean useLegacyReader = (vtkString != null);

            if (useLegacyReader)
            {
                FileWriter writer = new FileWriter(file);
                writer.write(vtkString);
                writer.flush();
                writer.close();
            }
            else
            {
                // the format is probably XML
                Node vtkXML = XMLUtil.getChild(node, "VTKFile");
                if (vtkXML == null)
                {
                    return false;
                }

                Document doc = XMLUtil.createDocument(false);
                doc.appendChild(doc.importNode(XMLUtil.getChild(node, "VTKFile"), true));
                XMLUtil.saveDocument(doc, file);
            }

            loadFromVTK(file, useLegacyReader);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Loads the mesh data from the specified VTK file. This method will attempt to guess the format
     * of the specified file and call the appropriate loader, which will result in slightly slower
     * performance. If the format is known in advance, use the {@link #loadFromVTK(File, boolean)}
     * method instead.
     * 
     * @param vtkFile
     *        the VTK file to read from
     * @return <code>true</code> if the file successfully loaded, <code>false</code> otherwise
     */
    public void loadFromVTK(File vtkFile)
    {
        // How to test: try to load as XML, and switch to legacy if it fails
        final Document vtkXML = XMLUtil.loadDocument(vtkFile);
        loadFromVTK(vtkFile, vtkXML == null);
    }

    /**
     * Loads the mesh data from the specified VTK file
     * 
     * @param vtkFile
     *        the VTK file to read from
     * @param useLegacyReader
     *        <ul>
     *        <li><code>true</code> if the file uses the old legacy (plain text or binary)
     *        format</li>
     *        <li><code>false</code> if the file uses the (recommended) XML format</li>
     *        </ul>
     * @return <code>true</code> if the file successfully loaded, <code>false</code> otherwise
     */
    public abstract void loadFromVTK(File vtkFile, boolean useLegacyReader);

    /**
     * Add a cell data from the given VTK cell object
     * 
     * @param cell
     *        the VTK cell to add
     */
    protected abstract void addVtkCell(vtkCell cell);

    /**
     * Loads the mesh data from the specified vtkPointSet object (internal method)
     * 
     * @param vtkPointSet
     *        the VTK object to set mesh data from
     * @return <code>true</code> if the file successfully loaded, <code>false</code> otherwise
     */
    protected void loadFromVTK(vtkPointSet vtkPointSet)
    {
        synchronized (vertices)
        {
            vertices.clear();
        }
        synchronized (cells)
        {
            cells.clear();
        }

        final vtkPoints vtkPoints = vtkPointSet.GetPoints();

        if (vtkPoints != null)
        {
            // load points (generically)
            final int nPoints = vtkPoints.GetNumberOfPoints();
            for (int i = 0; i < nPoints; i++)
            {
                final double[] xyz = vtkPoints.GetPoint(i);
                final Point3d position = new Point3d(xyz);

                addVertex(createVertex(position), false, false);
            }
        }

        // load cells (generically)
        final int nCells = vtkPointSet.GetNumberOfCells();
        for (int c = 0; c < nCells; c++)
            addVtkCell(vtkPointSet.GetCell(c));

        roiChanged(true);
    }

    @Override
    public void roiChanged(boolean contentChanged)
    {
        if (contentChanged)
        {
            // lazy update
            maskNeedUpdate = true;
            vtkMeshNeedUpdate = true;
            propertiesNeedUpdate = true;
            getMeshPainter().needRebuild = true;
        }

        super.roiChanged(contentChanged);
    }

    /**
     * Creates a clone of the specified contour
     * 
     * @param contour
     */
    @SuppressWarnings("unchecked")
    @Override
    public ROI3DMesh<C> clone()
    {
        try
        {
            // clone the mesh
            final ROI3DMesh<C> clone = getClass().newInstance();

            clone.setVertexData(vertices, true);
            clone.setCellData(cells, true);

            clone.setColor(getColor());
            clone.setT(getT());
            clone.setC(getC());

            clone.roiChanged(true);

            return clone;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Clears and replaces the vertices of this mesh by the specified vertex array. This is a
     * low-level method for bulk assignment, therefore the ROI is not notified of any change to its
     * internal data (a call to {@link #roiChanged()} is required)<br/>
     * <br/>
     * <b>Note:</b> the vertices in the provided list are not copied, therefore any change to their
     * contents after calling this method will still affect this mesh
     * 
     * @param newVertices
     *        the new vertices of this mesh
     */
    protected void setVertexData(Collection<Vertex3D> newVertices, boolean cloneVertex)
    {
        synchronized (vertices)
        {
            vertices.clear();

            if (cloneVertex)
            {
                for (Vertex3D v : newVertices)
                    vertices.add((v != null) ? v.clone() : null);
            }
            else
            {
                for (Vertex3D v : newVertices)
                    vertices.add(v);
            }
        }
    }

    /**
     * Clears and replaces the cells of this mesh by the specified cell array. This is a low-level
     * method for bulk assignment, therefore the ROI is not notified of any change to its internal
     * data (a call to {@link #roiChanged()} is required)<br/>
     * <br/>
     * <b>Note:</b> the cells in the provided list are not copied, therefore any change to their
     * contents after calling this method will still affect this mesh
     * 
     * @param newCells
     *        the new cells of this mesh
     */
    @SuppressWarnings("unchecked")
    protected void setCellData(Collection<C> newCells, boolean cloneCell)
    {
        synchronized (cells)
        {
            cells.clear();

            if (cloneCell)
            {
                for (C c : newCells)
                    cells.add((c != null) ? (C) c.clone() : null);
            }
            else
            {
                for (C c : newCells)
                    cells.add(c);
            }
        }
    }

    /**
     * Creates and adds a new face to this mesh using the specified vertex indices within the mesh's
     * existing vertex buffer.
     * 
     * @param vertices
     *        the face vertices, in counter-clockwise ordering
     * @throws MeshTopologyException
     */
    public void addCell(C cell)
    {
        synchronized (cells)
        {
            cells.add(cell);
        }
    }

    /**
     * Creates a new cell for this mesh (without adding it), using the specified vertex indices.
     * <br/>
     * <br/>
     * This method is internally used when calling {@link #addCell(int[])}, allowing overriding
     * classes to provide their own {@link Polygon3D} implementation.
     * 
     * @param type
     *        the type of cell to add
     * @param vertexIndices
     *        the indices of the vertices forming the mesh, in counter-clockwise order
     * @return the newly created face
     */
    protected abstract C createCell(CellType type, int... vertexIndices);

    /**
     * @param index
     *        the index of the cell to retrieve
     * @return the cell stored at the specified index
     */
    public C getCell(int index)
    {
        return cells.get(index);
    }

    /**
     * @return the number of cells in this mesh
     */
    public int getNumberOfCells()
    {
        return cells.size();
    }

    /**
     * @return a safe copy of the list of cells (changes to this list will not affect the mesh)
     */
    public List<C> getCells()
    {
        return new ArrayList<C>(cells);
    }

    /**
     * Adds the specified vertex to this mesh (if no vertex already exists at this vertex location)
     * 
     * @param v
     *        the vertex to add
     * @return the index of the new vertex, or that of the old vertex at that location (if any)
     */
    public int addVertex(Vertex3D v)
    {
        return addVertex(v, true, true);
    }

    /**
     * Adds a new vertex to this mesh and returns its index in the vertex array. Based on the
     * specified options, if a vertex already existed at that location, this method will return the
     * index of the existing vertex instead of adding a new one.
     * 
     * @param v
     *        the coordinates of the vertex to create and add<br/>
     *        <br/>
     * @param mergeDuplicates
     *        <ul>
     *        <li><code>true</code>: checks whether this mesh already contains a vertex at the
     *        specified position (and should return its index instead of adding the specified
     *        vertex)</li>
     *        <li><code>false</code>: adds the specified vertex to the list, whether or not
     *        another exists at the same location (this is recommended when loading VTK files
     *        which should be consistent by default)</li>
     *        </ul>
     * @param tidy
     *        if the vertex is added, this parameter indicates how the vertex should be stored:
     *        <br/>
     *        <ul>
     *        <li><code>true</code>: the vertex is stored in the first available
     *        <code>null</code> position in the array (or at the end if none exist)</li>
     *        <li><code>false</code>: the vertex is always stored at the end of the array (this
     *        is only recommended when loading existing mesh data to preserve vertex ordering)
     *        </li>
     *        </ul>
     * @return the index of the new vertex, or that of the old vertex at that location (if any)
     */
    public int addVertex(Vertex3D v, boolean tidy, boolean mergeDuplicates)
    {
        if (!tidy && !mergeDuplicates)
        {
            // fast version (ideal to load organized data)
            vertices.add(v);
            return vertices.size() - 1;
        }

        int nullIndex = -1;
        for (int index = 0; index < vertices.size(); index++)
        {
            final Vertex3D existingVertex = vertices.get(index);

            if (existingVertex == null)
            {
                // not merging duplicate ?
                if (!mergeDuplicates)
                {
                    // use first free slot (as tidy is enabled)
                    setVertex(index, v);
                    return index;
                }

                // store the first free index
                if (nullIndex == -1)
                    nullIndex = index;
            }
            // merging enabled ? --> return existing vertex index
            else if (mergeDuplicates && existingVertex.position.epsilonEquals(v.position, 0.00001))
                return index;
        }

        // if there is a free spot in the list, use it
        if (tidy && (nullIndex >= 0))
        {
            setVertex(nullIndex, v);
            return nullIndex;
        }
        else
        {
            vertices.add(v);
            return vertices.size() - 1;
        }
    }

    /**
     * Creates a new vertex for this mesh (without adding it), using the specified 3D coordinates.
     * <br/>
     * <br/>
     * This method is internally used when calling {@link #addVertex(Point3d)}, allowing overriding
     * classes to provide their own {@link Vertex3D} implementation.
     * 
     * @param position
     *        the position of the created vertex
     * @return the newly created vertex
     */
    public Vertex3D createVertex(Point3d position)
    {
        return new Vertex3D(position);
    }

    /**
     * @param index
     * @return the vertex at the specified index in the buffer
     */
    public Vertex3D getVertex(int index)
    {
        return vertices.get(index);
    }

    /**
     * @return the size of the vertex buffer
     * @param excludeNullElements
     *        set to <code>true</code> if <code>null</code> elements should not be counted
     *        (yielding the actual number of vertices, which may be smaller than the buffer
     *        size)
     */
    public int getNumberOfVertices(boolean excludeNullElements)
    {
        if (!excludeNullElements)
            return vertices.size();

        int count = 0;
        for (Vertex3D v : vertices)
            if (v != null)
                count++;

        return count;
    }

    /**
     * @return a shallow copy of the list of vertices. Adding or removing elements from this list
     *         will not affect the mesh, however modifying the vertices inside this list will.
     */
    public List<Vertex3D> getVertices()
    {
        return new ArrayList<Vertex3D>(vertices);
    }

    /**
     * Replaces the vertex at the specified index in the vertex buffer
     * 
     * @param index
     *        the index of the vertex to replace
     * @param vertex
     *        the new vertex
     */
    public void setVertex(int index, Vertex3D vertex)
    {
        if (vertex == null)
            deleteVertex(index);
        else
            vertices.set(index, vertex);
    }

    /**
     * Set vertex at given index to <i>null</i> (take care of updating cells and neighbors)
     */
    public void deleteVertex(int index)
    {
        final Vertex3D v = vertices.get(index);

        // nothing to do
        if (v == null)
            return;

        synchronized (cells)
        {
            // remove cells referencing that vertex
            for (int i = cells.size() - 1; i >= 0; i--)
                if (cells.get(i).contains(index))
                    cells.remove(i);
        }

        synchronized (vertices)
        {
            final Integer indexInteger = Integer.valueOf(index);

            // remove from neighborhood
            for (Integer n : v.neighbors)
            {
                final Vertex3D vn = vertices.get(n.intValue());

                if (vn != null)
                    vn.neighbors.remove(indexInteger);
            }

            // finally remove the vertex (set it to null as we don't want to alter vertices index)
            vertices.set(index, null);
        }
    }

    /**
     * Checks mesh integrity and will print out to the console any missing vertex
     * 
     * @return <code>true</code> if the check succeeded.
     */
    public boolean checkMeshIntegrity()
    {
        boolean success = true;

        synchronized (cells)
        {
            synchronized (vertices)
            {
                for (C cell : cells)
                {
                    for (int i : cell.vertexIndices)
                    {
                        if (vertices.get(i) == null)
                        {
                            success = false;
                            System.err.println("missing vertex : " + i);
                        }
                    }
                }
            }
        }

        return success;
    }

    /**
     * Optimizes the vertex buffer by shifting all elements such that there are no <code>null</code>
     * elements intermingled between vertices.
     * 
     * @param tighten
     *        <ul>
     *        <li>if <code>true</code>: the vertex buffer is resized after optimization to
     *        ensure it does not contain empty values anymore</li>
     *        <li>if <code>false</code>: the size of the vertex buffer remains unchanged,
     *        therefore <code>null</code> elements may be found at the end of the buffer after
     *        optimization.</li>
     *        </ul>
     */
    public void optimizeVertexBuffer()
    {
        boolean needOpti = false;

        for (Vertex3D v : vertices)
        {
            if (v == null)
            {
                needOpti = true;
                break;
            }
        }

        // already optimized
        if (!needOpti)
            return;

        final int nVerts = vertices.size();
        final List<Vertex3D> newVertices = new ArrayList<Vertex3D>(nVerts);
        int blankSpaces = 0;

        synchronized (vertices)
        {
            synchronized (cells)
            {
                for (int i = 0; i < nVerts; i++)
                {
                    final Vertex3D vertex = vertices.get(i);

                    // count the number of consecutive blank spaces
                    if (vertex == null)
                    {
                        blankSpaces++;
                        continue;
                    }

                    newVertices.add(vertex);

                    if (blankSpaces > 0)
                    {
                        // redirect all cells to this space
                        for (C cell : cells)
                            cell.replace(i, i - blankSpaces);
                    }
                }
            }

            setVertexData(newVertices, false);
        }

        roiChanged(true);
    }

    /**
     * @param scale
     *        scale vector (pixel size) if we want to compute major axis using real metrics. Set it to <code>null</code> if not needed.
     * @return The major axis of this contour, i.e. an unnormalized vector linking the two most
     *         distant contour points (the orientation of this vector is arbitrary)
     */
    public Vector3d getMajorAxis(Tuple3d scale)
    {
        Vector3d axis = new Vector3d();
        int nbPoints = vertices.size();

        // TODO this is not optimal, geometric moments should be used

        double maxDistSq = 0;
        Vector3d vec = new Vector3d();
        Point3d p1 = new Point3d();
        Point3d p2 = new Point3d();

        synchronized (vertices)
        {
            for (int i = 0; i < nbPoints; i++)
            {
                Vertex3D v1 = vertices.get(i);
                if (v1 == null)
                    continue;

                // scale and get result in p1
                vecMul(v1.position, scale, p1);

                for (int j = i + 1; j < nbPoints; j++)
                {
                    Vertex3D v2 = vertices.get(j);
                    if (v2 == null)
                        continue;

                    // scale and get result in p2
                    vecMul(v2.position, scale, p2);

                    vec.sub(p1, p2);

                    double dSq = vec.lengthSquared();

                    if (dSq > maxDistSq)
                    {
                        maxDistSq = dSq;
                        axis.set(vec);
                    }
                }
            }
        }

        return axis;
    }

    // /**
    // * @return The major axis of this contour, i.e. an unnormalized vector linking the two most
    // * distant contour points (the orientation of this vector is arbitrary)
    // */
    // public Vector3d getMajorAxis()
    // {
    // return getMajorAxis(null);
    // }

    // /**
    // * @return the pixel size associated to this mesh
    // */
    // public Tuple3d getPixelSize()
    // {
    // return new Point3d(pixelSize);
    // }

    // public Point3d getMassCenter(boolean convertToImageSpace)
    // {
    // if (propertiesNeedUpdate)
    // {
    // updateProperties();
    // propertiesNeedUpdate = false;
    // }
    //
    // return convertToImageSpace
    // ? new Point3d(massCenter.x / pixelSize.x, massCenter.y / pixelSize.y, massCenter.z / pixelSize.z)
    // : new Point3d(massCenter);
    // }

    /**
     * @param scale
     *        scale vector (pixel size) if we want to get mass axis using real metrics. Set it to <code>null</code> if not needed.
     * @return The mass center
     */
    public Point3d getMassCenter(Tuple3d scale)
    {
        if (propertiesNeedUpdate)
        {
            updateProperties();
            propertiesNeedUpdate = false;
        }

        return vecMul(massCenter, scale, null);
    }

    // public Point3d getMassCenter()
    // {
    // return getMassCenter(null);
    // }

    /**
     * Calculates Returns a 3D cuboid representing the contour's bounding box. The bounding box is
     * defined as the smallest cuboid that entirely contains the contour.
     * 
     * @return a {@link icy.type.rectangle.Rectangle3D.Double} object containing the bounding box of
     *         this mesh, expressed in voxel (image) space
     */
    @Override
    public Rectangle3D computeBounds3D()
    {
        // if (pixelSize.x == 0 || pixelSize.y == 0 || pixelSize.z == 0)
        // {
        // throw new RuntimeException(
        // "Invalid pixel size: (" + pixelSize.x + "," + pixelSize.y + "," + pixelSize.z + ')');
        // }
        //
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = 0, maxY = 0, maxZ = 0;

        for (Vertex3D v : getVertices())
        {
            if (v == null)
                continue;

            if (v.position.x < minX)
                minX = v.position.x;
            if (v.position.x > maxX)
                maxX = v.position.x;
            if (v.position.y < minY)
                minY = v.position.y;
            if (v.position.y > maxY)
                maxY = v.position.y;
            if (v.position.z < minZ)
                minZ = v.position.z;
            if (v.position.z > maxZ)
                maxZ = v.position.z;
        }

        // // switch from real to voxel space
        // minX /= pixelSize.x;
        // minY /= pixelSize.y;
        // minZ /= pixelSize.z;
        // maxX /= pixelSize.x;
        // maxY /= pixelSize.y;
        // maxZ /= pixelSize.z;

        return new Rectangle3D.Double(minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    public BooleanMask3D getMask()
    {
        // ensure boolean mask is up to date
        if (maskNeedUpdate)
        {
            mask = buildMask();
            roiMask = new ROI3DArea(mask);
            ROIUtil.copyROIProperties(this, roiMask, false);
            maskNeedUpdate = false;
        }

        return mask;
    }

    public ROI3DArea getROIMask()
    {
        // ensure boolean mask is up to date
        if (maskNeedUpdate)
        {
            mask = buildMask();
            roiMask = new ROI3DArea(mask);
            ROIUtil.copyROIProperties(this, roiMask, false);
            maskNeedUpdate = false;
        }

        return roiMask;
    }

    public abstract BooleanMask3D buildMask();

    protected void updateProperties()
    {
        // update internal properties (mass center only here)
        final Point3d center = new Point3d();
        int numVertices = 0;

        synchronized (vertices)
        {
            for (Vertex3D v : vertices)
            {
                if (v != null)
                {
                    center.add(v.position);
                    numVertices++;
                }
            }
        }

        // mass center updated
        if (numVertices > 0)
            massCenter.scale(1d / numVertices, center);
        else
            massCenter.set(0d, 0d, 0d);
    }

    /**
     * Rotates this mesh by a specified angle in radians. The axis of rotation (specified in the
     * parameter) is assumed to pass through the center of mass of this mesh, and the rotation angle
     * is assumed anti-clockwise when facing the vector.
     * 
     * @param axisAngle
     *        the axis and angle (in radians) of rotation
     */
    public void rotate(AxisAngle4d axisAngle)
    {
        final Matrix3d r = new Matrix3d();
        r.set(axisAngle);

        final Point3d center = getMassCenter(null);

        synchronized (vertices)
        {
            for (Vertex3D v : vertices)
            {
                if (v == null)
                    continue;

                // translate to the center of rotation
                v.position.sub(center);
                // rotate
                r.transform(v.position);
                // translate back to place
                v.position.add(center);
            }
        }

        roiChanged(true);
    }

    @Override
    public boolean canTranslate()
    {
        return true;
    }

    @Override
    public void translate(double dx, double dy, double dz)
    {
        translate(new Vector3d(dx, dy, dz));
    }

    /**
     * Translates (every vertex of) this mesh by the specified vector
     * 
     * @param vector
     *        the translation vector
     */
    public void translate(Vector3d vector)
    {
        if (vector.lengthSquared() == 0.0)
            return;

        // if (Double.isNaN(vector.x) || Double.isNaN(vector.y) || Double.isNaN(vector.z))
        // {
        // System.out.println("oups !");
        // }

        synchronized (vertices)
        {
            for (Vertex3D v : vertices)
                if (v != null)
                    v.position.add(vector);
        }

        roiChanged(true);
    }

    @Override
    public boolean contains(double x, double y, double z, double sizeX, double sizeY, double sizeZ)
    {
        // return getROIMask().contains(x, y, z, sizeX, sizeY, sizeZ);

        // the specified box is contained if all of its corners are
        if (!contains(x, y, z))
            return false;
        if (!contains(x + sizeX, y, z))
            return false;
        if (!contains(x, y + sizeY, z))
            return false;
        if (!contains(x, y, z + sizeZ))
            return false;
        if (!contains(x + sizeX, y + sizeY, z))
            return false;
        if (!contains(x + sizeX, y, z + sizeZ))
            return false;
        if (!contains(x, y + sizeY, z + sizeZ))
            return false;
        if (!contains(x + sizeX, y + sizeY, z + sizeZ))
            return false;

        return true;
    }

    @Override
    public boolean hasSelectedPoint()
    {
        return false;
    }

    @Override
    public boolean intersects(double x, double y, double z, double sizeX, double sizeY, double sizeZ)
    {
        return getROIMask().intersects(x, y, z, sizeX, sizeY, sizeZ);

        // FIXME: What ?? i guess this don't really work... (Stephane)
        // the specified box intersects if any of its corners do
        // if (contains(x, y, z))
        // return true;
        // if (contains(x + sizeX, y, z))
        // return true;
        // if (contains(x, y + sizeY, z))
        // return true;
        // if (contains(x, y, z + sizeZ))
        // return true;
        // if (contains(x + sizeX, y + sizeY, z))
        // return true;
        // if (contains(x + sizeX, y, z + sizeZ))
        // return true;
        // if (contains(x, y + sizeY, z + sizeZ))
        // return true;
        // if (contains(x + sizeX, y + sizeY, z + sizeZ))
        // return true;
        //
        // return false;
    }

    /**
     * default implementation
     */
    @Override
    public boolean contains(double x, double y, double z)
    {
        return getROIMask().contains(x, y, z);
    }

    @Override
    public BooleanMask2D getBooleanMask2D(int z, boolean inclusive)
    {
        return getROIMask().getBooleanMask2D(z, inclusive);
    }

    @Override
    public BooleanMask3D getBooleanMask(boolean inclusive)
    {
        return getROIMask().getBooleanMask(inclusive);
    }

    @Override
    public double getNumberOfContourPoints()
    {
        return getROIMask().getNumberOfContourPoints();
    }

    @Override
    public double getNumberOfPoints()
    {
        return getROIMask().getNumberOfPoints();
    }
}
