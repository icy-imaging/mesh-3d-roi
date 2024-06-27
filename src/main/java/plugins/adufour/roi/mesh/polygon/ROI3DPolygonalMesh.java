/*
 * Copyright (c) 2010-2024. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.adufour.roi.mesh.polygon;

import org.bioimageanalysis.extension.kernel.roi.roi3d.ROI3DArea;
import org.bioimageanalysis.icy.common.geom.point.Point3D;
import org.bioimageanalysis.icy.gui.render.IcyVtkPanel;
import org.bioimageanalysis.icy.model.render.vtk.VtkUtil;
import org.bioimageanalysis.icy.model.roi.ROI;
import org.bioimageanalysis.icy.model.roi.ROI3D;
import org.bioimageanalysis.icy.model.roi.mask.BooleanMask3D;
import plugins.adufour.quickhull.QuickHull3D;
import plugins.adufour.roi.mesh.Cell3D;
import plugins.adufour.roi.mesh.ROI3DMesh;
import plugins.adufour.roi.mesh.Vertex3D;
import vtk.*;

import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * 3D <u>r</u>egion <u>o</u>f <u>i</u>nterest (ROI) defined as a polygonal mesh
 * (i.e., a closed set of 2D polygons in 3D space defining the contour of the
 * ROI).
 * <p>
 * <h2>Structure</h2> A polygonal mesh is defined by two data structures:
 * <ul>
 * <li>A list of vertices (also known as the <b>vertex buffer</b>), defining the
 * 3D coordinates of each point of the mesh surface. Vertices are stored here in
 * the form of {@link Vertex3D} objects, allowing developers to easily extend
 * the vertex structure as needed by overriding the
 * {@link #createVertex(Point3d)} method;</li>
 * <li>A list of polygons (also referred to as <i>faces</i> or <i>cells</i>)
 * linking vertices together, by storing a set of indices pointing to the vertex
 * buffer, in counter-clockwise order when looking at the front of the face
 * (this list is also commonly known as the <b>index buffer</b> in computer
 * graphics). Polygons are stored here in the form of {@link Polygon3D} objects,
 * allowing developers to easily extend the polygon structure as needed by
 * overriding the {@link #createCell(int...)} method</li>
 * </ul>
 * <h2>Visualization</h2> Polygonal meshes carry an integrated painter that will
 * automatically display them on almost any available sequence viewer. In a 3D
 * viewer, display is performed using the VTK library. In 2D viewers, the
 * current slice of the mesh is represented as a filled area.
 * <p>
 * <h2>Important note</h2> Contrary to 3D ROI backed by the pixel grid (such as
 * {@link ROI3DArea}), polygon mesh ROI are defined in metric space to optimize
 * the calculation of geometric measures (e.g. volume, surface area, normals,
 * curvature, distances, etc.). It is therefore essential that pixel size
 * information is indicated during or after creation, such that translation
 * between both coordinate spaces is made easy and transparent to the user (this
 * includes ROI-based operations and measures such as
 * {@link #contains(Point3D) contains()}, {@link #getBounds()},
 * {@link #getBooleanMask(boolean) getBooleanMask()}, etc.)
 * <p>
 * <p>
 *
 * @author Alexandre Dufour
 * @see <a href="http://en.wikipedia.org/wiki/Polygon_mesh">Polygon mesh
 * (Wikipedia)</a>
 */
public class ROI3DPolygonalMesh extends ROI3DMesh<Polygon3D> {
    public class PolygonalMeshPainter extends MeshPainter {
        @Override
        protected vtkMapper createVTKMapper() {
            return new vtkPolyDataMapper();
        }
    }

    // use vtkCells object instead.
    // private vtkPolyData vtkPolygons;

    // @SuppressWarnings("unused")
    // private double surfaceArea;
    // @SuppressWarnings("unused")
    // private double volume;
    // private boolean surfVolUpToDate = false;

    // private double surfaceAreaPx;
    // private double volumePx;
    // private boolean surfVolPxUpToDate = false;

    /**
     * <h1>DO NOT USE! This constructor is for XML loading purposes only</h1>
     */
    public ROI3DPolygonalMesh() {
        //
    }

    /**
     * Creates a 3D ROI from the specified {@link QuickHull3D} object
     *
     * @param qhull a convex envelope obtained using Icy's QuickHull library
     */
    public ROI3DPolygonalMesh(final QuickHull3D qhull) {
        super();

        loadFromQuickHull(qhull);
    }

    public ROI3DPolygonalMesh(final vtkPolyData vtk) {
        super();

        loadFromVTK(vtk);
    }

    @Override
    protected ROI.ROIPainter createPainter() {
        return new PolygonalMeshPainter();
    }

    @Override
    protected vtkPointSet createVTKMesh() {
        return new vtkPolyData();
    }

    protected vtkPolyData getVtkPolydata() {
        return (vtkPolyData) getVTKMesh();
    }

