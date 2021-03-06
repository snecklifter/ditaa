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

import org.stathissideris.ascii2image.core.RenderingOptions;
import org.stathissideris.ascii2image.core.Shape3DOrderingComparator;
import org.stathissideris.ascii2image.core.ShapeAreaComparator;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author Efstathios Sideris
 */
public class BitmapRenderer {

    private static final boolean DEBUG = false;

    Stroke normalStroke;
    Stroke dashStroke;

    public BufferedImage renderToImage(Diagram diagram, RenderingOptions options)
    {
        BufferedImage image;
        if (options.needsTransparency()) {
            image = new BufferedImage(
                    diagram.getWidth(),
                    diagram.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
        } else {
            image = new BufferedImage(
                    diagram.getWidth(),
                    diagram.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
        }

        return render(diagram, image, options);
    }

    public BufferedImage render(Diagram diagram, BufferedImage image, RenderingOptions options)
    {
        BufferedImage renderedImage = image;
        Graphics2D g2 = image.createGraphics();

        Object antialiasSetting = RenderingHints.VALUE_ANTIALIAS_OFF;
        if (options.performAntialias())
            antialiasSetting = RenderingHints.VALUE_ANTIALIAS_ON;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasSetting);

        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2.setColor(options.getBackgroundColor());
        g2.fillRect(0, 0, image.getWidth() + 10, image.getHeight() + 10);

        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));

        ArrayList<DiagramShape> shapes = diagram.getAllDiagramShapes();

        if (DEBUG) System.out.println("Rendering " + shapes.size() + " shapes (groups flattened)");

