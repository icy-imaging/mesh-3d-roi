/*
 * Copyright (c) 2010-2023. Institut Pasteur.
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

package plugins.adufour.roi.mesh;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.HashSet;
import java.util.Set;

/**
 * Structural element of a {@link ROI3DMesh 3D mesh}, representing a position in 3D space. This
 * class can be overridden to provide additional functionalities, under the condition that
 * subclasses also override the {@link #clone()} method accordingly.
 *
 * @author Alexandre Dufour
 */
public class Vertex3D {
    public final Point3d position = new Point3d();
    public final Vector3d normal = new Vector3d();
    public final Set<Integer> neighbors;

    protected Vertex3D(final Vertex3D v) {
        this(v.position, v.neighbors);
    }

    protected Vertex3D(final Point3d position) {
        this(position, 0);
    }

    protected Vertex3D(final Point3d position, final int nbNeighbors) {
//        if (Double.isNaN(position.x) || Double.isNaN(position.y) || Double.isNaN(position.z))
//        {
//            System.out.println("oups !");
//        }      

        this.position.set(position);
        this.neighbors = new HashSet<>(nbNeighbors);
    }

    public Vertex3D(final Point3d position, final Set<Integer> neighbors) {
        this(position, neighbors.size());

        this.neighbors.addAll(neighbors);
    }

    @Override
    public Vertex3D clone() {
        return new Vertex3D(this);
    }

    public double distanceTo(final Vertex3D v) {
        return position.distance(v.position);
    }

    public double distanceSqTo(final Vertex3D v) {
        return position.distanceSquared(v.position);
    }

    @Override
    public String toString() {
        return "Vertex {" + position.x + "," + position.y + "," + position.z + "} (" + neighbors.size() + " neighbors)";
    }
}