    @Override
    protected void updateVTKMesh(final vtkPointSet vtkPointSet, final Tuple3d pixelSize) {
        // we have vtkPolydata here
        final vtkPolyData vtkPolygons = (vtkPolyData) vtkPointSet;

        // build new points and new cells
        final vtkPoints newPoints = getVtkPoints(pixelSize);
        final vtkCellArray newCells = getVtkCells();

        final IcyVtkPanel vtkPanel = getMeshPainter().getVtkPanel();

        if (vtkPanel != null)
            vtkPanel.lock();
        try {
            synchronized (vtkInternalLock) {
                // lock vtkPolygons as well
                //synchronized (vtkPolygons) {
                final vtkPoints oldPoints = vtkPolygons.GetPoints();
                // set the point data
                vtkPolygons.SetPoints(newPoints);
                if (oldPoints != null)
                    oldPoints.Delete();

                final vtkCellArray oldCells = vtkPolygons.GetPolys();
                // interpret the raw index buffer as cells
                vtkPolygons.SetPolys(newCells);
                if (oldCells != null)
                    oldCells.Delete();

                // notify changes
                vtkPolygons.Modified();
                //}
            }
        }
        finally {
            if (vtkPanel != null)
                vtkPanel.unlock();
        }
    }

    /**
     * Adds a new face to this mesh based on the specified vertex indices. Note that these indices
     * are not checked for existence in the vertex buffer.
     */
    public void addCell(final int... vertexIndices) {
        addCell(createCell(vertexIndices));
    }

    /**
     * Adds a new face to this mesh. Note that the vertex indices of the
     * specified face are not checked for existence in the vertex buffer.
     * <p>
     * For optimal compatibility, it is recommended that the specified face is
     * obtained via the {@link #createCell(int...) createCompatibleCell} method
     *
     * @param face the new face to add
     */
    @Override
    public void addCell(final Polygon3D face) {
        super.addCell(face);

        synchronized (vertices) {
            // let vertices know about their new neighbors
            for (int i = 0; i < face.size; i++) {
                final int vi = face.vertexIndices[i];
                final int vj = face.vertexIndices[(i + 1) % face.size];

                vertices.get(vi).neighbors.add(vj);
                vertices.get(vj).neighbors.add(vi);
            }
        }
    }

    @Override
    protected Polygon3D createCell(final CellType type, final int... vertexIndices) {
        if (type != CellType.POLYGON)
            throw new IllegalArgumentException("A polygonal mesh can only store cells of type POLYGON");

        return createCell(vertexIndices);
    }

    /**
     * Creates a new cell for this mesh (without adding it), using the specified
     * vertex indices.
     * <p>
     * By default, this method will return a {@link Polygon3D} object, but it
     * can be overridden to provide a custom implementation.
     *
     * @param vertexIndices the indices of the vertices forming the mesh, in
     *                      counter-clockwise order
     * @return the newly created face
     */
    public Polygon3D createCell(final int... vertexIndices) {
        return new Polygon3D(vertexIndices);
    }

    /**
     * Returns the minimum distance between all contour points and the specified point.
     *
     * @param closestPoint an optional Point3d object that will be filled with the closest point
     * @param scale        scale vector (pixel size) if we want to compute distance using real metrics in which case input point should be in metric unit too.
     *                     Set it to <code>null</code> if not needed.
     */
    public double getMinDistance(final Point3d point, final Point3d closestPoint, final Tuple3d scale) {
        final Point3d p = new Point3d();
        double dist = Double.MAX_VALUE;

        for (final Vertex3D v : vertices) {
            if (v == null)
                continue;

            final double d = vecMul(v.position, scale, p).distanceSquared(point);
            if (d < dist) {
                dist = d;
                if (closestPoint != null)
                    closestPoint.set(p);
            }
        }

        if (dist > 0)
            dist = Math.sqrt(dist);

        return dist;
    }

    /**
     * Returns the maximum distance between all contour points and the specified point
     *
     * @param point         the point to get max distance from
     * @param farthestPoint an optional Point3d object that will be filled with the farthest point
     * @param scale         scale vector (pixel size) if we want to compute distance using real metrics in which case input point should be in metric unit too.
     *                      Set it to <code>null</code> if not needed.
     */
    public double getMaxDistance(final Point3d point, final Point3d farthestPoint, final Tuple3d scale) {
        final Point3d p = new Point3d();
        double dist = 0;

        for (final Vertex3D v : vertices) {
            if (v == null)
                continue;

            final double d = vecMul(v.position, scale, p).distanceSquared(point);
            if (d > dist) {
                dist = d;
                if (farthestPoint != null)
                    farthestPoint.set(p);
            }
        }

        if (dist > 0)
            dist = Math.sqrt(dist);

        return dist;
    }

    // @Override
    // public BooleanMask3D getBooleanMask(boolean inclusive)
    // {
    // if (!maskUpToDate && !maskUpdating)
    // {
    // rasterScan(null, null, null);
    // // TODO replace with rasterScanVTK()
    // }
    //
    // return mask.getBooleanMask(inclusive);
    // }

    @Override
    public ROI3DPolygonalMesh getCopy() {
        return (ROI3DPolygonalMesh) clone();
    }