        if (options.dropShadows()) {
            //render shadows
            for (DiagramShape shape : shapes) {
                if (shape.getPoints().isEmpty()) continue;

                GeneralPath path = shape.makeIntoRenderPath(diagram, options);

                float offset = diagram.getMinimumOfCellDimension() / 3.333f;

                if (path != null
                        && shape.dropsShadow()
                        && shape.getType() != DiagramShape.TYPE_CUSTOM) {
                    GeneralPath shadow = new GeneralPath(path);
                    AffineTransform translate = new AffineTransform();
                    translate.setToTranslation(offset, offset);
                    shadow.transform(translate);
                    g2.setColor(new Color(150, 150, 150));
                    g2.fill(shadow);

                }
            }


            //blur shadows
            int blurRadius = 6;
            int blurRadius2 = blurRadius * blurRadius;
            float weight = 1.0f / (float) blurRadius2;
            float[] elements = new float[blurRadius2];
            for (int k = 0; k < blurRadius2; k++)
                elements[k] = weight;
            Kernel myKernel = new Kernel(blurRadius, blurRadius, elements);

            //if EDGE_NO_OP is not selected, EDGE_ZERO_FILL is the default which creates a black border
            ConvolveOp simpleBlur = new ConvolveOp(myKernel, ConvolveOp.EDGE_NO_OP, null);

            BufferedImage destination =
                    new BufferedImage(
                            image.getWidth(),
                            image.getHeight(),
                            image.getType());

            simpleBlur.filter(image, destination);

            g2 = (Graphics2D) destination.getGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasSetting);
            renderedImage = destination;
        }


        //fill and stroke

        float dashInterval = Math.min(diagram.getCellWidth(), diagram.getCellHeight()) / 2;
        //Stroke normalStroke = g2.getStroke();

        float strokeWeight = diagram.getMinimumOfCellDimension() / 10;

        normalStroke =
                new BasicStroke(
                        strokeWeight,
                        //10,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                );

        dashStroke =
                new BasicStroke(
                        strokeWeight,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_ROUND,
                        0,
                        new float[]{dashInterval},
                        0
                );

        //TODO: at this stage we should draw the open shapes first in order to make sure they are at the bottom (this is useful for the {mo} shape)


        //find storage shapes
        ArrayList<DiagramShape> storageShapes = new ArrayList<DiagramShape>();
        for (DiagramShape shape : shapes) {
            if (shape.getType() == DiagramShape.TYPE_STORAGE) {
                storageShapes.add(shape);
            }
        }

        //render storage shapes
        //special case since they are '3d' and should be
        //rendered bottom to top
        //TODO: known bug: if a storage object is within a bigger normal box, it will be overwritten in the main drawing loop
        //(BUT this is not possible since tags are applied to all shapes overlaping shapes)


        Collections.sort(storageShapes, new Shape3DOrderingComparator());

        g2.setStroke(normalStroke);
        for (DiagramShape shape : storageShapes) {
            GeneralPath path = shape.makeIntoRenderPath(diagram, options);

            if (!shape.isStrokeDashed()) {
                if (shape.getFillColor() != null)
                    g2.setColor(shape.getFillColor());
                else
                    g2.setColor(Color.white);
                g2.fill(path);
            }

            if (shape.isStrokeDashed())
                g2.setStroke(dashStroke);
            else
                g2.setStroke(normalStroke);
            g2.setColor(shape.getStrokeColor());
            g2.draw(path);
        }

        //sort so that the largest shapes are rendered first
        Collections.sort(shapes, new ShapeAreaComparator());

        //render the rest of the shapes
        ArrayList<DiagramShape> pointMarkers = new ArrayList<DiagramShape>();
        for (DiagramShape shape : shapes) {
            if (shape.getType() == DiagramShape.TYPE_POINT_MARKER) {
                pointMarkers.add(shape);
                continue;
            }
            if (shape.getType() == DiagramShape.TYPE_STORAGE) {
                continue;
            }
            if (shape.getType() == DiagramShape.TYPE_CUSTOM) {
                renderCustomShape(shape, g2);
                continue;
            }

            if (shape.getPoints().isEmpty()) continue;

            GeneralPath path = shape.makeIntoRenderPath(diagram, options);

            //fill
            if (path != null && shape.isClosed() && !shape.isStrokeDashed()) {
                if (shape.getFillColor() != null)
                    g2.setColor(shape.getFillColor());
                else
                    g2.setColor(Color.white);
                g2.fill(path);
            }

            //draw
            if (shape.getType() != DiagramShape.TYPE_ARROWHEAD) {
                g2.setColor(shape.getStrokeColor());
                if (shape.isStrokeDashed())
                    g2.setStroke(dashStroke);
                else
                    g2.setStroke(normalStroke);
                g2.draw(path);
            }
        }

        //render point markers

        g2.setStroke(normalStroke);
        for (DiagramShape pointMarker : pointMarkers) {
            GeneralPath path = pointMarker.makeIntoRenderPath(diagram, options);

            g2.setColor(Color.white);
            g2.fill(path);
            g2.setColor(pointMarker.getStrokeColor());
            g2.draw(path);
        }

        //handle text
        //g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        //renderTextLayer(diagram.getTextObjects().iterator());

        for (DiagramText text : diagram.getTextObjects()) {
            g2.setFont(text.getFont());
            if (text.hasOutline()) {
                g2.setColor(text.getOutlineColor());
                g2.drawString(text.getText(), text.getXPos() + 1, text.getYPos());
                g2.drawString(text.getText(), text.getXPos() - 1, text.getYPos());
                g2.drawString(text.getText(), text.getXPos(), text.getYPos() + 1);
                g2.drawString(text.getText(), text.getXPos(), text.getYPos() - 1);
            }
            g2.setColor(text.getColor());
            g2.drawString(text.getText(), text.getXPos(), text.getYPos());
        }

        if (options.renderDebugLines()) {
            Stroke debugStroke =
                    new BasicStroke(
                            1,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    );
            g2.setStroke(debugStroke);
            g2.setColor(new Color(170, 170, 170));
            g2.setXORMode(Color.white);
            for (int x = 0; x < diagram.getWidth(); x += diagram.getCellWidth())
                g2.drawLine(x, 0, x, diagram.getHeight());
            for (int y = 0; y < diagram.getHeight(); y += diagram.getCellHeight())
                g2.drawLine(0, y, diagram.getWidth(), y);
        }


        g2.dispose();

        return renderedImage;
    }

    private void renderCustomShape(DiagramShape shape, Graphics2D g2)
    {
        CustomShapeDefinition definition = shape.getDefinition();

        Rectangle bounds = shape.getBounds();

        if (definition.hasBorder()) {
            g2.setColor(shape.getStrokeColor());
            if (shape.isStrokeDashed())
                g2.setStroke(dashStroke);
            else
                g2.setStroke(normalStroke);
            g2.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y);
            g2.drawLine(bounds.x + bounds.width, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height);
            g2.drawLine(bounds.x, bounds.y + bounds.height, bounds.x + bounds.width, bounds.y + bounds.height);
            g2.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + bounds.height);
        }

        //TODO: custom shape distintion relies on filename extension. Make this more intelligent
        if (definition.getFilename().endsWith(".png")) {
            renderCustomPNGShape(shape, g2);
        }
    }

    private void renderCustomPNGShape(DiagramShape shape, Graphics2D g2)
    {
        CustomShapeDefinition definition = shape.getDefinition();
        Rectangle bounds = shape.getBounds();
        Image graphic = ImageHandler.instance().loadImage(definition.getFilename());

        int xPos, yPos, width, height;

        if (definition.stretches()) { //occupy all available space
            xPos = bounds.x;
            yPos = bounds.y;
            width = bounds.width;
            height = bounds.height;
        } else { //decide how to fit
            int newHeight = bounds.width * graphic.getHeight(null) / graphic.getWidth(null);
            if (newHeight < bounds.height) { //expand to fit width
                height = newHeight;
                width = bounds.width;
                xPos = bounds.x;
                yPos = bounds.y + bounds.height / 2 - graphic.getHeight(null) / 2;
            } else { //expand to fit height
                width = graphic.getWidth(null) * bounds.height / graphic.getHeight(null);
                height = bounds.height;
                xPos = bounds.x + bounds.width / 2 - graphic.getWidth(null) / 2;
                yPos = bounds.y;
            }
        }

        g2.drawImage(graphic, xPos, yPos, width, height, null);
    }

    public static boolean isColorDark(Color color)
    {
        int brightness = Math.max(color.getRed(), color.getGreen());
        brightness = Math.max(color.getBlue(), brightness);
        if (brightness < 200) {
            if (DEBUG) System.out.println("Color " + color + " is dark");
            return true;
        }
        if (DEBUG) System.out.println("Color " + color + " is not dark");
        return false;
    }
}
