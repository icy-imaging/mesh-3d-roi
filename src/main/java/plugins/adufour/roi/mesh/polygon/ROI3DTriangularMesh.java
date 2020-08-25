package plugins.adufour.roi.mesh.polygon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.vecmath.Point3d;

import icy.roi.ROI3D;
import icy.type.collection.array.Array1DUtil;
import icy.type.rectangle.Rectangle3D;
import plugins.adufour.quickhull.QuickHull3D;
import plugins.adufour.roi.mesh.MeshTopologyException;
import plugins.adufour.roi.mesh.Vertex3D;

/**
 * Special implementation of {@link ROI3DPolygonalMesh} where the surface is exclusively formed of
 * triangular faces, providing additional methods to maintain a constant surface sampling, as
 * described in the following publication: <i>Dufour et al., 3D active meshes: fast discrete
 * deformable models for cell tracking in 3D time-lapse microscopy. IEEE Transactions on Image
 * Processing 20, 2011</i> <br/>
 * <br/>
 * Example of a polygonal mesh formed exclusively of triangles (quoted from <a
 * href="http://en.wikipedia.org/wiki/Polygon_mesh">Wikipedia</a>):<br/>
 * <br/>
 * <img width="250"
 * src="http://upload.wikimedia.org/wikipedia/commons/f/fb/Dolphin_triangle_mesh.png"/> <br/>
 * 
 * @see <a href="http://en.wikipedia.org/wiki/Polygon_mesh">Polygon mesh (Wikipedia)</a>
 * @author Alexandre Dufour
 */
public class ROI3DTriangularMesh extends ROI3DPolygonalMesh
{
    /**
     * <h1>DO NOT USE! This constructor is for XML loading purposes only</h1>
     */
    public ROI3DTriangularMesh()
    {
        //
    }

    /**
     * Creates a regularly-sampled triangular mesh from the specified ROI.
     * 
     * @param sampling
     *        the mesh sampling (i.e. the average distance between any two neighbor vertices of
     *        the mesh). It is usually recommended that this sampling is set to approximately
     *        20% of the roi's shortest diameter
     * @param roi
     *        the region of interest around which the mesh should be created
     */
    public ROI3DTriangularMesh(double sampling, ROI3D roi)
    {
        if (roi instanceof ROI3DTriangularMesh)
        {
            final ROI3DTriangularMesh roiTriangularMesh = (ROI3DTriangularMesh) roi;

            setVertexData(roiTriangularMesh.vertices, true);
            setCellData(roiTriangularMesh.cells, true);
        }
        else
        {
            try
            {
                // convex hull
                final int[] pts = roi.getBooleanMask(true).getContourPointsAsIntArray();
                final double[] pts_dbl = Array1DUtil.intArrayToDoubleArray(pts, true);

                final QuickHull3D qh3 = new QuickHull3D(pts_dbl);

                // we want triangle here
                qh3.triangulate();

                loadFromQuickHull(qh3);
            }
            catch (Exception e)
            {
                vertices.clear();
                cells.clear();
                System.err
                        .println("Unable to triangulate ROI " + roi.getName() + ", will use the bounding box instead");
                // initialize a sphere "stretched" to the bounding box of the ROI
                setIcosahedron(roi.getBounds3D(), sampling);
            }

            try
            {
                // finish with a proper sampling
                resample(sampling, 0.4);
            }
            catch (MeshTopologyException e)
            {
                // just ignore here (resampling failed, not a big issue)
                System.err.println("Warning: couldn't resample initial contour:");
                System.err.println(e.getMessage());

                // if (e.children == null)
                // throw new MeshTopologyException(this, null);
                //
                // Mesh3D[] children = new Mesh3D[e.children.length];
                //
                // for (int i = 0; i < children.length; i++)
                // children[i] = new Mesh3D(sampling, pixelSize, e.children[i], convergence);
                //
                // throw new TopologyException(this, children);
            }
        }

        roiChanged(true);
    }