    /**
     * Tests whether the given point is inside the contour, and if so returns
     * the penetration depth of this point. <br>
     *
     * @param point      a point to test
     * @param meshCenter the center of the mesh, if different from the mass center
     * @param scale      scale vector (pixel size) if we want to compute penetration
     *                   depth using real metrics, in which case input point and
     *                   meshCenter should be in metric unit too.
     *                   Set it to <code>null</code> if not needed.
     * @return <ul>
     * <li>if <code>p</code> is outside: <code>0</code>
     * <li>if <code>p</code> is inside: the distance from <code>p</code>
     * to the contour edge
     * </ul>
     */
    public double getPenetrationDepth(final Point3d point, final Point3d meshCenter, final Tuple3d scale) {
        final double epsilon = 1.0e-12;

        final Vector3d edge1 = new Vector3d();
        final Vector3d edge2 = new Vector3d();
        final Vector3d vp = new Vector3d();
        final Vector3d vt = new Vector3d();
        final Vector3d vq = new Vector3d();
        final Vector3d ray = new Vector3d();
        final Point3d v1 = new Point3d();
        final Point3d v2 = new Point3d();
        final Point3d v3 = new Point3d();

        // the input point belongs to another mesh
        // 1) trace a ray from that point outwards (away from my center)
        // 2) count the intersections with my boundary
        // 3) if the number is odd, the point is inside
        // 4) if the point is inside, measure the distance to the edge

        // create a ray from the mass center to the given point
        ray.sub(point, meshCenter);

        double det, u, v, distance;
        double penetration = Double.MAX_VALUE;
        int crossCount = 0;

        // intersect the ray with all mesh faces
        // NB: for faces with more than 3 vertices,
        // cut the face into a triangle fan
        // e.g. 5 points: (1,2,3); (1,3,4); (1,3,5)

        for (final Polygon3D face : cells) {
            // the first point never changes
            vecMul(vertices.get(face.vertexIndices[0]).position, scale, v1);

            for (int i = 1; i < face.size - 1; i++) {
                vecMul(vertices.get(face.vertexIndices[i]).position, scale, v2);
                vecMul(vertices.get(face.vertexIndices[i + 1]).position, scale, v3);

                edge1.sub(v3, v1);
                edge2.sub(v2, v1);

                vp.cross(ray, edge2);

                det = edge1.dot(vp);

                if (det < epsilon)
                    continue;

                vt.sub(point, v1);

                u = vt.dot(vp);

                if (u < 0 || u > det)
                    continue;

                vq.cross(vt, edge1);

                v = ray.dot(vq);

                if (v < 0 || u + v > det)
                    continue;

                distance = edge2.dot(vq) / det;

                if (distance < 0)
                    continue;

                if (penetration > distance)
                    penetration = distance;

                crossCount++;
            }
        }

        return crossCount % 2 == 1 ? penetration : 0;
    }

    /**
     * Tests whether the given point is inside the contour, and if so returns
     * the penetration depth of this point. <br>
     *
     * @param point a point to test
     * @param scale scale vector (pixel size) if we want to compute penetration
     *              depth using real metrics in which case input point should be
     *              in metric unit too.
     *              Set it to <code>null</code> if not needed.
     * @return <ul>
     * <li>if <code>p</code> is outside: <code>0</code>
     * <li>if <code>p</code> is inside: the distance from <code>p</code>
     * to the contour edge
     * </ul>
     */
    public double getPenetrationDepth(final Point3d point, final Tuple3d scale) {
        return getPenetrationDepth(point, getMassCenter(scale), scale);
    }

    /**
     * Tests whether the given point is inside the contour, and if so returns
     * the penetration depth of this point. <br>
     *
     * @param point a point to test
     * @return <ul>
     * <li>if <code>p</code> is outside: <code>0</code>
     * <li>if <code>p</code> is inside: the distance from <code>p</code>
     * to the contour edge
     * </ul>
     */
    public double getPenetrationDepth(final Point3d point) {
        return getPenetrationDepth(point, getMassCenter(null), null);
    }

