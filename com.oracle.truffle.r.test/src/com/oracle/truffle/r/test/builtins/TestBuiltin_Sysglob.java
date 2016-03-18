/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_Sysglob extends TestBase {

    @Test
    public void testSysglob1() {
        assertEval("argv <- list('/home/lzhao/hg/r-instrumented/src/library/utils/man/unix/*.rd', FALSE); .Internal(Sys.glob(argv[[1]], argv[[2]]))");
    }
}
