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

package plugins.adufour.roi.mesh;

import org.bioimageanalysis.icy.common.geom.rectangle.Rectangle3D;
import org.bioimageanalysis.icy.common.type.DataType;
import org.bioimageanalysis.icy.extension.plugin.abstract_.PluginFileImporter;
import org.bioimageanalysis.icy.gui.frame.progress.FileFrame;
import org.bioimageanalysis.icy.gui.viewer.Viewer;
import org.bioimageanalysis.icy.io.FileUtil;
import org.bioimageanalysis.icy.model.image.IcyBufferedImage;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.system.thread.ThreadUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import plugins.adufour.roi.mesh.polygon.ROI3DPolygonalMesh;
import plugins.kernel.canvas.VtkCanvasPlugin;

import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.util.List;

public class VTKMeshImporter extends PluginFileImporter {
    @Override
    public boolean acceptFile(final String path) {
        if (FileUtil.isDirectory(path)) return false;
        return path.endsWith("vtk") || path.endsWith("vtk.txt");
    }

    @Override
    public List<FileFilter> getFileFilters() {
        final FileFilter vtkFilter = new FileFilter() {
            @Contract(pure = true)
            @Override
            public @NotNull String getDescription() {
                return "VTK mesh files";
            }

            @Override
            public boolean accept(final @NotNull File f) {
                return f.isDirectory() || acceptFile(f.getPath());
            }
        };

        return List.of(vtkFilter);
    }

    @Override
    public boolean load(final String path, final FileFrame loadingFrame) {
        Sequence sequence = getActiveSequence();
        boolean newSequence = false;

        if (sequence == null) {
            // Create a fake Sequence
            sequence = new Sequence(FileUtil.getFileName(path, false));
            addSequence(sequence);
            newSequence = true;
        }

        if (loadingFrame != null) loadingFrame.setTitle("Importing " + FileUtil.getFileName(path) + "...");

        final ROI3DMesh<?> mesh = new ROI3DPolygonalMesh();

//        mesh.setPixelSize(new Point3d(sequence.getPixelSizeX(), sequence.getPixelSizeY(), sequence.getPixelSizeZ()));
        mesh.loadFromVTK(new File(path));

        if (newSequence) {
            final Rectangle3D.Integer bounds = mesh.getBounds();
            // Add a fake image
            sequence.addImage(new IcyBufferedImage(bounds.sizeX, bounds.sizeY, 1, DataType.UBYTE));

            final Sequence theSequence = sequence;
            ThreadUtil.invokeLater(() -> {
                final Viewer viewer = theSequence.getFirstViewer();
                if (viewer != null) viewer.setCanvas(VtkCanvasPlugin.class.getName());
            }, true);
        }

        sequence.addROI(mesh);

        return true;
    }
}