    public void resample(final double distance) {
        final IcyVtkPanel vtkPanel = getMeshPainter().getVtkPanel();

        if (vtkPanel != null)
            vtkPanel.lock();
        try {
            synchronized (vtkInternalLock) {
                final vtkPolyDataPointSampler sampler = new vtkPolyDataPointSampler();

                // use VTK object (should be up to date)
                sampler.SetInputData(getVtkPolydata());
                sampler.SetDistance(distance);
                sampler.Update();

                final vtkPolyData resampledPolyData = sampler.GetOutput();

                final vtkDelaunay3D delaunay3d = new vtkDelaunay3D();
                delaunay3d.SetInputData(resampledPolyData);
                delaunay3d.Update();

                final vtkUnstructuredGrid gridData = delaunay3d.GetOutput();

                final vtkDataSetSurfaceFilter datasetSurfaceFilter = new vtkDataSetSurfaceFilter();
                datasetSurfaceFilter.SetInputData(gridData);
                datasetSurfaceFilter.Update();
                final vtkPolyData triangleGeoPolyData = datasetSurfaceFilter.GetOutput();

                // final vtkGeometryFilter geometryFilter = new vtkGeometryFilter();
                // geometryFilter.SetInputData(resampledPolyData);
                // geometryFilter.Update();
                //
                // final vtkPolyData geoPolyData = geometryFilter.GetOutput();
                //
                // // by default we want triangle type polygon
                // vtkTriangleFilter triangleFilter = new vtkTriangleFilter();
                // triangleFilter.SetInputData(geoPolyData);
                // triangleFilter.Update();
                //
                // final vtkPolyData triangleGeoPolyData = triangleFilter.GetOutput();

                // rebuild from given polydata object
                loadFromVTK(triangleGeoPolyData);

                triangleGeoPolyData.Delete();
                // triangleFilter.Delete();
                // geoPolyData.Delete();
                // geometryFilter.Delete();
                resampledPolyData.Delete();
                sampler.Delete();
            }
        }
        finally {
            if (vtkPanel != null)
                vtkPanel.unlock();
        }
    }

    // @Override
    // public double computeNumberOfPoints()
    // {
    // updateSurfaceAreaAndVolume(false);
    // return volumePx;
    // }
    //
    // @Override
    // public double computeNumberOfContourPoints()
    // {
    // updateSurfaceAreaAndVolume(false);
    // return surfaceAreaPx;
    // }

    // private void updateSurfaceAreaAndVolume(boolean realUnits)
    // {
    // if (realUnits && surfVolUpToDate)
    // return;
    // if (!realUnits && surfVolPxUpToDate)
    // return;
    //
    // Vector3d v12 = new Vector3d();
    // Vector3d v13 = new Vector3d();
    // Vector3d cross = new Vector3d();
    //
    // Vector3d v1 = new Vector3d(), v2 = new Vector3d(), v3 = new Vector3d();
    //
    // synchronized (cells)
    // {
    // double tmpSurfaceArea = 0;
    // double tmpVolume = 0;
    // for (Polygon3D face : cells)
    // {
    // // if the face has more than 3 vertices,
    // // split it into a triangle fan
    //
    // // the first vertex of the fan never changes
    // // => has to be a vector for the final cross product
    // v1.set(vertices.get(face.vertexIndices[0]).position);
    //
    // if (!realUnits)
    // {
    // v1.x /= pixelSize.x;
    // v1.y /= pixelSize.y;
    // v1.z /= pixelSize.z;
    // }
    //
    // for (int i = 1; i < face.size - 1; i++)
    // {
    // v2.set(vertices.get(face.vertexIndices[i]).position);
    // v3.set(vertices.get(face.vertexIndices[i + 1]).position);
    //
    // if (!realUnits)
    // {
    // v2.x /= pixelSize.x;
    // v2.y /= pixelSize.y;
    // v2.z /= pixelSize.z;
    // v3.x /= pixelSize.x;
    // v3.y /= pixelSize.y;
    // v3.z /= pixelSize.z;
    // }
    //
    // v12.sub(v2, v1);
    // v13.sub(v3, v1);
    //
    // cross.cross(v12, v13);
    //
    // double surf = cross.length() * 0.5f;
    //
    // tmpSurfaceArea += surf;
    //
    // cross.normalize();
    //
    // // from the divergence theorem:
    // // V = sum_i( surface_of_i * normal_of_i 'dot' any_point_in_i ) / 3
    //
    // tmpVolume += surf * cross.dot(v1) / 3.0;
    // }
    // }
    // if (realUnits)
    // {
    // this.volume = tmpVolume;
    // this.surfaceArea = tmpSurfaceArea;
    // surfVolUpToDate = true;
    // }
    // else
    // {
    // this.volumePx = tmpVolume;
    // this.surfaceAreaPx = tmpSurfaceArea;
    // surfVolPxUpToDate = true;
    // }
    // }
    //
    // }