    private void setIcosahedron(Rectangle3D bounds, double sampling)
    {
        // start from a regular icosahedron using the golden number PHI
        // distance between any vertex pair: 2
        // radius of the bounding sphere: PHI
        // center of the icosahedron: origin

        double PHI = (1.0 + Math.sqrt(5.0)) / 2.0;

        // Declaration of the vertices
        int zA = addVertex(createVertex(new Point3d(PHI, 1, 0)), false, false);
        int zB = addVertex(createVertex(new Point3d(-PHI, 1, 0)), false, false);
        int zC = addVertex(createVertex(new Point3d(-PHI, -1, 0)), false, false);
        int zD = addVertex(createVertex(new Point3d(PHI, -1, 0)), false, false);
        int yA = addVertex(createVertex(new Point3d(1, 0, PHI)), false, false);
        int yB = addVertex(createVertex(new Point3d(1, 0, -PHI)), false, false);
        int yC = addVertex(createVertex(new Point3d(-1, 0, -PHI)), false, false);
        int yD = addVertex(createVertex(new Point3d(-1, 0, PHI)), false, false);
        int xA = addVertex(createVertex(new Point3d(0, PHI, 1)), false, false);
        int xB = addVertex(createVertex(new Point3d(0, -PHI, 1)), false, false);
        int xC = addVertex(createVertex(new Point3d(0, -PHI, -1)), false, false);
        int xD = addVertex(createVertex(new Point3d(0, PHI, -1)), false, false);

        // Declaration of the faces
        addCell(yA, xA, yD);
        addCell(yA, yD, xB);
        addCell(yB, yC, xD);
        addCell(yB, xC, yC);

        addCell(zA, yA, zD);
        addCell(zA, zD, yB);
        addCell(zC, yD, zB);
        addCell(zC, zB, yC);

        addCell(xA, zA, xD);
        addCell(xA, xD, zB);
        addCell(xB, xC, zD);
        addCell(xB, zC, xC);

        addCell(xA, yA, zA);
        addCell(xD, zA, yB);
        addCell(yA, xB, zD);
        addCell(yB, zD, xC);
        addCell(yD, xA, zB);
        addCell(yC, zB, xD);
        addCell(yD, zC, xB);
        addCell(yC, xC, zC);

        // r3 is always be given in voxel space
        // => take the pixel size into account

        // double minX = bounds.getMinX() * pixelSize.x;
        // double minY = bounds.getMinY() * pixelSize.y;
        // double minZ = bounds.getMinZ() * pixelSize.z;
        // double halfSizeX = (bounds.getSizeX() - 1) * pixelSize.x * 0.5;
        // double halfSizeY = (bounds.getSizeY() - 1) * pixelSize.y * 0.5;
        // double halfSizeZ = (bounds.getSizeZ() - 1) * pixelSize.z * 0.5;
        double minX = bounds.getMinX();
        double minY = bounds.getMinY();
        double minZ = bounds.getMinZ();
        double halfSizeX = (bounds.getSizeX() - 1) * 0.5;
        double halfSizeY = (bounds.getSizeY() - 1) * 0.5;
        double halfSizeZ = (bounds.getSizeZ() - 1) * 0.5;

        Point3d center = new Point3d(minX + halfSizeX, minY + halfSizeY, minZ + halfSizeZ);

        for (Vertex3D v : vertices)
        {
            // scale to the final size
            v.position.x *= halfSizeX / PHI;
            v.position.y *= halfSizeY / PHI;
            v.position.z *= halfSizeZ / PHI;

            // if (Double.isNaN(v.position.x) || Double.isNaN(v.position.y) || Double.isNaN(v.position.z))
            // {
            // System.out.println("oups !");
            // }

            // translate to the final location
            v.position.add(center);
        }

        // the resolution is now stretched...
        double minResolution = 4 * Math.min(halfSizeX, Math.min(halfSizeY, halfSizeZ)) / PHI;

        while (minResolution > (2 * sampling))
        {
            // do global subdivisions (faster)
            subdivide();
            minResolution /= 2;
        }
    }

    @Override
    public Polygon3D createCell(int... vertexIndices)
    {
        if (vertexIndices.length != 3)
            throw new IllegalArgumentException("A triangle must be defined by exactly 3 vertex indices");

        return super.createCell(vertexIndices);
    }

