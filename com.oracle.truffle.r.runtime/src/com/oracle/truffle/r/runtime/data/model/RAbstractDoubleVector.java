/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data.model;

import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RDouble;
import com.oracle.truffle.r.runtime.data.RDoubleVector;

public interface RAbstractDoubleVector extends RAbstractVector {

    @Override
    default Object getDataAtAsObject(int index) {
        return getDataAt(index);
    }

    default double getDataAt(@SuppressWarnings("unused") Object store, int index) {
        return getDataAt(index);
    }

    double getDataAt(int index);

    @SuppressWarnings("unused")
    default void setDataAt(Object store, int index, double value) {
        throw new UnsupportedOperationException();
    }

    @Override
    default void setNA(Object store, int index) {
        setDataAt(store, index, RRuntime.DOUBLE_NA);
    }

    @Override
    RDoubleVector materialize();

    @Override
    default boolean checkCompleteness() {
        for (int i = 0; i < getLength(); i++) {
            if (RRuntime.isNA(getDataAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    default RType getRType() {
        return RType.Double;
    }

    @Override
    default Class<?> getElementClass() {
        return RDouble.class;
    }

}