    // /**
    // * Test method to see whether VTK could be faster and/or more efficient at building a binary
    // * mask from the mesh
    // *
    // * @param imageData
    // */
    // @SuppressWarnings("unused")
    // private void rasterScanVTK()
    // {
    // maskUpdating = true;
    //
    // Rectangle3D.Integer b3 = getBounds();
    //
    // byte[] data = new byte[b3.sizeX * b3.sizeY * b3.sizeZ];
    // vtkImageData imageVolume = VtkUtil.getImageData(data, DataType.UBYTE, b3.sizeX, b3.sizeY, b3.sizeZ, 1);
    // imageVolume.SetSpacing(new double[] {pixelSize.x, pixelSize.y, pixelSize.z});
    //
    // vtkPolyDataToImageStencil pd2s = new vtkPolyDataToImageStencil();
    // pd2s.SetInputData(vtkPolygons);
    // pd2s.SetOutputSpacing(imageVolume.GetSpacing());
    // pd2s.SetOutputWholeExtent(imageVolume.GetExtent());
    //
    // vtkImageStencilToImage s2i = new vtkImageStencilToImage();
    // s2i.SetInsideValue(255.0);
    // s2i.SetOutsideValue(0.0);
    // s2i.SetInputConnection(pd2s.GetOutputPort());
    // s2i.Update();
    //
    // vtkUnsignedCharArray array = (vtkUnsignedCharArray) s2i.GetOutput().GetPointData().GetScalars();
    // byte[] raster = array.GetJavaArray();
    //
    // int offset = 0;
    // for (int z = 0; z < b3.sizeZ; z++)
    // {
    // boolean[] _mask = new boolean[b3.sizeX * b3.sizeY];
    //
    // for (int i = 0; i < _mask.length; i++)
    // _mask[i] = raster[offset++] != 0;
    //
    // ROI2DArea maskSlice = new ROI2DArea(new BooleanMask2D(new Rectangle(0, 0, b3.sizeX, b3.sizeY), _mask));
    // mask.setSlice(z, maskSlice);
    // }
    //
    // maskUpToDate = true;
    // maskUpdating = false;
    // }
    //
    // /**
    // * Perform a raster scan on the mesh, and calculate various information based on the specified
    // * parameters. The raster scan algorithm is based on an efficient line-scan algorithm developed
    // * in: <i>Dufour et al., 3D active meshes: fast discrete deformable models for cell tracking in
    // * 3D time-lapse microscopy. IEEE Transactions on Image Processing 20, 2011</i>
    // *
    // * @param imageData
    // * (optional, set to <code>null</code> if not needed) a sequence that will be used to
    // * compute the average intensity inside the mesh (note that the T and C of this ROI
    // * have to be different than <code>-1</code> if the sequence has more than 1 time
    // * point and 1 channel)
    // * @param out_averageIntensity
    // * (optional, only used if <code>imageData</code> is provided) a variable that will
    // * be hold the average image intensity inside the mesh after the scan is complete
    // * @param out_imageMask
    // * (optional, set to <code>null</code> if not needed) a boolean mask of same
    // * dimensions as the image data that will be filled with the mesh interior
    // * @throws MeshTopologyException
    // */
    // public void rasterScan(final Sequence imageData, VarDouble out_averageIntensity, final BooleanMask3D out_imageMask)
    // {
    // // don't update just the mask if not necessary
    // if (maskUpToDate && imageData == null && out_imageMask == null)
    // return;
    //
    // maskUpdating = true;
    //
    // final int currentT, currentC;
    // if (imageData != null)
    // {
    // int sizeT = imageData.getSizeT();
    // if (sizeT > 1)
    // {
    // currentT = getT();
    // if (currentT == -1 || currentT >= sizeT)
    // throw new IllegalArgumentException("Invalid T: " + currentT);
    // }
    // else
    // currentT = 0;
    //
    // int sizeC = imageData.getSizeC();
    // if (sizeC > 1)
    // {
    // currentC = getC();
    // if (currentC == -1 || currentC >= sizeC)
    // throw new IllegalArgumentException("Invalid C: " + currentC);
    // }
    // else
    // currentC = 0;
    // }
    // else
    // currentT = currentC = 0;
    //
    // // direction of scan: along X
    // final Vector3d direction = new Vector3d(1, 0, 0);
    //
    // final int globalStride = (out_imageMask != null ? out_imageMask.bounds.sizeX
    // : (imageData != null ? imageData.getSizeX() : 0));
    //
    // // bounds are in voxel space
    // Rectangle3D bounds = getBounds3D();
    //
    // final int minX = (int) Math.floor(bounds.getMinX()) - 1;
    // final int minY = (int) Math.floor(bounds.getMinY()) - 1;
    // final int minZ = (int) Math.floor(bounds.getMinZ()) - 1;
    //
    // final int maxX = (int) Math.ceil(bounds.getMaxX()) + 1;
    // final int maxY = (int) Math.ceil(bounds.getMaxY()) + 1;
    // final int maxZ = (int) Math.ceil(bounds.getMaxZ()) + 1;
    //
    // final int localMaskWidth = maxX - minX + 1;
    // final int localMaskHeight = maxY - minY + 1;
    // final int localMaskDepth = maxZ - minZ + 1;
    //
    // // epsilon used for the line-triangle intersection test
    // final double epsilon = 1.0e-13;
    //
    // ArrayList<Future<?>> sliceTasks;
    //
    // try
    // {
    // sliceTasks = new ArrayList<Future<?>>(localMaskDepth);
    // }
    // catch (IllegalArgumentException e)
    // {
    // // something went wrong when updating the bounds. Ignore for now
    // return;
    // }
    //
    // final double[] inSums = (imageData != null ? new double[imageData.getSizeZ()] : null);
    // final double[] inCpts = (imageData != null ? new double[imageData.getSizeZ()] : null);
    //
    // final List<Polygon3D> myCells = getCells();
    // final List<Vertex3D> myVertices = getVertices();
    //
    // for (int k = minZ; k <= maxZ; k++)
    // {
    // final int currentSlice = k;
    //
    // class Slicer implements Runnable
    // {
    // @Override
    // public void run()
    // {
    // // should we update the global mask?
    // boolean[] imageMaskSlice = (out_imageMask != null && out_imageMask.mask.containsKey(currentSlice)
    // ? out_imageMask.mask.get(currentSlice).mask
    // : null);
    //
    // // should we update the local mask?
    // boolean[] localSlice = new boolean[localMaskWidth * localMaskHeight];
    // Rectangle localSliceBounds = new Rectangle(minX, minY, localMaskWidth, localMaskHeight);
    //
    // // should we compute the average intensity?
    // Object imageDataSlice = (imageData != null ? imageData.getDataXY(currentT, currentSlice, currentC)
    // : null);
    //
    // Point3d origin = new Point3d(minX * pixelSize.x, minY * pixelSize.y, currentSlice * pixelSize.z);
    //
    // Vector3d edge1 = new Vector3d();
    // Vector3d edge2 = new Vector3d();
    // Vector3d vp = new Vector3d();
    // Vector3d vt = new Vector3d();
    // Vector3d vq = new Vector3d();
    //
    // ArrayList<Integer> intersections = new ArrayList<Integer>(4);
    //
    // double localInSum = 0;
    // int localInCpt = 0;
    //
    // // core intersection code:
    // // for each slice, scan each line (along X), then step along Y
    // for (int j = minY; j < maxY; j++, origin.y += pixelSize.y)
    // {
    // intersections.clear();
    // int nbCrosses = 0;
    //
    // for (Polygon3D face : myCells)
    // {
    // // if a face has more than 3 vertices,
    // // split it into a triangle fan
    //
    // Point3d v1 = myVertices.get(face.vertexIndices[0]).position;
    //
    // for (int i = 1; i < face.size - 1; i++)
    // {
    // Point3d v2 = myVertices.get(face.vertexIndices[i]).position;
    // Point3d v3 = myVertices.get(face.vertexIndices[i + 1]).position;
    //
    // // raw intersection test with the face bounds
    //
    // if (origin.y < v1.y && origin.y < v2.y && origin.y < v3.y)
    // continue;
    // if (origin.z < v1.z && origin.z < v2.z && origin.z < v3.z)
    // continue;
    // if (origin.y > v1.y && origin.y > v2.y && origin.y > v3.y)
    // continue;
    // if (origin.z > v1.z && origin.z > v2.z && origin.z > v3.z)
    // continue;
    //
    // // more precise ray-triangle intersection test
    //
    // edge1.sub(v2, v1);
    // edge2.sub(v3, v1);
    //
    // vp.cross(direction, edge2);
    //
    // double det = edge1.dot(vp);
    //
    // if (Math.abs(det) < epsilon)
    // continue;
    //
    // double inv_det = 1.0 / det;
    //
    // vt.sub(origin, v1);
    // double u = vt.dot(vp) * inv_det;
    // if (u < 0 || u > 1.0)
    // continue;
    //
    // vq.cross(vt, edge1);
    // double v = direction.dot(vq) * inv_det;
    // if (v < 0.0 || u + v > 1.0)
    // continue;
    //
    // // intersection found, compute the distance
    //
    // double distance = edge2.dot(vq) * inv_det;
    // Integer distanceInVoxels = (int) Math.round(distance / pixelSize.x);
    //
    // if (distanceInVoxels < 0)
    // continue;
    //
    // if (!intersections.contains(distanceInVoxels))
    // {
    // intersections.add(distanceInVoxels);
    // nbCrosses++;
    // }
    // }
    // }
    //
    // // if nbCrosses = 0, the ray never crosses the mesh
    // // if nbCrosses = 1, the ray is tangent to the mesh
    // if (nbCrosses < 2)
    // continue;
    //
    // Collections.sort(intersections);
    //
    // if (nbCrosses == 3)
    // {
    // // Due to numerical errors, an intersection may be detected
    // // twice
    // // => remove one of them (=> the middle one to be sure)
    // intersections.remove(1);
    // nbCrosses--;
    // }
    // else if (nbCrosses % 2 == 1)
    // {
    // // more numerical errors, just discard the line for this once
    // continue;
    // }
    //
    // // what shall we now do with this intersection data??
    //
    // int globalLineStart = j * globalStride + minX;
    // int localLineStart = (j - minY) * localMaskWidth;
    //
    // for (int cross = 0; cross < nbCrosses; cross += 2)
    // {
    // // the ray enters at "cross" and leaves at "cross + 1"
    // int in = intersections.get(cross);
    // int out = intersections.get(cross + 1);
    //
    // // fill the line in the current slice of this ROI's boolean mask
    // Arrays.fill(localSlice, localLineStart + in, localLineStart + out, true);
    //
    // // check if we need to update anything
    // if (imageDataSlice == null && imageMaskSlice == null)
    // continue;
    //
    // // update the (global) image mask (if we are inside it)
    // if (imageMaskSlice != null && out_imageMask.bounds.contains(in, j, currentSlice))
    // {
    // Arrays.fill(imageMaskSlice, globalLineStart + in, globalLineStart + out, true);
    // }
    //
    // // accumulate the image intensity (if we are inside it)
    // if (imageDataSlice != null)
    // {
    // DataType dataType = imageData.getDataType_();
    // localInCpt += (out - in);
    // for (int offset = globalLineStart + in; offset < globalLineStart + out; offset++)
    // localInSum += Array1DUtil.getValue(imageDataSlice, offset, dataType);
    // }
    // }
    // }
    //
    // // gather the slice data
    // synchronized (mask)
    // {
    // mask.setSlice(currentSlice, new ROI2DArea(new BooleanMask2D(localSliceBounds, localSlice)));
    // }
    //
    // // gather the intensity data
    // if (imageData != null && currentSlice >= 0 && currentSlice < imageData.getSizeZ())
    // {
    // inSums[currentSlice] = localInSum;
    // inCpts[currentSlice] = localInCpt;
    // }
    // }
    // }
    //
    // sliceTasks.add(processor.submit(new Slicer()));
    // }
    //
    // try
    // {
    // for (Future<?> sliceTask : sliceTasks)
    // sliceTask.get();
    // }
    // catch (InterruptedException e)
    // {
    // // preserve the interrupted state
    // Thread.currentThread().interrupt();
    // processor.purge();
    // processor.removeAllWaitingTasks();
    // }
    // catch (ExecutionException e)
    // {
    // // most likely an error due to lack of thread synchronization
    // }
    //
    // // the local mask is up to date
    // maskUpToDate = true;
    // maskUpdating = false;
    //
    // if (imageData != null)
    // {
    // double inSum = ArrayMath.sum(inSums);
    // double inCpt = ArrayMath.sum(inCpts);
    //
    // out_averageIntensity.setValue(inCpt == 0 ? 0 : inSum / inCpt);
    // }
    //
    // }

