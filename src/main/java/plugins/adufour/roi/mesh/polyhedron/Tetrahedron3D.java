/*
 * Copyright (c) 2010-2026. Institut Pasteur.
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

package plugins.adufour.roi.mesh.polyhedron;

import vtk.vtkCell3D;
import vtk.vtkTetra;

/**
 * Specialized implementation of a polyhedron in the form of a tetrahedron. By convention, if vertex
 * indices (0,1,2) describe the base of the tetrahedron, then vertex index (3) is located in the
 * direction of the base's normal vector.
 *
 * @author Alexandre Dufour
 */
public class Tetrahedron3D extends Polyhedron3D {
    public Tetrahedron3D(final int... vertexIndices) {
        super(vertexIndices);

        if (vertexIndices.length != 4)
            throw new IllegalArgumentException("A tetrahedron is defined by exactly 4 vertices");
    }

    @Override
    public Tetrahedron3D clone() {
        return new Tetrahedron3D(vertexIndices);
    }

    @Override
    public vtkCell3D createVTKCell() {
        return new vtkTetra();
    }
}
