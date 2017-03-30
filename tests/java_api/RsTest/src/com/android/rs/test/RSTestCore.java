/*
 * Copyright (C) 2008-2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.rs.test;

import android.app.ListActivity;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;

public class RSTestCore {
    ListActivity mCtx;

    public RSTestCore(ListActivity ctx) {
        mCtx = ctx;
    }

    private RenderScript mRS;

    private ArrayList<UnitTest> unitTests;
    private ListIterator<UnitTest> test_iter;
    private UnitTest activeTest;
    private boolean stopTesting;

    private ScriptField_ListAllocs_s mListAllocs;

    private ArrayAdapter<UnitTest> testAdapter;

    /* Periodic timer for ensuring future tests get scheduled */
    private Timer mTimer;
    public static final int RS_TIMER_PERIOD = 100;

    public void init(RenderScript rs) {
        mRS = rs;
        stopTesting = false;

        unitTests = new ArrayList<UnitTest>();

        unitTests.add(new UT_primitives(this, mCtx));
        unitTests.add(new UT_constant(this, mCtx));
        unitTests.add(new UT_vector(this, mCtx));
        unitTests.add(new UT_unsigned(this, mCtx));
        unitTests.add(new UT_array_init(this, mCtx));
        unitTests.add(new UT_array_alloc(this, mCtx));
        unitTests.add(new UT_kernel(this, mCtx));
        unitTests.add(new UT_kernel_struct(this, mCtx));
        unitTests.add(new UT_kernel2d(this, mCtx));
        unitTests.add(new UT_kernel3d(this, mCtx));
        unitTests.add(new UT_kernel2d_oldstyle(this, mCtx));
        unitTests.add(new UT_ctxt_default(this, mCtx));
        unitTests.add(new UT_bug_char(this, mCtx));
        unitTests.add(new UT_clamp(this, mCtx));
        unitTests.add(new UT_clamp_relaxed(this, mCtx));
        unitTests.add(new UT_convert(this, mCtx));
        unitTests.add(new UT_convert_relaxed(this, mCtx));
        unitTests.add(new UT_copy_test(this, mCtx));
        unitTests.add(new UT_rsdebug(this, mCtx));
        unitTests.add(new UT_rstime(this, mCtx));
        unitTests.add(new UT_rstypes(this, mCtx));
        unitTests.add(new UT_alloc(this, mCtx));
        unitTests.add(new UT_check_dims(this, mCtx));
        unitTests.add(new UT_static_globals(this, mCtx));
        unitTests.add(new UT_refcount(this, mCtx));
        unitTests.add(new UT_foreach(this, mCtx));
        unitTests.add(new UT_foreach_bounds(this, mCtx));
        unitTests.add(new UT_noroot(this, mCtx));
        unitTests.add(new UT_single_source_alloc(this, mCtx));
        unitTests.add(new UT_single_source_script(this, mCtx));
        unitTests.add(new UT_single_source_ref_count(this, mCtx));
        unitTests.add(new UT_script_group2_pointwise(this, mCtx));
        unitTests.add(new UT_script_group2_gatherscatter(this, mCtx));
        unitTests.add(new UT_script_group2_nochain(this, mCtx));
        unitTests.add(new UT_script_group2_float(this, mCtx));
        unitTests.add(new UT_atomic(this, mCtx));
        unitTests.add(new UT_struct(this, mCtx));
        unitTests.add(new UT_math(this, mCtx));
        unitTests.add(new UT_math_conformance(this, mCtx));
        unitTests.add(new UT_math_agree(this, mCtx));
        unitTests.add(new UT_fp16(this, mCtx));
        unitTests.add(new UT_math_fp16(this, mCtx));
        unitTests.add(new UT_fp16_globals(this, mCtx));
        unitTests.add(new UT_min(this, mCtx));
        unitTests.add(new UT_int4(this, mCtx));
        unitTests.add(new UT_element(this, mCtx));
        unitTests.add(new UT_sampler(this, mCtx));
        unitTests.add(new UT_foreach_multi(this, mCtx));
        unitTests.add(new UT_fp_mad(this, mCtx));
        unitTests.add(new UT_reduce(this, mCtx));
        unitTests.add(new UT_reduce_backward(this, mCtx));
        unitTests.add(new UT_small_struct(this, mCtx));
        unitTests.add(new UT_small_struct_2(this, mCtx));
        unitTests.add(new UT_struct_field(this, mCtx));
        unitTests.add(new UT_struct_field_simple(this, mCtx));
        unitTests.add(new UT_bitfield(this, mCtx));

        /*
        unitTests.add(new UnitTest(null, "<Pass>", 1));
        unitTests.add(new UnitTest());
        unitTests.add(new UnitTest(null, "<Fail>", -1));

        for (int i = 0; i < 20; i++) {
            unitTests.add(new UnitTest(null, "<Pass>", 1));
        }
        */

        UnitTest[] uta = new UnitTest[unitTests.size()];
        uta = unitTests.toArray(uta);

        mListAllocs = new ScriptField_ListAllocs_s(mRS, uta.length);
        for (int i = 0; i < uta.length; i++) {
            ScriptField_ListAllocs_s.Item listElem = new ScriptField_ListAllocs_s.Item();
            listElem.text = Allocation.createFromString(mRS, uta[i].name, Allocation.USAGE_SCRIPT);
            listElem.result = uta[i].getResult();
            mListAllocs.set(listElem, i, false);
            uta[i].setItem(listElem);
        }

        mListAllocs.copyAll();

        testAdapter = new ArrayAdapter<UnitTest>(mCtx, android.R.layout.simple_list_item_1, unitTests);
        mCtx.setListAdapter(testAdapter);

        test_iter = unitTests.listIterator();
        refreshTestResults(); /* Kick off the first test */

        TimerTask pTask = new TimerTask() {
            public void run() {
                refreshTestResults();
            }
        };

        mTimer = new Timer();
        mTimer.schedule(pTask, RS_TIMER_PERIOD, RS_TIMER_PERIOD);
    }

    public void checkAndRunNextTest() {
        mCtx.runOnUiThread(new Runnable() {
            public void run() {
                if (testAdapter != null)
                    testAdapter.notifyDataSetChanged();
            }
        });

        if (activeTest != null) {
            if (!activeTest.isAlive()) {
                /* Properly clean up on our last test */
                try {
                    activeTest.join();
                } catch (InterruptedException e) {
                }
                activeTest = null;
            }
        }

        if (!stopTesting && activeTest == null) {
            if (test_iter.hasNext()) {
                activeTest = test_iter.next();
                activeTest.start();
                /* This routine will only get called once when a new test
                 * should start running. The message handler in UnitTest.java
                 * ensures this. */
            } else {
                if (mTimer != null) {
                    mTimer.cancel();
                    mTimer.purge();
                    mTimer = null;
                }
            }
        }
    }

    public void refreshTestResults() {
        checkAndRunNextTest();
    }

    public void cleanup() {
        stopTesting = true;
        UnitTest t = activeTest;

        /* Stop periodic refresh of testing */
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }

        /* Wait to exit until we finish the current test */
        if (t != null) {
            try {
                t.join();
            } catch (InterruptedException e) {
            }
            t = null;
        }

    }

}