    /**
     * Creates a 3D ROI from the specified {@link QuickHull3D} object
     *
     * @param qhull a convex envelope obtained using Icy's QuickHull library
     */
    public void loadFromQuickHull(final QuickHull3D qhull) {
        synchronized (vertices) {
            vertices.clear();
        }

        for (final Point3d vertex : qhull.getVertices())
            addVertex(createVertex(vertex), false, false);

        synchronized (cells) {
            cells.clear();
        }

        for (final int[] face : qhull.getFaces())
            addCell(face);

        roiChanged(true);
    }

    @Override
    public void loadFromVTK(final File vtkFile, final boolean useLegacyReader) {
        final vtkPolyData polyData;

        if (useLegacyReader) {
            final vtkPolyDataReader legacyReader = new vtkPolyDataReader();
            legacyReader.SetFileName(vtkFile.getPath());
            legacyReader.Update();
            polyData = legacyReader.GetOutput();
            legacyReader.Delete();
        }
        else {
            final vtkXMLPolyDataReader reader = new vtkXMLPolyDataReader();
            reader.SetFileName(vtkFile.getPath());
            reader.Update();
            polyData = reader.GetOutput();
            reader.Delete();
        }

        loadFromVTK(polyData);
    }

    @Override
    protected void addVtkCell(final vtkCell cell) {
        final vtkIdList ids = cell.GetPointIds();
        final int nId = (int) ids.GetNumberOfIds();
        final int[] vertexIndices = new int[nId];

        for (int id = 0; id < nId; id++)
            vertexIndices[id] = (int) ids.GetId(id);

        addCell(createCell(vertexIndices));
    }

