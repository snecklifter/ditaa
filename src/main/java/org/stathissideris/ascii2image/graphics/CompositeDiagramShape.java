/**
 * ditaa - Diagrams Through Ascii Art
 * <p/>
 * Copyright (C) 2004-2011 Efstathios Sideris
 * <p/>
 * ditaa is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * <p/>
 * ditaa is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with ditaa.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.stathissideris.ascii2image.graphics;

import org.stathissideris.ascii2image.text.CellSet;
import org.stathissideris.ascii2image.text.TextGrid;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Efstathios Sideris
 */
public class CompositeDiagramShape extends DiagramComponent {

    private static final boolean DEBUG = false;

    private ArrayList<DiagramShape> shapes = new ArrayList<DiagramShape>();

    public static DiagramComponent createOpenFromBoundaryCells(
            final TextGrid grid,
            final CellSet boundaryCells,
            final int cellWidth,
            final int cellHeight,
            boolean allRound)
    {

        if (boundaryCells.getType(grid) != CellSet.TYPE_OPEN)
            throw new IllegalArgumentException("This shape is closed and cannot be handled by this method");
        if (boundaryCells.size() == 0) return null;


        CompositeDiagramShape compositeShape = new CompositeDiagramShape();
        TextGrid workGrid = new TextGrid(grid.getWidth(), grid.getHeight());
        grid.copyCellsTo(boundaryCells, workGrid);

        if (DEBUG) {
            System.out.println("Making composite shape from grid:");
            workGrid.printDebug();
        }


        CellSet visitedCells = new CellSet();

        List<DiagramShape> shapes = new ArrayList<DiagramShape>(100);

        for (TextGrid.Cell cell : boundaryCells) {
            if (workGrid.isLinesEnd(cell)) {
                CellSet nextCells = workGrid.followCell(cell);
                shapes.addAll(growEdgesFromCell(workGrid, cellWidth, cellHeight, allRound, nextCells.getFirst(), cell, visitedCells));
                break;
            }
        }

        //dashed shapes should "infect" the rest of the shapes
        boolean dashedShapeExists = false;
        for (DiagramShape shape : shapes)
            if (shape.isStrokeDashed())
                dashedShapeExists = true;

        for (DiagramShape shape : shapes) {
            if (dashedShapeExists) shape.setStrokeDashed(true);
            compositeShape.addToShapes(shape);
        }

        return compositeShape;
    }


    private static List<DiagramShape> growEdgesFromCell(
            TextGrid workGrid,
            final int cellWidth,
            final int cellHeight,
            boolean allRound,
            TextGrid.Cell cell,
            TextGrid.Cell previousCell,
            CellSet visitedCells)
    {

        List<DiagramShape> result = new ArrayList<DiagramShape>(50);

        visitedCells.add(previousCell);

        DiagramShape shape = new DiagramShape();

        shape.addToPoints(makePointForCell(previousCell, workGrid, cellWidth, cellHeight, allRound));
        if (workGrid.cellContainsDashedLineChar(previousCell)) shape.setStrokeDashed(true);

        boolean finished = false;
        while (!finished) {
            visitedCells.add(cell);
            if (workGrid.isPointCell(cell)) {
                shape.addToPoints(makePointForCell(cell, workGrid, cellWidth, cellHeight, allRound));
            }

            if (workGrid.cellContainsDashedLineChar(cell)) shape.setStrokeDashed(true);

            if (workGrid.isLinesEnd(cell)) {
                finished = true;
                if (DEBUG) System.out.println("finished shape");
            }

            CellSet nextCells = workGrid.followCell(cell, previousCell);
            if (nextCells.size() == 1) {
                previousCell = cell;
                cell = nextCells.getFirst();
            } else if (nextCells.size() > 1 || nextCells.size() == 0) {//3- or 4- way intersection
                finished = true;
                for (TextGrid.Cell nextCell : nextCells)
                    result.addAll(growEdgesFromCell(workGrid, cellWidth, cellHeight, allRound, nextCell, cell, visitedCells));
            }
        }

        result.add(shape);
        return result;
    }

    public void connectEndsToAnchors(TextGrid grid, Diagram diagram)
    {
        for (DiagramShape shape : shapes) {
            if (!shape.isClosed()) {
                shape.connectEndsToAnchors(grid, diagram);
            }
        }
    }

    public void addToShapes(DiagramShape shape)
    {
        shapes.add(shape);
    }

    public ArrayList<DiagramShape> getShapes()
    {
        return shapes;
    }

}

