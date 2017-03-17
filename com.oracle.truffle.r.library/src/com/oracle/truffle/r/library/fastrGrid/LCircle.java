/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (C) 2001-3 Paul Murrell
 * Copyright (c) 1998-2013, The R Core Team
 * Copyright (c) 2017, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.library.fastrGrid;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.abstractVectorValue;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.fastrGrid.Unit.UnitConversionContext;
import com.oracle.truffle.r.library.fastrGrid.ViewPortTransform.GetViewPortTransformNode;
import com.oracle.truffle.r.library.fastrGrid.device.DrawingContext;
import com.oracle.truffle.r.library.fastrGrid.device.GridDevice;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nmath.RMath;

public abstract class LCircle extends RExternalBuiltinNode.Arg3 {
    @Child private Unit.UnitToInchesNode unitToInches = Unit.createToInchesNode();
    @Child private Unit.UnitLengthNode unitLength = Unit.createLengthNode();
    @Child private GetViewPortTransformNode getViewPortTransform = new GetViewPortTransformNode();

    static {
        Casts casts = new Casts(LCircle.class);
        casts.arg(0).mustBe(abstractVectorValue());
        casts.arg(1).mustBe(abstractVectorValue());
        casts.arg(2).mustBe(abstractVectorValue());
    }

    public static LCircle create() {
        return LCircleNodeGen.create();
    }

    @Specialization
    Object doCircle(RAbstractVector xVec, RAbstractVector yVec, RAbstractVector radiusVec) {
        GridContext ctx = GridContext.getContext();
        GridDevice dev = ctx.getCurrentDevice();

        RList currentVP = ctx.getGridState().getViewPort();
        DrawingContext drawingCtx = GPar.asDrawingContext(ctx.getGridState().getGpar());
        ViewPortTransform vpTransform = getViewPortTransform.execute(currentVP, dev);
        ViewPortContext vpContext = ViewPortContext.fromViewPort(currentVP);
        UnitConversionContext conversionCtx = new UnitConversionContext(vpTransform.size, vpContext, dev, drawingCtx);

        int length = GridUtils.maxLength(unitLength, xVec, yVec, radiusVec);
        for (int i = 0; i < length; i++) {
            Size radiusSizes = Size.fromUnits(unitToInches, radiusVec, radiusVec, i, conversionCtx);
            double radius = RMath.fmin2(radiusSizes.getWidth(), radiusSizes.getHeight());
            Point origLoc = Point.fromUnits(unitToInches, xVec, yVec, i, conversionCtx);
            Point loc = TransformMatrix.transLocation(origLoc, vpTransform.transform);
            dev.drawCircle(drawingCtx, loc.x, loc.y, radius);
        }
        return RNull.instance;
    }
}