    // protected boolean loadFromVTK(vtkPolyData polyData)
    // {
    // // load points (generically)
    // setVertexData(polyData.GetPoints());
    //
    // // load faces
    // final vtkCellArray faces = polyData.GetPolys();
    // final int nFaces = faces.GetNumberOfCells();
    // final int[] indexBuffer = VtkUtil.getArray(faces.GetData());
    //
    // for (int f = 0, idx = 0; f < nFaces; f++)
    // {
    // int nPointsPerFace = indexBuffer[idx++];
    // int[] faceIndices = new int[nPointsPerFace];
    // System.arraycopy(indexBuffer, idx, faceIndices, 0, nPointsPerFace);
    // addCell(faceIndices);
    // idx += nPointsPerFace;
    // }
    //
    // roiChanged(true);
    //
    // return true;
    // }

    // @Override
    // public void roiChanged(boolean contentChanged)
    // {
    // if (contentChanged)
    // {
    // surfVolUpToDate = false;
    // surfVolPxUpToDate = false;
    // }
    //
    // super.roiChanged(contentChanged);
    // }

    /**
     * Saves this mesh as a OFF file
     * (<a href= "https://en.wikipedia.org/wiki/OFF_(file_format)" >Object File
     * Format</a>)
     *
     * @param filePath the file to write to
     */
    public void saveToOFF(final String filePath) throws FileNotFoundException {
        optimizeVertexBuffer();

        final PrintStream ps = new PrintStream(filePath);

        // OFF File format is written as follows:

        // line 1: "OFF"
        ps.println("OFF");

        // line 2: numVertices numFaces numEdges (numEdges is not important)
        ps.println(vertices.size() + " " + cells.size() + " 0");

        // one line per vertex "x y z"
        for (final Vertex3D vertex : vertices)
            ps.println(vertex.position.x + " " + vertex.position.y + " " + vertex.position.z);

        // one line per face "numVertices v1 v2 ... vn"
        for (final Cell3D cell : cells) {
            final StringBuilder line = new StringBuilder(String.valueOf(cell.size));
            for (final int vertexIndex : cell.vertexIndices)
                line.append(" ").append(vertexIndex);
            ps.println(line);
        }

        ps.close();
    }

