/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.library.fastrGrid.device.awt;

import static com.oracle.truffle.r.library.fastrGrid.device.DrawingContext.INCH_TO_POINTS_FACTOR;
import static java.awt.geom.Path2D.WIND_EVEN_ODD;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.MemoryImageSource;

import com.oracle.truffle.r.library.fastrGrid.device.DrawingContext;
import com.oracle.truffle.r.library.fastrGrid.device.DrawingContext.GridFontStyle;
import com.oracle.truffle.r.library.fastrGrid.device.DrawingContext.GridLineEnd;
import com.oracle.truffle.r.library.fastrGrid.device.DrawingContext.GridLineJoin;
import com.oracle.truffle.r.library.fastrGrid.device.GridColor;
import com.oracle.truffle.r.library.fastrGrid.device.GridDevice;
import com.oracle.truffle.r.runtime.RInternalError;

/**
 * A device that draws to given {@code Graphics2D} object regardless of whether it was created for
 * e.g. an image, or window. This device only used by other devices and not exposed at the R level.
 * Note: it is responsibility of the use to handle resources management, i.e. calling
 * {@code dispose} on the graphics object once it is not needed anymore.
 */
public class Graphics2DDevice implements GridDevice {
    // Grid's coordinate system has origin in left bottom corner and y axis grows from bottom to
    // top. Moreover, the grid system uses inches as units. We do not use builtin transformations,
    // because (a) text rendering gets affected badly (upside down text), (b) the user of this call
    // may wish to apply his/her own transformations to the graphics object and we should not
    // interfere with these. In cases we do use transformation, we make sure to set back the
    // original one after we're done.
    static final double AWT_POINTS_IN_INCH = GraphicsEnvironment.isHeadless() ? 72. : Toolkit.getDefaultToolkit().getScreenResolution();

    private static BasicStroke blankStroke;

    private final int width;
    private final int height;
    private Graphics2D graphics;
    private final boolean graphicsIsExclusive;
    private DrawingContext cachedContext;

    /**
     * @param graphics Object that should be used for the drawing.
     * @param width Width of the drawing area in AWT units.
     * @param height Height of the drawing area in AWT units.
     * @param graphicsIsExclusive If the graphics object is exclusively used for drawing only by
     *            this class, then it can optimize some things.
     */
    public Graphics2DDevice(Graphics2D graphics, int width, int height, boolean graphicsIsExclusive) {
        initStrokes();
        setGraphics2D(graphics);
        this.width = width;
        this.height = height;
        this.graphicsIsExclusive = graphicsIsExclusive;
    }

