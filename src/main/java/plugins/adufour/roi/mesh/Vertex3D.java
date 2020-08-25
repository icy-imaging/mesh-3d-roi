package plugins.adufour.roi.mesh;

import java.util.HashSet;
import java.util.Set;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

/**
 * Structural element of a {@link ROI3DMesh 3D mesh}, representing a position in 3D space. This
 * class can be overridden to provide additional functionalities, under the condition that
 * subclasses also override the {@link #clone()} method accordingly.
 * 
 * @author Alexandre Dufour
 */
public class Vertex3D
{
    public final Point3d position = new Point3d();
    public final Vector3d normal = new Vector3d();
    public final Set<Integer> neighbors;

    protected Vertex3D(Vertex3D v)
    {
        this(v.position, v.neighbors);
    }

    protected Vertex3D(Point3d position)
    {
        this(position, 0);
    }

    protected Vertex3D(Point3d position, int nbNeighbors)
    {
//        if (Double.isNaN(position.x) || Double.isNaN(position.y) || Double.isNaN(position.z))
//        {
//            System.out.println("oups !");
//        }      
        
        this.position.set(position);
        this.neighbors = new HashSet<Integer>(nbNeighbors);
    }

    public Vertex3D(Point3d position, Set<Integer> neighbors)
    {
        this(position, neighbors.size());
        
        for (Integer i : neighbors)
            this.neighbors.add(i.intValue());
    }

    public Vertex3D clone()
    {
        return new Vertex3D(this);
    }

    public double distanceTo(Vertex3D v)
    {
        return position.distance(v.position);
    }

    public double distanceSqTo(Vertex3D v)
    {
        return position.distanceSquared(v.position);
    }

    public String toString()
    {
        return "Vertex {" + position.x + "," + position.y + "," + position.z + "} (" + neighbors.size() + " neighbors)";
    }
}