    public void updateNormals() {
        final Vector3d v31 = new Vector3d();
        final Vector3d v12 = new Vector3d();
        final Vector3d v23 = new Vector3d();

        for (final Polygon3D f : cells) {
            // Accumulate face normals in each vertex
            // All face vertices are in the same plane
            // => use any 3 consecutive vertices
            // if (f.size > 3)
            // {
            // System.out.println("oups !");
            // }

            final Vertex3D v1 = vertices.get(f.vertexIndices[0]);
            final Vertex3D v2 = vertices.get(f.vertexIndices[1]);
            final Vertex3D v3 = vertices.get(f.vertexIndices[2]);

            v31.sub(v1.position, v3.position);
            v12.sub(v2.position, v1.position);
            v23.sub(v3.position, v2.position);

            // normal at v1 = [v1 v2] ^ [v1 v3] = [v3 v1] ^ [v1 v2]
            v1.normal.x += v31.y * v12.z - v31.z * v12.y;
            v1.normal.y += v31.z * v12.x - v31.x * v12.z;
            v1.normal.z += v31.x * v12.y - v31.y * v12.x;

            // normal at v2 = [v2 v3] ^ [v2 v1] = [v1 v2] ^ [v2 v3]
            v2.normal.x += v12.y * v23.z - v12.z * v23.y;
            v2.normal.y += v12.z * v23.x - v12.x * v23.z;
            v2.normal.z += v12.x * v23.y - v12.y * v23.x;

            // normal at v3 = [v3 v1] ^ [v3 v2] = [v2 v3] ^ [v3 v1]
            v3.normal.x += v23.y * v31.z - v23.z * v31.y;
            v3.normal.y += v23.z * v31.x - v23.x * v31.z;
            v3.normal.z += v23.x * v31.y - v23.y * v31.x;

            // if (Double.isNaN(v1.normal.x) || Double.isNaN(v1.normal.y) || Double.isNaN(v1.normal.z))
            // {
            // System.out.println("oups !");
            // }
            // if (Double.isNaN(v2.normal.x) || Double.isNaN(v2.normal.y) || Double.isNaN(v2.normal.z))
            // {
            // System.out.println("oups !");
            // }
            // if (Double.isNaN(v3.normal.x) || Double.isNaN(v3.normal.y) || Double.isNaN(v3.normal.z))
            // {
            // System.out.println("oups !");
            // }
        }

        // Normalize the accumulated normals
        for (final Vertex3D v : vertices) {
            if (v != null) {
                // important to test length as trying to normalize 0 length normal result in NaN !
                if (v.normal.lengthSquared() > 0d)
                    v.normal.normalize();

                // if (Double.isNaN(v.normal.x) || Double.isNaN(v.normal.y) || Double.isNaN(v.normal.z))
                // {
                // System.out.println("oups !");
                // }
            }
        }
    }

    // ROI-related methods:
    //
    // All methods below override methods from package icy.roi,
    // which assume that all coordinates are given in image space.
    // Since 3D meshes are handled in metric space, conversion
    // between both space must not be forgotten!

    /**
     * Tests whether the point at the given coordinates is inside this mesh. This method is
     * overridden from class {@link ROI3D}, therefore the coordinates of the point to test are
     * assumed to be in voxel (image) space (they will be locally converted to real space before the
     * test is carried out).
     */
    @Override
    public boolean contains(final double x, final double y, final double z) {
        // TODO: is it really used in real space anywhere as ROI logic imply image space ?? (Stef)

        // Point3d point = new Point3d(x, y, z);
        //
        // // Convert from image to metric space
        // point.x *= pixelSize.x;
        // point.y *= pixelSize.y;
        // point.z *= pixelSize.z;
        //
        // return getDistanceToMesh(point) > 0;

        return getPenetrationDepth(new Point3d(x, y, z)) > 0;
    }

    @Override
    public BooleanMask3D buildMask() {
        final vtkPolyData polyData = getVtkPolydata();
        final vtkImageData binaryImage;

        // prevent modification of polyData during processing
        synchronized (polyData) {
            try {
                // get mask from polydata (may fail if the polyData is too large)
                binaryImage = VtkUtil.getBinaryImageData(polyData, null);
            }
            catch (final Exception e) {
                // return empty mask 3D
                return new BooleanMask3D();
            }
        }

        final BooleanMask3D result = VtkUtil.getBooleanMaskFromBinaryImage(binaryImage, false);

        // cleanup
        binaryImage.Delete();

        return result;
    }
}