    /**
     * Deletes a tetrahedron from the mesh, and fill the hole with a new face
     * 
     * @param topVertex
     *        the vertex at the top of the tetrahedron
     * @param v1
     *        one of the three vertices at the base of the tetrahedron
     * @param v2
     *        another of the vertices at the base of the tetrahedron
     */
    private void deleteTetrahedron(int topVertex, int v1, int v2)
    {
        // find the third vertex at the base of the tetrahedron
        int v3 = -1;
        for (int n : vertices.get(topVertex).neighbors)
        {
            if ((n != v1) && (n != v2))
                v3 = n;

            // take the opportunity to update the neighborhood
            vertices.get(n).neighbors.remove(topVertex);
        }

        for (int i = 0; i < cells.size(); i++)
        {
            if (cells.get(i).contains(topVertex))
                cells.remove(i--);
        }

        // fill the hole
        addCell(v1, v2, v3);

        // delete the top vertex for good
        deleteVertex(topVertex);
    }

    private static void extractVertices(Integer seed, Set<Vertex3D> visitedVertices, List<Vertex3D> oldPoints,
            List<Vertex3D> newPoints)
    {
        final Stack<Integer> seeds = new Stack<Integer>();

        seeds.add(seed);

        while (!seeds.isEmpty())
        {
            final int currentVertexIndex = seeds.pop().intValue();
            final Vertex3D currentVertex = oldPoints.get(currentVertexIndex);

            // don't process a visited vertex
            if (visitedVertices.contains(currentVertex))
                continue;

            // mark the vertex as visited
            visitedVertices.add(currentVertex);
            // extract the vertex into the new list (at the same position!!)
            newPoints.set(currentVertexIndex, currentVertex);

            // add the neighbors to the list of seeds
            for (int n : currentVertex.neighbors)
                seeds.push(Integer.valueOf(n));
        }
    }

    private static void extractFaces(List<Vertex3D> pointsList, List<Polygon3D> oldFaces, List<Polygon3D> newFaces)
    {
        for (Polygon3D face : oldFaces)
        {
            for (int i : face.vertexIndices)
            {
                if (pointsList.get(i) != null)
                {
                    newFaces.add(face);
                    break;
                }
            }
        }
    }