    static void defaultInitGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setBackground(Color.WHITE);
    }

    @Override
    public void openNewPage() {
        ensureOpen();
        graphics.clearRect(0, 0, getWidthAwt(), getHeightAwt());
        cachedContext = null;
    }

    @Override
    public void drawRect(DrawingContext ctx, double leftXIn, double bottomYIn, double widthIn, double heightIn, double rotationAnticlockWise) {
        ensureOpen();
        double leftXReal = transX(leftXIn);
        double topYReal = transY(bottomYIn + heightIn);
        int rectWidth = transDim(widthIn, leftXReal);
        int rectHeight = transDim(heightIn, topYReal);
        int leftX = iround(leftXReal);
        int topY = iround(topYReal);
        setContext(ctx);
        if (rotationAnticlockWise == 0.) {
            drawShape(ctx, new Rectangle2D.Double(leftX, topY, rectWidth, rectHeight));
        } else {
            int halfWidth = iround(rectWidth / 2.);
            int halfHeight = iround(rectHeight / 2.);
            transformed(iround(leftX + halfWidth), iround(topY + halfHeight), rotationAnticlockWise, () -> drawShape(ctx, new Rectangle2D.Double(-halfWidth, -halfHeight, rectWidth, rectHeight)));
        }
    }

    @Override
    public void drawPolyLines(DrawingContext ctx, double[] x, double[] y, int startIndex, int length) {
        ensureOpen();
        Path2D.Double path = getPath2D(x, y, startIndex, length);
        setContext(ctx);
        graphics.draw(path);
    }

    @Override
    public void drawPolygon(DrawingContext ctx, double[] x, double[] y, int startIndex, int length) {
        ensureOpen();
        Path2D.Double path = getPath2D(x, y, startIndex, length);
        setContext(ctx);
        drawShape(ctx, path);
    }

    @Override
    public void drawCircle(DrawingContext ctx, double centerXIn, double centerYIn, double radiusIn) {
        ensureOpen();
        setContext(ctx);
        double xRel = transX(centerXIn - radiusIn);
        double yRel = transY(centerYIn + radiusIn);
        int diameter = transDim(radiusIn * 2d, Math.max(xRel % 1, yRel % 1));
        drawShape(ctx, new Ellipse2D.Double(iround(xRel), iround(yRel), diameter, diameter));
    }

    @Override
    public void drawRaster(double leftX, double bottomY, double width, double height, int[] pixels, int pixelsColumnsCount, ImageInterpolation interpolation) {
        ensureOpen();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, fromInterpolation(interpolation));
        Image image = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(pixelsColumnsCount, pixels.length / pixelsColumnsCount, pixels, 0, pixelsColumnsCount));
        double yRel = transY(bottomY + height);
        double xRel = transX(leftX);
        graphics.drawImage(image, iround(xRel), iround(yRel), transDim(width, xRel), transDim(height, yRel), null);
    }

    @Override
    public void drawString(DrawingContext ctx, double leftXIn, double bottomYIn, double rotationAnticlockWise, String text) {
        ensureOpen();
        setContextAndFont(ctx);
        int leftX = iround(transX(leftXIn));
        FontMetrics fontMetrics = graphics.getFontMetrics(graphics.getFont());
        int bottomY = iround(transY(bottomYIn)) - fontMetrics.getDescent();
        transformed(leftX, bottomY, rotationAnticlockWise, () -> graphics.drawString(text, 0, 0));
    }

    @Override
    public double getWidth() {
        return getWidthAwt() / AWT_POINTS_IN_INCH;
    }

    @Override
    public double getHeight() {
        return getHeightAwt() / AWT_POINTS_IN_INCH;
    }

    @Override
    public double getStringWidth(DrawingContext ctx, String text) {
        setContextAndFont(ctx);
        int swingUnits = graphics.getFontMetrics(graphics.getFont()).stringWidth(text);
        return swingUnits / AWT_POINTS_IN_INCH;
    }

    @Override
    public double getStringHeight(DrawingContext ctx, String text) {
        setContextAndFont(ctx);
        FontMetrics fontMetrics = graphics.getFontMetrics(graphics.getFont());
        double swingUnits = fontMetrics.getAscent() + fontMetrics.getDescent();
        return swingUnits / AWT_POINTS_IN_INCH;
    }

    /**
     * Allows to override the default fixed width behavior with dynamically computed width.
     */
    int getWidthAwt() {
        return width;
    }

    /**
     * Allows to override the default fixed height behavior with dynamically computed height.
     */
    int getHeightAwt() {
        return height;
    }

    /**
     * If the device can be closed by an action outside of the R interpreter (not e.g. dev.close()),
     * then the device should be able to re-open itself if any drawing happens after it was closed
     * in such way.
     */
    void ensureOpen() {
        // nop
    }

    void setGraphics2D(Graphics2D newGraphics) {
        assert newGraphics != null;
        graphics = newGraphics;
        cachedContext = null;
    }

    public Graphics2D getGraphics2D() {
        return graphics;
    }

    private double transY(double y) {
        return getHeightAwt() - y * AWT_POINTS_IN_INCH;
    }

    private static double transX(double x) {
        return x * AWT_POINTS_IN_INCH;
    }

    private static int transDim(double widthOrHeight, double transformedAxis) {
        return (int) Math.round(widthOrHeight * AWT_POINTS_IN_INCH + transformedAxis % 1);
    }

    private static void initStrokes() {
        if (blankStroke != null) {
            return;
        }
        blankStroke = new BasicStroke(0f);
    }

    private void transformed(int centreX, int centreY, double radiansAnticlockwise, Runnable action) {
        AffineTransform oldTransform = graphics.getTransform();
        AffineTransform newTr = new AffineTransform(oldTransform);
        newTr.translate(centreX, centreY);
        newTr.rotate(-radiansAnticlockwise);
        graphics.setTransform(newTr);
        action.run();
        graphics.setTransform(oldTransform);
    }

    private Path2D.Double getPath2D(double[] x, double[] y, int startIndex, int length) {
        assert startIndex >= 0 && startIndex < x.length && startIndex < y.length : "startIndex out of bounds";
        assert length > 0 && (startIndex + length) <= Math.min(x.length, y.length) : "length out of bounds";
        Path2D.Double path = new Path2D.Double(WIND_EVEN_ODD, x.length);
        path.moveTo(transX(x[startIndex]), transY(y[startIndex]));
        for (int i = startIndex + 1; i < length; i++) {
            path.lineTo(transX(x[i]), transY(y[i]));
        }
        return path;
    }

    private void drawShape(DrawingContext drawingCtx, Shape shape) {
        Paint paint = graphics.getPaint();
        graphics.setPaint(fromGridColor(drawingCtx.getFillColor()));
        graphics.fill(shape);
        graphics.setPaint(paint);
        graphics.draw(shape);
    }

    private void setContext(DrawingContext ctx) {
        if (graphicsIsExclusive && cachedContext == ctx) {
            return;
        }
        graphics.setColor(fromGridColor(ctx.getColor()));
        graphics.setStroke(getStrokeFromCtx(ctx));
        cachedContext = ctx;
    }

    private void setContextAndFont(DrawingContext ctx) {
        if (graphicsIsExclusive && cachedContext == ctx) {
            return;
        }
        setContext(ctx);
        float fontSize = (float) ((ctx.getFontSize() / INCH_TO_POINTS_FACTOR) * AWT_POINTS_IN_INCH);
        Font font = new Font(getFontName(ctx.getFontFamily()), getAwtFontStyle(ctx.getFontStyle()), 1).deriveFont(fontSize);
        graphics.setFont(font);
    }

    // Transformation of DrawingContext data types to AWT constants

    private static String getFontName(String gridFontFamily) {
        if (gridFontFamily == null) {
            return null;
        }
        switch (gridFontFamily) {
            case DrawingContext.FONT_FAMILY_MONO:
                return Font.MONOSPACED;
            case DrawingContext.FONT_FAMILY_SANS:
                return Font.SANS_SERIF;
            case DrawingContext.FONT_FAMILY_SERIF:
                return Font.SERIF;
            case "":
                return null;
        }
        return gridFontFamily;
    }

    private static int getAwtFontStyle(GridFontStyle fontStyle) {
        switch (fontStyle) {
            case PLAIN:
                return Font.PLAIN;
            case BOLD:
                return Font.BOLD;
            case ITALIC:
                return Font.ITALIC;
            case BOLDITALIC:
                return Font.BOLD | Font.ITALIC;
            default:
                throw RInternalError.shouldNotReachHere("unexpected value of GridFontStyle enum");
        }
    }

    private static Color fromGridColor(GridColor color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static BasicStroke getStrokeFromCtx(DrawingContext ctx) {
        byte[] type = ctx.getLineType();
        double width = ctx.getLineWidth();
        int lineJoin = fromGridLineJoin(ctx.getLineJoin());
        float lineMitre = (float) ctx.getLineMitre();
        int endCap = fromGridLineEnd(ctx.getLineEnd());
        if (type == DrawingContext.GRID_LINE_BLANK) {
            return blankStroke;
        } else if (type == DrawingContext.GRID_LINE_SOLID) {
            return new BasicStroke((float) (width), endCap, lineJoin, lineMitre);
        }
        float[] pattern = new float[type.length];
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = type[i];
        }
        return new BasicStroke((float) (width), endCap, lineJoin, lineMitre, pattern, 0f);
    }

    private static int fromGridLineEnd(GridLineEnd lineEnd) {
        switch (lineEnd) {
            case ROUND:
                return BasicStroke.CAP_ROUND;
            case BUTT:
                return BasicStroke.CAP_BUTT;
            case SQUARE:
                return BasicStroke.CAP_SQUARE;
            default:
                throw RInternalError.shouldNotReachHere("unexpected value of GridLineEnd enum");
        }
    }

    private static int fromGridLineJoin(GridLineJoin lineJoin) {
        switch (lineJoin) {
            case BEVEL:
                return BasicStroke.JOIN_BEVEL;
            case MITRE:
                return BasicStroke.JOIN_MITER;
            case ROUND:
                return BasicStroke.JOIN_ROUND;
            default:
                throw RInternalError.shouldNotReachHere("unexpected value of GridLineJoin enum");
        }
    }

    private static Object fromInterpolation(ImageInterpolation interpolation) {
        if (interpolation == ImageInterpolation.NEAREST_NEIGHBOR) {
            return RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
        }
        return RenderingHints.VALUE_INTERPOLATION_BILINEAR;
    }

    private static int iround(double val) {
        return (int) Math.round(val);
    }
}