    /**
     * Splits the current contour using the 'cutting' face defined by the given vertices. <br>
     * 
     * <pre>
     * How this works:
     *  - separate all vertices on each side of the cutting face (without considering the vertices of the cutting face), 
     *  - separate all faces touching at least one vertex of each group (will include faces touching the cutting face),
     *  - create a contour with each group of vertices and faces,
     *  - add the cutting face and its vertices to each created contour
     * </pre>
     * 
     * @param v1
     * @param v2
     * @param v3
     * @throws MeshTopologyException
     */
    private void splitContourAtVertices(int v1, int v2, int v3) throws MeshTopologyException
    {
        List<Polygon3D> resultFaces = null;
        List<Vertex3D> resultVertices = null;

        // need synch access
        synchronized (vertices)
        {
            synchronized (cells)
            {
                final int numVertices = vertices.size();
                final Set<Vertex3D> visitedVertices = new HashSet<Vertex3D>(numVertices);

                // mark the vertices from the cutting triangle as visited
                // => that should separate the mesh into 2 (open) meshes
                visitedVertices.add(vertices.get(v1));
                visitedVertices.add(vertices.get(v2));
                visitedVertices.add(vertices.get(v3));

                // the mesh split into two components, extract them via connected component analysis
                for (int child = 0; child < 2; child++)
                {
                    // use the first non-visited vertex as seed
                    Integer seed = null;

                    for (int i = 0; i < numVertices; i++)
                    {
                        final Vertex3D v = vertices.get(i);

                        if ((v != null) && !visitedVertices.contains(v))
                        {
                            seed = Integer.valueOf(i);
                            break;
                        }
                    }

                    if (seed == null)
                    {
                        // no seed for first child (should not happen)
                        if (child == 0)
                        {
                            System.err.println("While splitting mesh at vertices (" + v1 + "," + v2 + "," + v3 + "):");
                            System.err.println("Couldn't create child mesh #" + (child + 1) + ": no more seeds");
                            throw new MeshTopologyException(ROI3DTriangularMesh.this, null);
                        }
                    }
                    else
                    {
                        final List<Polygon3D> newFaces = new ArrayList<Polygon3D>();
                        final List<Vertex3D> newVertices = new ArrayList<Vertex3D>(numVertices);

                        // create a null empty list that will be used to clone the source vertices
                        for (int i = 0; i < numVertices; i++)
                            newVertices.add(null);

                        extractVertices(seed, visitedVertices, vertices, newVertices);
                        extractFaces(newVertices, cells, newFaces);

                        // Fill the hole using the cutting face

                        // First, add the 3 vertices from the cut
                        for (int v : new int[] {v1, v2, v3})
                        {
                            final Vertex3D newV = vertices.get(v).clone();

                            newVertices.set(v, newV);

                            // the neighbors have also been cloned
                            // remove those on the wrong side of the cut
                            // => they should point to null in the child's vertex list

                            final Iterator<Integer> it = newV.neighbors.iterator();
                            while (it.hasNext())
                            {
                                final int n = it.next();

                                if ((n != v1) && (n != v2) && (n != v3) && (newVertices.get(n) == null))
                                    // newV should not point to n anymore
                                    it.remove();
                            }
                        }

                        // Add the new face used for the cut
                        // => find any edge (e.g. v1-v2) to check its ordering
                        for (Polygon3D f : newFaces)
                        {
                            if (f.contains(v1) && f.contains(v2))
                            {
                                // if the edge v1-v2 is counter-clockwise in f,
                                // the new face must be clockwise and vice-versa
                                newFaces.add(f.isEdgeOrdered(v1, v2) ? createCell(v1, v3, v2) : createCell(v1, v2, v3));
                                break;
                            }
                        }

                        // more than 10 faces ? --> valid
                        if (newFaces.size() > 10)
                        {
                            // if we obtain 2 meshes with more than 10 faces then we need to create a new mesh (done through MeshTopologyException...)
                            if (resultVertices != null)
                            {
                                final ROI3DTriangularMesh[] children = new ROI3DTriangularMesh[2];

                                // first child
                                children[0] = buildMesh(resultVertices, resultFaces);
                                // second child
                                children[1] = buildMesh(newVertices, newFaces);

                                // throw exception with the new children
                                throw new MeshTopologyException(ROI3DTriangularMesh.this, children);
                            }

                            // set resulting mesh
                            resultVertices = newVertices;
                            resultFaces = newFaces;
                        }
                    }
                }
            }
        }

        // if we obtain no mesh with more than 10 faces then something wrong happened...
        if (resultVertices == null)
            throw new MeshTopologyException(ROI3DTriangularMesh.this, null);

        // apply new geometry
        setVertexData(resultVertices, false);
        setCellData(resultFaces, false);

        roiChanged(true);

        // try
        // {
        // final ROI3DTriangularMesh newMesh = getClass().newInstance();
        //
        // newMesh.setColor(getColor());
        // newMesh.setVertexData(newVertices, false);
        // newMesh.setCellData(newFaces, false);
        // newMesh.setT(getT());
        // newMesh.setC(getC());
        //
        // // keep meshes with more than 10 vertices
        // if (newMesh.getNumberOfCells() > 10)
        // {
        // newMesh.roiChanged(true);
        // children[child] = newMesh;
        // }
        // }
        // catch (Exception e)
        // {
        // throw new RuntimeException(e);
        // }
        // }

        // if(children[0]==null)
        //
        // {
        // if (children[1] == null)
        // throw new MeshTopologyException(ROI3DTriangularMesh.this, null);
        // }else
        // {
        // if (children[1] != null)
        // throw new MeshTopologyException(ROI3DTriangularMesh.this, children);
        // }
    }

    private ROI3DTriangularMesh buildMesh(List<Vertex3D> vertices, List<Polygon3D> faces)
    {
        final ROI3DTriangularMesh result = new ROI3DTriangularMesh();

        result.setColor(getColor());
        result.setVertexData(vertices, false);
        result.setCellData(faces, false);
        result.setT(getT());
        result.setC(getC());

        result.roiChanged(true);

        return result;
    }

    private void subdivide()
    {
        final List<Polygon3D> oldFaces = new ArrayList<Polygon3D>(cells);

        beginUpdate();
        try
        {
            cells.clear();

            for (Vertex3D v : vertices)
                if (v != null)
                    v.neighbors.clear();

            for (Polygon3D f : oldFaces)
            {
                int v1 = f.vertexIndices[0];
                int v2 = f.vertexIndices[1];
                int v3 = f.vertexIndices[2];

                int centerv1v2 = addVertexBetween(v1, v2);
                int centerv2v3 = addVertexBetween(v2, v3);
                int centerv3v1 = addVertexBetween(v3, v1);

                addCell(v1, centerv1v2, centerv3v1);
                addCell(centerv1v2, v2, centerv2v3);
                addCell(centerv2v3, v3, centerv3v1);
                addCell(centerv1v2, centerv2v3, centerv3v1);
            }
        }
        finally
        {
            endUpdate();
        }

        roiChanged(true);
    }

    private int addVertexBetween(int v1, int v2)
    {
        // Create the middle vertex using v1
        final Point3d p = new Point3d(vertices.get(v1).position);

        // move it half way towards v2
        p.interpolate(vertices.get(v2).position, 0.5);

        return addVertex(createVertex(p));
    }

    /**
     * Re-samples the mesh surface such that the distance between any two neighbor vertices of the
     * surface is close to the specified distance (with specified tolerance)
     * 
     * @param distance
     *        average target distance between mesh vertices
     * @param tolerance
     *        the tolerance around the target resolution (percentage between 0 and 1). A
     *        tolerance of 40% (0.4) is recommended
     * @throws MeshTopologyException
     *         if the mesh becomes inconsistent (splitting or vanishing)
     */
    public void resample(double distance, double tolerance) throws MeshTopologyException
    {
        // safeguard
        if (tolerance < 0 || tolerance > 1)
            throw new IllegalArgumentException("Invalid tolerance: " + tolerance);

        final double minADist = distance * (1.0 - tolerance);
        final double maxADist = distance * (1.0 + tolerance);
        // we use squared value (trivial optimization)
        final double minAllowedDist = minADist * minADist;
        final double maxAllowedDist = maxADist * maxADist;

        // if there are 2 faces only in the mesh, it should be destroyed
        if (cells.size() < 10)
        {
            System.out.println(getName() + " has vanished (too small)");
            throw new MeshTopologyException(ROI3DTriangularMesh.this, null);
        }

        boolean roiChanged = false;
        int cpt = -1;

        while (true)
        {
            cpt++;

            // we are looking for 2 faces f1 = a-b-c1 and f2 = b-a-c2
            // such that they share an edge a-b that is either
            // - shorter than the low-threshold (resolution * min)
            // or
            // - longer than the high-threshold (resolution * max)

            boolean split = false, merge = false;
            int e1 = 0, e2 = 0, f1v3 = -1, f2v3 = -1;
            Polygon3D f1 = null, f2 = null;
            int[] f1v123 = null;
            final int[] f1v231 = new int[3];
            final int[] f1v312 = new int[3];

            for (int i = 0; i < cells.size(); i++)
            {
                f1 = getCell(i);

                f1v123 = f1.vertexIndices;
                f1v231[0] = f1v123[1];
                f1v231[1] = f1v123[2];
                f1v231[2] = f1v123[0];
                f1v312[0] = f1v123[2];
                f1v312[1] = f1v123[0];
                f1v312[2] = f1v123[1];

                double minEdgeDist = minAllowedDist;
                int merge_e1 = -1, merge_e2 = -1, merge_f1v3 = -1;
                double maxEdgeDist = maxAllowedDist;
                int split_e1 = -1, split_e2 = -1, split_f1v3 = -1;

                // find the extreme edge sizes
                for (int v = 0; v < 3; v++)
                {
                    // --> null pointer here ??
                    final Vertex3D v1 = vertices.get(f1v123[v]);
                    final Vertex3D v2 = vertices.get(f1v231[v]);

                    // can happen sometime (need to figure how)
                    if ((v1 != null) && (v2 != null))
                    {
                        final double edgeDist = v1.position.distanceSquared(v2.position);

                        if (edgeDist < minEdgeDist)
                        {
                            minEdgeDist = edgeDist;
                            merge_e1 = f1v123[v];
                            merge_e2 = f1v231[v];
                            merge_f1v3 = f1v312[v];
                        }
                        else if (edgeDist > maxEdgeDist)
                        {
                            maxEdgeDist = edgeDist;
                            split_e1 = f1v123[v];
                            split_e2 = f1v231[v];
                            split_f1v3 = f1v312[v];
                        }
                    }
                }

                // need merge ? (favor merging over splitting)
                if (minEdgeDist < minAllowedDist)
                {
                    merge = true;
                    e1 = merge_e1;
                    e2 = merge_e2;
                    f1v3 = merge_f1v3;
                    break;
                }
                // need split ?
                else if (maxEdgeDist > maxAllowedDist)
                {
                    split = true;
                    e1 = split_e1;
                    e2 = split_e2;
                    f1v3 = split_f1v3;
                    break;
                }
            }

            // no change ? --> everything is fine, stop here !
            if (!split && !merge)
                break;

            // => we need the second associated face for that edge
            for (int i = 0; i < cells.size(); i++)
            {
                f2 = getCell(i);

                if (f2 == f1)
                    continue;

                // check if f2 contains [v1-v2]
                if ((e1 == f2.vertexIndices[0]) && (e2 == f2.vertexIndices[2]))
                {
                    f2v3 = f2.vertexIndices[1];
                    break;
                }
                else if ((e1 == f2.vertexIndices[1]) && (e2 == f2.vertexIndices[0]))
                {
                    f2v3 = f2.vertexIndices[2];
                    break;
                }
                else if ((e1 == f2.vertexIndices[2]) && (e2 == f2.vertexIndices[1]))
                {
                    f2v3 = f2.vertexIndices[0];
                    break;
                }
            }

            // CASE 0: THE MESH IS INCONSISTENT
            if (f2v3 == -1)
            {
                // should never happen:
                // if f2v3 does not exist, then [v1-v2] only belongs to a single face (f1)
                // => this means the mesh is inconsistent (not closed)

                System.err.println("[MESH RESAMPLING ERROR] Problematic edge: " + e1 + "-" + e2 + ":");
                System.err.print(" Vertex " + f1v123[0] + " has neighbors: ");
                for (Integer nn : getVertex(f1v123[0]).neighbors)
                    System.err.print(nn.intValue() + "  ");
                System.err.println();
                System.err.print(" Vertex " + f1v123[1] + " has neighbors: ");
                for (Integer nn : getVertex(f1v123[1]).neighbors)
                    System.err.print(nn.intValue() + "  ");
                System.err.println();
                System.err.print(" Vertex " + f1v123[2] + " has neighbors: ");
                for (Integer nn : getVertex(f1v123[2]).neighbors)
                    System.err.print(nn.intValue() + "  ");
                System.err.println();

                System.err.println("The mesh will be removed from further computations");

                throw new MeshTopologyException(ROI3DTriangularMesh.this, null);
            }

            roiChanged = true;

            // we're about to change the mesh structure
            // => lock everything to prevent nasty bugs
            synchronized (cells)
            {
                synchronized (vertices)
                {
                    // CASE 1: MERGE
                    if (merge)
                    {
                        // Check first if the edge to merge is at the base of a tetrahedron
                        // if so, delete the whole tetrahedron
                        if (vertices.get(f1v3).neighbors.size() == 3)
                        {
                            // deleteTetrahedron(f1v3, f1v1, f1v2);
                            deleteTetrahedron(f1v3, e1, e2);
                        }
                        else if (vertices.get(f2v3).neighbors.size() == 3)
                        {
                            // deleteTetrahedron(f2v3, f1v2, f1v1);
                            deleteTetrahedron(f2v3, e2, e1);
                        }
                        else
                        {
                            final Vertex3D v1 = vertices.get(e1);
                            final Vertex3D v2 = vertices.get(e2);

                            // if v1 and v2 have a 3rd common neighbor n in addition to f1v3 and f2v3,
                            // then the mesh has reached a tubular structure.
                            // => split the mesh using the virtual face [v1,v2,n]
                            for (int n : v1.neighbors)
                            {
                                if (v2.neighbors.contains(n) && (n != f1v3) && (n != f2v3))
                                {
                                    // FIXME: what is splitContourAtVertices(..) actually doing ?? (Stephane)
                                    splitContourAtVertices(e1, e2, n);
                                    // don't go further
                                    return;
                                }
                            }

                            // Now, we can confidently merge v1 and v2:
                            // 1) Remove f1, f2 (they used to [v1-v2])
                            // 2) Move v1 to the center of [v1-v2]
                            // 3) Remove v2 from its neighborhood
                            // 4) Faces pointing to v2 should now point to v1
                            // 5) Delete v2

                            // 1) remove the 2 old faces sharing [v1-v2]
                            cells.remove(f1);
                            cells.remove(f2);

                            // 2a) Move v1 to the middle of v1-v2
                            v1.position.interpolate(v2.position, 0.5);

                            // 3) Remove v2 from its neighborhood...
                            for (int n : v2.neighbors)
                            {
                                final Vertex3D vn = vertices.get(n);

                                vn.neighbors.remove(e2);

                                // additionally, add v2's neighbors to v1
                                // (except the existing ones: e1, f1v3, f2v3)
                                if ((n != e1) && (n != f1v3) && (n != f2v3))
                                {
                                    v1.neighbors.add(n);
                                    vn.neighbors.add(e1);
                                }
                            }

                            // 4) All faces pointing to v2 should point to v1
                            // System.out.println("replaced vertex " + e2 + ":" + vertices.get(e2) + " by " + e1 + ":"
                            // + vertices.get(e1));
                            for (Polygon3D f : cells)
                                f.replace(e2, e1);

                            // 5) delete the old vertex and notify its neighbors
                            deleteVertex(e2);
                        }
                    }
                    // CASE 2: INVERT or SPLIT
                    else if (split)
                    {
                        // 1) remove the old faces
                        cells.remove(f1);
                        cells.remove(f2);

                        // 2) the vertices won't be neighbors anymore
                        vertices.get(e1).neighbors.remove(e2);
                        vertices.get(e2).neighbors.remove(e1);

                        // 3) invert or split?
                        final Vertex3D v1 = vertices.get(f1v3);
                        final Vertex3D v2 = vertices.get(f2v3);

                        if ((v1.distanceSqTo(v2) < maxAllowedDist) && v1.neighbors.contains(f2v3))
                        {
                            // INVERT --> 3) create the two new faces
                            addCell(f1v3, e1, f2v3);
                            addCell(f2v3, e2, f1v3);
                        }
                        else
                        {
                            // SPLIT

                            // 3) create a vertex in the middle of the edge (it may be merged from an existing one)
                            int newV = addVertexBetween(e1, e2);

                            // System.out.println("added vertex " + newV);

                            // 4) create 4 new faces around the new vertex
                            // we need to check this as the new vertex may has been merged with a previous existing one (Stephane fix)
                            if ((newV != e1) && (newV != f1v3))
                                addCell(e1, newV, f1v3);
                            // we need to check this as the new vertex may has been merged with a previous existing one
                            if ((newV != f1v3) && (newV != e2))
                                addCell(f1v3, newV, e2);
                            // we need to check this as the new vertex may has been merged with a previous existing one
                            if ((newV != e1) && (newV != f2v3))
                                addCell(e1, f2v3, newV);
                            // we need to check this as the new vertex may has been merged with a previous existing one
                            if ((newV != f2v3) && (newV != e2))
                                addCell(newV, f2v3, e2);
                        }
                    }
                }
            }

            // prevent infinite loop
            if (cpt > (getNumberOfVertices(false) * 2))
                break;
        }

        if (roiChanged)
            roiChanged(true);
    }
}
