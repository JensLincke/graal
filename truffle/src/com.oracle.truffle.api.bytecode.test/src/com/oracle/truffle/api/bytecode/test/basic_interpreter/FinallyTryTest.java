/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.exception.AbstractTruffleException;

public class FinallyTryTest extends AbstractBasicInterpreterTest {
    // @formatter:off

    private static void testOrdering(boolean expectException, RootCallTarget root, Long... order) {
        testOrderingWithArguments(expectException, root, null, order);
    }

    private static void testOrderingWithArguments(boolean expectException, RootCallTarget root, Object[] args, Long... order) {
        List<Object> result = new ArrayList<>();

        Object[] allArgs;
        if (args == null) {
            allArgs = new Object[]{result};
        } else {
            allArgs = new Object[args.length + 1];
            allArgs[0] = result;
            System.arraycopy(args, 0, allArgs, 1, args.length);
        }

        try {
            root.call(allArgs);
            if (expectException) {
                Assert.fail();
            }
        } catch (AbstractTruffleException ex) {
            if (!expectException) {
                throw new AssertionError("unexpected", ex);
            }
        }

        Assert.assertArrayEquals("expected " + Arrays.toString(order) + " got " + result, order, result.toArray());
    }

    @Test
    public void testFinallyTryBasic() {
        // try {
        //   arg0.append(1);
        // } finally {
        //   arg0.append(2);
        // }

        RootCallTarget root = parse("finallyTryBasic", b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 2));
            emitAppend(b, 1);
            b.endFinallyTry();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L);
    }

    @Test
    public void testFinallyTryException() {
        // try {
        //   arg0.append(1);
        //   throw 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        // }

        RootCallTarget root = parse("finallyTryException", b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 3));
                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrow(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L);
    }

    @Test
    public void testFinallyTryReturn() {
        // try {
        //   arg0.append(2);
        //   return 0;
        // } finally {
        //   arg0.append(1);
        // }
        // arg0.append(3);

        RootCallTarget root = parse("finallyTryReturn", b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 1));
                b.beginBlock();
                    emitAppend(b, 2);

                    emitReturn(b, 0);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 3);

            b.endRoot();
        });

        testOrdering(false, root, 2L, 1L);
    }

    @Test
    public void testFinallyTryBindBasic() {
        // try {
        //   arg0.append(1);
        // } finally(ex) {
        //   if (ex) arg0.append(3) else arg0.append(2)
        // }

        RootCallTarget root = parse("finallyTryBindBasic", b -> {
            b.beginRoot(LANGUAGE);
            BytecodeLocal ex = b.createLocal();
            b.beginFinallyTry(ex, () -> {
                b.beginIfThenElse();
                b.beginNonNull();
                b.emitLoadLocal(ex);
                b.endNonNull();
                emitAppend(b, 3);
                emitAppend(b, 2);
                b.endIfThenElse();
            });
            emitAppend(b, 1);
            b.endFinallyTry();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L);
    }

     @Test
     public void testFinallyTryBindException() {
         // try {
         //   arg0.append(1);
         //   throw 0;
         //   arg0.append(2);
         // } finally(ex) {
         //   if (ex) arg0.append(3) else arg0.append(4);
         // }

         BasicInterpreter root = parseNode("finallyTryBindException", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLocal ex = b.createLocal();
             b.beginFinallyTry(ex, () -> {
                 b.beginIfThenElse();
                 b.beginNonNull();
                 b.emitLoadLocal(ex);
                 b.endNonNull();
                 emitAppend(b, 3);
                 emitAppend(b, 4);
                 b.endIfThenElse();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitThrow(b, 0);
                     emitAppend(b, 2);
                 b.endBlock();
             b.endFinallyTry();

             emitReturn(b, 0);

             b.endRoot();
         });

         testOrdering(true, root.getCallTarget(), 1L, 3L);
     }

     @Test
     public void testFinallyTryBindReturn() {
         // try {
         //   arg0.append(2);
         //   return 0;
         // } finally(ex) {
         //   if (ex) arg0.append(4) else arg0.append(1);
         // }
         // arg0.append(3);

         RootCallTarget root = parse("finallyTryBindReturn", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLocal ex = b.createLocal();
             b.beginFinallyTry(ex, () -> {
                 b.beginIfThenElse();
                 b.beginNonNull();
                 b.emitLoadLocal(ex);
                 b.endNonNull();
                 emitAppend(b, 4);
                 emitAppend(b, 1);
                 b.endIfThenElse();
             });
                 b.beginBlock();
                     emitAppend(b, 2);

                     emitReturn(b, 0);
                 b.endBlock();
             b.endFinallyTry();

             emitAppend(b, 3);

             b.endRoot();
         });

         testOrdering(false, root, 2L, 1L);
     }

     @Test
     public void testFinallyTryBranchOut() {
         // try {
         //   arg0.append(1);
         //   goto lbl;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         // }
         // arg0.append(4)
         // lbl:
         // arg0.append(5);

         RootCallTarget root = parse("finallyTryBranchOut", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();

             b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 3));
                 b.beginBlock();
                     emitAppend(b, 1);
                     b.emitBranch(lbl);
                     emitAppend(b, 2);
                 b.endBlock();
             b.endFinallyTry();

             emitAppend(b, 4);
             b.emitLabel(lbl);
             emitAppend(b, 5);
             emitReturn(b, 0);

             b.endRoot();
         });

         testOrdering(false, root, 1L, 3L, 5L);
     }

     @Test
     public void testFinallyTryBranchForwardOutOfHandler() {
         // try {
         //   arg0.append(1);
         // } finally {
         //   arg0.append(2);
         //   goto lbl;
         // }
         // arg0.append(3);
         // lbl:
         // arg0.append(4);

         BasicInterpreter root = parseNode("finallyTryBranchForwardOutOfHandler", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 2);
                     b.emitBranch(lbl);
                 b.endBlock();
             });

                 b.beginBlock();
                     emitAppend(b, 1);
                 b.endBlock();
             b.endFinallyTry();

             emitAppend(b, 3);
             b.emitLabel(lbl);
             emitAppend(b, 4);
             emitReturn(b, 0);

             b.endRoot();
         });

         testOrdering(false, root.getCallTarget(), 1L, 2L, 4L);
     }

     @Test
     public void testFinallyTryBranchForwardOutOfHandlerUnbalanced() {
         /**
          * This test is the same as the previous, but because of the "return 0",
          * the sp at the branch does not match the sp at the label.
          */

         // try {
         //   arg0.append(1);
         //   return 0;
         // } finally {
         //   arg0.append(2);
         //   goto lbl;
         // }
         // arg0.append(3);
         // lbl:
         // arg0.append(4);

         BasicInterpreter root = parseNode("finallyTryBranchForwardOutOfHandler", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 2);
                     b.emitBranch(lbl);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturn(b, 0);
                 b.endBlock();
             b.endFinallyTry();

             emitAppend(b, 3);
             b.emitLabel(lbl);
             emitAppend(b, 4);
             emitReturn(b, 0);

             b.endRoot();
         });

         testOrdering(false, root.getCallTarget(), 1L, 2L, 4L);
     }

     @Test
     public void testFinallyTryBranchBackwardOutOfHandler() {
         // tee(0, local);
         // arg0.append(1);
         // lbl:
         // if (0 < local) {
         //   arg0.append(4);
         //   return 0;
         // }
         // try {
         //   tee(1, local);
         //   arg0.append(2);
         //   return 0;
         // } finally {
         //   arg0.append(3);
         //   goto lbl;
         // }
         // arg0.append(5);

         thrown.expect(IllegalStateException.class);
         thrown.expectMessage("Backward branches are unsupported. Use a While operation to model backward control flow.");
         parse("finallyTryBranchBackwardOutOfHandler", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();
             BytecodeLocal local = b.createLocal();

             b.beginTeeLocal(local);
             b.emitLoadConstant(0L);
             b.endTeeLocal();

             emitAppend(b, 1);

             b.emitLabel(lbl);
             b.beginIfThen();
                 b.beginLessThanOperation();
                 b.emitLoadConstant(0L);
                 b.emitLoadLocal(local);
                 b.endLessThanOperation();

                 b.beginBlock();
                     emitAppend(b, 4);
                     emitReturn(b, 0);
                 b.endBlock();
             b.endIfThen();

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 3);
                     b.emitBranch(lbl);
                 b.endBlock();
             });
                 b.beginBlock();
                     b.beginTeeLocal(local);
                     b.emitLoadConstant(1L);
                     b.endTeeLocal();
                     emitAppend(b, 2);
                     emitReturn(b, 0);
                 b.endBlock();
             b.endFinallyTry();

             emitAppend(b, 5);

             b.endRoot();
         });
     }

     /*
      * The following few test cases have local control flow inside finally handlers.
      * Since finally handlers are relocated, these local branches should be properly
      * adjusted by the builder.
      */

     @Test
     public void testFinallyTryBranchWithinHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         //   if (arg2) goto outerLbl;
         //   arg0.append(3);
         //   if (arg3) throw 123
         //   arg0.append(4);
         // } finally {
         //   arg0.append(5);
         //   goto lbl;
         //   arg0.append(6);
         //   lbl:
         //   arg0.append(7);
         // }
         // outerLbl:
         // arg0.append(8);

         BasicInterpreter root = parseNode("finallyTryBranchWithinHandler", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel outerLbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     BytecodeLabel lbl = b.createLabel();
                     emitAppend(b, 5);
                     b.emitBranch(lbl);
                     emitAppend(b, 6);
                     b.emitLabel(lbl);
                     emitAppend(b, 7);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                     emitBranchIf(b, 2, outerLbl);
                     emitAppend(b, 3);
                     emitThrowIf(b, 3, 123);
                     emitAppend(b, 4);
                 b.endBlock();
             b.endFinallyTry();
             b.emitLabel(outerLbl);
             emitAppend(b, 8);
             b.endBlock();
             b.endRoot();
         });

         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 7L, 8L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false}, 1L, 5L, 7L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false}, 1L, 2L, 5L, 7L, 8L);
         testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 7L);
     }

     @Test
     public void testFinallyTryIfThenWithinHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         //   if (arg2) goto outerLbl;
         //   arg0.append(3);
         //   if (arg3) throw 123
         //   arg0.append(4);
         // } finally {
         //   arg0.append(5);
         //   if (arg4) {
         //     arg0.append(6);
         //   }
         //   arg0.append(7);
         // }
         // arg0.append(8);

         BasicInterpreter root = parseNode("finallyTryIfThenWithinHandler", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel outerLbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 5);
                     b.beginIfThen();
                         b.emitLoadArgument(4);
                         emitAppend(b, 6);
                     b.endIfThen();
                     emitAppend(b, 7);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                     emitBranchIf(b, 2, outerLbl);
                     emitAppend(b, 3);
                     emitThrowIf(b, 3, 123);
                     emitAppend(b, 4);
                 b.endBlock();
             b.endFinallyTry();

             b.emitLabel(outerLbl);
             emitAppend(b, 8);
             b.endBlock();
             b.endRoot();
         });
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, false}, 1L, 2L, 3L, 4L, 5L, 7L, 8L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, true}, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, false}, 1L, 5L, 7L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, true}, 1L, 5L, 6L, 7L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, false}, 1L, 2L, 5L, 7L, 8L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, true}, 1L, 2L, 5L, 6L, 7L, 8L);
         testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, false}, 1L, 2L, 3L, 5L, 7L);
         testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, true}, 1L, 2L, 3L, 5L, 6L, 7L);
     }

     @Test
     public void testFinallyTryIfThenElseWithinHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         //   if (arg2) goto outerLbl;
         //   arg0.append(3);
         //   if (arg3) throw 123
         //   arg0.append(4);
         // } finally {
         //   arg0.append(5);
         //   if (arg4) {
         //     arg0.append(6);
         //   } else {
         //     arg0.append(7);
         //   }
         //   arg0.append(8);
         // }
         // outerLbl:
         // arg0.append(9);

         BasicInterpreter root = parseNode("finallyTryIfThenElseWithinHandler", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel outerLbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 5);
                     b.beginIfThenElse();
                         b.emitLoadArgument(4);
                         emitAppend(b, 6);
                         emitAppend(b, 7);
                     b.endIfThenElse();
                     emitAppend(b, 8);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                     emitBranchIf(b, 2, outerLbl);
                     emitAppend(b, 3);
                     emitThrowIf(b, 3, 123);
                     emitAppend(b, 4);
                 b.endBlock();
             b.endFinallyTry();

             b.emitLabel(outerLbl);
             emitAppend(b, 9);
             b.endBlock();
             b.endRoot();
         });

         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, false}, 1L, 2L, 3L, 4L, 5L, 7L, 8L, 9L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, true}, 1L, 2L, 3L, 4L, 5L, 6L, 8L, 9L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, false}, 1L, 5L, 7L, 8L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, true}, 1L, 5L, 6L, 8L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, false}, 1L, 2L, 5L, 7L, 8L, 9L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, true}, 1L, 2L, 5L, 6L, 8L, 9L);
         testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, false}, 1L, 2L, 3L, 5L, 7L, 8L);
         testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, true}, 1L, 2L, 3L, 5L, 6L, 8L);
     }

     @Test
     public void testFinallyTryConditionalWithinHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         //   if (arg2) goto outerLbl;
         //   arg0.append(3);
         //   if (arg3) throw 123
         //   arg0.append(4);
         // } finally {
         //   arg0.append(5);
         //   (arg4) ? { arg0.append(6); 0 } : { arg0.append(7); 0 }
         //   (arg5) ? { arg0.append(8); 0 } : { arg0.append(9); 0 }
         //   arg0.append(10);
         // }
         // arg0.append(11);

         BasicInterpreter root = parseNode("finallyTryConditionalWithinHandler", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel outerLbl = b.createLabel();

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 5);
                     b.beginConditional();
                         b.emitLoadArgument(4);
                         b.beginBlock();
                             emitAppend(b, 6);
                             b.emitLoadConstant(0L);
                         b.endBlock();
                         b.beginBlock();
                             emitAppend(b, 7);
                             b.emitLoadConstant(0L);
                         b.endBlock();
                     b.endConditional();

                     b.beginConditional();
                         b.emitLoadArgument(5);
                         b.beginBlock();
                             emitAppend(b, 8);
                             b.emitLoadConstant(0L);
                         b.endBlock();
                         b.beginBlock();
                             emitAppend(b, 9);
                             b.emitLoadConstant(0L);
                         b.endBlock();
                     b.endConditional();

                     emitAppend(b, 10);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                     emitBranchIf(b, 2, outerLbl);
                     emitAppend(b, 3);
                     emitThrowIf(b, 3, 123);
                     emitAppend(b, 4);
                 b.endBlock();
             b.endFinallyTry();

             b.emitLabel(outerLbl);
             emitAppend(b, 11);
             b.endBlock();
             b.endRoot();
         });

         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, false, true}, 1L, 2L, 3L, 4L, 5L, 7L, 8L, 10L, 11L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, true, false}, 1L, 2L, 3L, 4L, 5L, 6L, 9L, 10L, 11L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, false, true}, 1L, 5L, 7L, 8L, 10L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, true, false}, 1L, 5L, 6L, 9L, 10L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, false, true}, 1L, 2L, 5L, 7L, 8L, 10L, 11L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, true, false}, 1L, 2L, 5L, 6L, 9L, 10L, 11L);
         testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, false, true}, 1L, 2L, 3L, 5L, 7L, 8L, 10L);
         testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, true, false}, 1L, 2L, 3L, 5L, 6L, 9L, 10L);
     }

     @Test
     public void testFinallyTryLoopWithinHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         //   tee(local, 4);
         //   while (local < 7) {
         //     arg0.append(local);
         //     tee(local, local+1);
         //   }
         //   arg0.append(8);
         // }
         // arg0.append(9);

         RootCallTarget root = parse("finallyTryLoopWithinHandler", b -> {
             b.beginRoot(LANGUAGE);

             BytecodeLocal local = b.createLocal();

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 3);

                     b.beginTeeLocal(local);
                     b.emitLoadConstant(4L);
                     b.endTeeLocal();

                     b.beginWhile();
                         b.beginLessThanOperation();
                             b.emitLoadLocal(local);
                             b.emitLoadConstant(7L);
                         b.endLessThanOperation();

                         b.beginBlock();
                             b.beginAppenderOperation();
                             b.emitLoadArgument(0);
                             b.emitLoadLocal(local);
                             b.endAppenderOperation();

                             b.beginTeeLocal(local);
                                 b.beginAddOperation();
                                     b.emitLoadLocal(local);
                                     b.emitLoadConstant(1L);
                                 b.endAddOperation();
                             b.endTeeLocal();
                         b.endBlock();
                     b.endWhile();

                     emitAppend(b, 8);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                 b.endBlock();
             b.endFinallyTry();

             emitAppend(b, 9);

             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 5L, 6L, 8L);
         testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 4L, 5L, 6L, 8L, 9L);
     }


     @Test
     public void testFinallyTryShortCircuitOpWithinHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg0) return 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         //   { arg0.append(4); true } && { arg0.append(5); false } && { arg0.append(6); true }
         //   { arg0.append(7); false } || { arg0.append(8); true } || { arg0.append(9); false }
         //   arg0.append(10);
         // }
         // arg0.append(11);

         RootCallTarget root = parse("finallyTryShortCircuitOpWithinHandler", b -> {
             b.beginRoot(LANGUAGE);

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 3);

                     b.beginScAnd();
                         b.beginBlock();
                             emitAppend(b, 4);
                             b.emitLoadConstant(true);
                         b.endBlock();

                         b.beginBlock();
                             emitAppend(b, 5);
                             b.emitLoadConstant(false);
                         b.endBlock();

                         b.beginBlock();
                             emitAppend(b, 6);
                             b.emitLoadConstant(true);
                         b.endBlock();
                     b.endScAnd();

                     b.beginScOr();
                         b.beginBlock();
                             emitAppend(b, 7);
                             b.emitLoadConstant(false);
                         b.endBlock();

                         b.beginBlock();
                             emitAppend(b, 8);
                             b.emitLoadConstant(true);
                         b.endBlock();

                         b.beginBlock();
                             emitAppend(b, 9);
                             b.emitLoadConstant(false);
                         b.endBlock();
                     b.endScOr();

                     emitAppend(b, 10);

                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                 b.endBlock();
             b.endFinallyTry();

             emitAppend(b, 11);

             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 5L, 7L, 8L, 10L);
         testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 4L, 5L, 7L, 8L, 10L, 11L);
     }

     @Test
     public void testFinallyTryNonThrowingTryCatchWithinHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         //   try {
         //     arg0.append(4);
         //   } catch {
         //     arg0.append(5);
         //   }
         //   arg0.append(6);
         // }
         // arg0.append(7);

         RootCallTarget root = parse("finallyTryNonThrowingTryCatchWithinHandler", b -> {
             b.beginRoot(LANGUAGE);

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 3);
                     b.beginTryCatch(b.createLocal());
                         emitAppend(b, 4);
                         emitAppend(b, 5);
                     b.endTryCatch();
                     emitAppend(b, 6);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                 b.endBlock();
             b.endFinallyTry();

             emitAppend(b, 7);
             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 6L);
         testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 4L, 6L, 7L);
     }

     @Test
     public void testFinallyTryThrowingTryCatchWithinHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         //   try {
         //     arg0.append(4);
         //     throw 0;
         //     arg0.append(5);
         //   } catch {
         //     arg0.append(6);
         //   }
         //   arg0.append(7);
         // }
         // arg0.append(8);

         RootCallTarget root = parse("finallyTryThrowingTryCatchWithinHandler", b -> {
             b.beginRoot(LANGUAGE);
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 3);
                     b.beginTryCatch(b.createLocal());
                         b.beginBlock();
                             emitAppend(b, 4);
                             emitThrow(b, 0);
                             emitAppend(b, 5);
                         b.endBlock();

                         emitAppend(b, 6);
                     b.endTryCatch();

                     emitAppend(b, 7);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                 b.endBlock();
             b.endFinallyTry();
             emitAppend(b, 8);
             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 6L, 7L);
         testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 4L, 6L, 7L, 8L);
     }

     @Test
     public void testFinallyTryBranchWithinHandlerNoLabel() {
         // try {
         //   return 0;
         // } finally {
         //   goto lbl;
         //   return 0;
         // }

         thrown.expect(IllegalStateException.class);
         thrown.expectMessage("Operation Block ended without emitting one or more declared labels. This likely indicates a bug in the parser.");
         parse("finallyTryBranchWithinHandlerNoLabel", b -> {
             b.beginRoot(LANGUAGE);

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     b.emitBranch(b.createLabel());
                     emitReturn(b, 0);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitReturn(b, 0);
                 b.endBlock();
             b.endFinallyTry();
             b.endRoot();
         });
     }

     @Test
     public void testFinallyTryBranchIntoTry() {
         // try {
         //   return 0;
         //   lbl:
         //   return 0;
         // } finally {
         //   goto lbl;
         //   return 0;
         // }

         // This error has nothing to do with try-finally, but it's still useful to ensure this doesn't work.
         thrown.expect(IllegalStateException.class);
         thrown.expectMessage("BytecodeLabel must be emitted inside the same operation it was created in.");
         parse("finallyTryBranchIntoTry", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     b.emitBranch(lbl);
                     emitReturn(b, 0);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitReturn(b, 0);
                     b.emitLabel(lbl);
                     emitReturn(b, 0);
                 b.endBlock();
             b.endFinallyTry();

             b.endRoot();
         });
     }

     @Test
     public void testFinallyTryBranchIntoFinally() {
         // try {
         //   goto lbl;
         //   return 0;
         // } finally {
         //   lbl:
         //   return 0;
         // }

         // This error has nothing to do with try-finally, but it's still useful to ensure this doesn't work.
         thrown.expect(IllegalStateException.class);
         thrown.expectMessage("BytecodeLabel must be emitted inside the same operation it was created in.");
         parse("finallyTryBranchIntoFinally", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     b.emitLabel(lbl);
                     emitReturn(b, 0);
                 b.endBlock();
             });
                 b.beginBlock();
                     b.emitBranch(lbl);
                     emitReturn(b, 0);
                 b.endBlock();
             b.endFinallyTry();

             b.endRoot();
         });
     }

     @Test
     public void testFinallyTryBranchIntoOuterFinally() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         //   if (arg2) goto outerLbl;
         //   arg0.append(3);
         //   if (arg3) throw 123;
         //   arg0.append(4);
         // } finally {
         //   try {
         //     arg0.append(5);
         //   } finally {
         //     arg0.append(6);
         //     goto lbl;
         //     arg0.append(7);
         //   }
         //   arg0.append(8);
         //   lbl:
         //   arg0.append(9);
         // }
         // outerLbl:
         // arg0.append(10);
         // return 0;
         BasicInterpreter root = parseNode("finallyTryBranchIntoOuterFinally", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel outerLbl = b.createLabel();

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     BytecodeLabel lbl = b.createLabel();
                     b.beginFinallyTry(b.createLocal(), () -> {
                         b.beginBlock();
                             emitAppend(b, 6);
                             b.emitBranch(lbl);
                             emitAppend(b, 7);
                         b.endBlock();
                     });
                         emitAppend(b, 5);
                     b.endFinallyTry();

                     emitAppend(b, 8);
                     b.emitLabel(lbl);
                     emitAppend(b, 9);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                     emitBranchIf(b, 2, outerLbl);
                     emitAppend(b, 3);
                     emitThrowIf(b, 3, 123);
                     emitAppend(b, 4);
                 b.endBlock();
             b.endFinallyTry();

             b.emitLabel(outerLbl);
             emitAppend(b, 10);
             b.endBlock();
             b.endRoot();
         });

         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 6L, 9L, 10L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false}, 1L, 5L, 6L, 9L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false}, 1L, 2L, 5L, 6L, 9L, 10L);
         testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 6L, 9L);
     }


     @Test
     public void testFinallyTryBranchWhileInParentHandler() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         //   try {
         //     arg0.append(4);
         //     goto lbl;
         //     arg0.append(5);
         //     lbl:
         //     arg0.append(6);
         //   } finally {
         //     arg0.append(7);
         //   }
         //   arg0.append(8);
         // }
         // arg0.append(9);

         BasicInterpreter root = parseNode("finallyTryBranchWhileInParentHandler", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
                 b.beginFinallyTry(b.createLocal(), () -> {
                     b.beginBlock();
                         emitAppend(b, 3);

                         b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 7));
                             b.beginBlock();
                                 BytecodeLabel lbl = b.createLabel();
                                 emitAppend(b, 4);
                                 b.emitBranch(lbl);
                                 emitAppend(b, 5);
                                 b.emitLabel(lbl);
                                 emitAppend(b, 6);
                             b.endBlock();
                         b.endFinallyTry();

                         emitAppend(b, 8);
                     b.endBlock();
                 });
                     b.beginBlock();
                         emitAppend(b, 1);
                         emitReturnIf(b, 1, 0);
                         emitAppend(b, 2);
                     b.endBlock();
                 b.endFinallyTry();

                 emitAppend(b, 9);
             b.endBlock();
             b.endRoot();
         });

         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false}, 1L, 2L, 3L, 4L, 6L, 7L, 8L, 9L);
         testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true}, 1L, 3L, 4L, 6L, 7L, 8L);
     }

     @Test
     public void testFinallyTryNestedFinally() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         // } finally {
         //   try {
         //     arg0.append(3);
         //     if (arg2) return 0;
         //     arg0.append(4);
         //   } finally {
         //     arg0.append(5);
         //   }
         // }

         RootCallTarget root = parse("finallyTryNestedFinally", b -> {
             b.beginRoot(LANGUAGE);

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 5));
                     b.beginBlock();
                         emitAppend(b, 3);
                         emitReturnIf(b, 2, 0);
                         emitAppend(b, 4);
                     b.endBlock();
                 b.endFinallyTry();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                 b.endBlock();
             b.endFinallyTry();

             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {false, false}, 1L, 2L, 3L, 4L, 5L);
         testOrderingWithArguments(false, root, new Object[] {true, false}, 1L, 3L, 4L, 5L);
         testOrderingWithArguments(false, root, new Object[] {false, true}, 1L, 2L, 3L, 5L);
         testOrderingWithArguments(false, root, new Object[] {true, true}, 1L, 3L, 5L);
     }

     @Test
     public void testFinallyTryNestedInTry() {
         // try {
         //   try {
         //     arg0.append(1);
         //     if (arg1) return 0;
         //     arg0.append(2);
         //     if (arg2) goto outerLbl;
         //     arg0.append(3);
         //     if (arg3) throw 123
         //     arg0.append(4);
         //   } finally {
         //     arg0.append(5);
         //   }
         // } finally {
         //   arg0.append(6);
         // }
         // outerLbl:
         // arg0.append(7);

         RootCallTarget root = parse("finallyTryNestedInTry", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel outerLbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 6));
                 b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 5));
                     b.beginBlock();
                         emitAppend(b, 1);
                         emitReturnIf(b, 1, 0);
                         emitAppend(b, 2);
                         emitBranchIf(b, 2, outerLbl);
                         emitAppend(b, 3);
                         emitThrowIf(b, 3, 123);
                         emitAppend(b, 4);
                     b.endBlock();
                 b.endFinallyTry();
             b.endFinallyTry();
             b.emitLabel(outerLbl);
             emitAppend(b, 7);
             b.endBlock();
             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
         testOrderingWithArguments(false, root, new Object[] {true, false, false}, 1L, 5L, 6L);
         testOrderingWithArguments(false, root, new Object[] {false, true, false}, 1L, 2L, 5L, 6L, 7L);
         testOrderingWithArguments(true, root, new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 6L);
     }

     @Test
     public void testFinallyTryNestedInFinally() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0;
         //   arg0.append(2);
         //   if (arg2) goto outerLbl;
         //   arg0.append(3);
         //   if (arg3) throw 123
         //   arg0.append(4);
         // } finally {
         //   try {
         //     arg0.append(5);
         //     if (arg1) return 0;
         //     arg0.append(6);
         //     if (arg2) goto outerLbl;
         //     arg0.append(7);
         //     if (arg3) throw 123
         //     arg0.append(8);
         //   } finally {
         //     arg0.append(9);
         //   }
         // }
         // outerLbl:
         // arg0.append(10);

         RootCallTarget root = parse("finallyTryNestedInFinally", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel outerLbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 9));
                     b.beginBlock();
                         emitAppend(b, 5);
                         emitReturnIf(b, 1, 0);
                         emitAppend(b, 6);
                         emitBranchIf(b, 2, outerLbl);
                         emitAppend(b, 7);
                         emitThrowIf(b, 3, 123);
                         emitAppend(b, 8);
                     b.endBlock();
                 b.endFinallyTry();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                     emitBranchIf(b, 2, outerLbl);
                     emitAppend(b, 3);
                     emitThrowIf(b, 3, 123);
                     emitAppend(b, 4);
                 b.endBlock();
             b.endFinallyTry();

             b.emitLabel(outerLbl);
             emitAppend(b, 10);
             b.endBlock();
             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
         testOrderingWithArguments(false, root, new Object[] {true, false, false}, 1L, 5L, 9L);
         testOrderingWithArguments(false, root, new Object[] {false, true, false}, 1L, 2L, 5L, 6L, 9L, 10L);
         testOrderingWithArguments(true, root, new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 6L, 7L, 9L);
     }

     @Test
     public void testFinallyTryNestedInFinallyWithinAnotherFinallyTry() {
         // Same as the previous test, but put it all within another FinallyTry.
         // The unwinding step should skip over some open operations but include the outermost TryFinally.

         // try {
         //   try {
         //     arg0.append(1);
         //     if (arg1) return 0;
         //     arg0.append(2);
         //     if (arg2) goto outerLbl;
         //     arg0.append(3);
         //     if (arg3) throw 123
         //     arg0.append(4);
         //   } finally {
         //     try {
         //       arg0.append(5);
         //       if (arg1) return 0;
         //       arg0.append(6);
         //       if (arg2) goto outerLbl;
         //       arg0.append(7);
         //       if (arg3) throw 123
         //       arg0.append(8);
         //     } finally {
         //       arg0.append(9);
         //     }
         //   }
         //   outerLbl:
         //   arg0.append(10);
         // } finally {
         //   arg0.append(11);
         // }

         RootCallTarget root = parse("finallyTryNestedInFinally", b -> {
             b.beginRoot(LANGUAGE);
             b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 11));
                 b.beginBlock();
                 BytecodeLabel outerLbl = b.createLabel();

                 b.beginFinallyTry(b.createLocal(), () -> {
                     b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 9));
                         b.beginBlock();
                             emitAppend(b, 5);
                             emitReturnIf(b, 1, 0);
                             emitAppend(b, 6);
                             emitBranchIf(b, 2, outerLbl);
                             emitAppend(b, 7);
                             emitThrowIf(b, 3, 123);
                             emitAppend(b, 8);
                         b.endBlock();
                     b.endFinallyTry();
                 });
                     b.beginBlock();
                         emitAppend(b, 1);
                         emitReturnIf(b, 1, 0);
                         emitAppend(b, 2);
                         emitBranchIf(b, 2, outerLbl);
                         emitAppend(b, 3);
                         emitThrowIf(b, 3, 123);
                         emitAppend(b, 4);
                     b.endBlock();
                 b.endFinallyTry();

                 b.emitLabel(outerLbl);
                 emitAppend(b, 10);
                 b.endBlock();
             b.endFinallyTry();
             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
         testOrderingWithArguments(false, root, new Object[] {true, false, false}, 1L, 5L, 9L, 11L);
         testOrderingWithArguments(false, root, new Object[] {false, true, false}, 1L, 2L, 5L, 6L, 9L, 10L, 11L);
         testOrderingWithArguments(true, root, new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 6L, 7L, 9L, 11L);
     }

     @Test
     public void testFinallyTryNestedTryCatchWithEarlyReturn() {
         /**
          * The try-catch handler should take precedence over the finally handler.
          */

         // try {
         //   try {
         //     arg0.append(1);
         //     throw 0;
         //     arg0.append(2);
         //   } catch ex {
         //     arg0.append(3);
         //     return 0;
         //     arg0.append(4);
         //   }
         // } finally {
         //   arg0.append(5);
         // }

         BasicInterpreter root = parseNode("finallyTryNestedTryThrow", b -> {
             b.beginRoot(LANGUAGE);

             b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 5));
                 b.beginTryCatch(b.createLocal());
                     b.beginBlock();
                         emitAppend(b, 1);
                         emitThrow(b, 0);
                         emitAppend(b, 2);
                     b.endBlock();

                     b.beginBlock();
                         emitAppend(b, 3);
                         emitReturn(b, 0);
                         emitAppend(b, 4);
                     b.endBlock();
                 b.endTryCatch();
             b.endFinallyTry();

             b.endRoot();
         });

         testOrdering(false, root.getCallTarget(), 1L, 3L, 5L);
     }

     @Test
     public void testFinallyTryHandlerNotGuarded() {
         /**
          * A finally handler should not be guarded by itself. If it throws, the throw should go uncaught.
          */
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0
         //   arg0.append(2);
         //   if (arg2) goto lbl
         //   arg0.append(3);
         // } finally ex {
         //   arg0.append(4);
         //   throw MyException(123);
         // }
         // lbl:

         RootCallTarget root = parse("finallyTryHandlerNotGuarded", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLocal ex = b.createLocal();
             BytecodeLabel lbl = b.createLabel();
             b.beginFinallyTry(ex, () -> {
                 b.beginBlock();
                     emitAppend(b, 4L);
                     emitThrow(b, 123);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     b.beginIfThen();
                         b.emitLoadArgument(1);
                         b.beginReturn();
                             b.emitLoadConstant(0L);
                         b.endReturn();
                     b.endIfThen();
                     emitAppend(b, 2);
                     b.beginIfThen();
                         b.emitLoadArgument(2);
                         b.emitBranch(lbl);
                     b.endIfThen();
                     emitAppend(b, 3);
                 b.endBlock();
             b.endFinallyTry();
             b.emitLabel(lbl);

             b.endRoot();
         });

         testOrderingWithArguments(true, root,  new Object[] {false, false}, 1L, 2L, 3L, 4L);
         testOrderingWithArguments(true, root,  new Object[] {true, false}, 1L, 4L);
         testOrderingWithArguments(true, root,  new Object[] {false, true}, 1L, 2L, 4L);
     }

     @Test
     public void testFinallyTryOuterHandlerNotGuarded() {
         /**
          * A finally handler should not guard an outer handler. If the outer throws, the inner should not catch it.
          */
         // try {
         //   arg0.append(1);
         //   try {
         //      if (arg1) return 0;
         //      arg0.append(2);
         //      if (arg2) goto lbl;
         //      arg0.append(3);
         //   } finally {
         //      arg0.append(4);
         //   }
         // } finally {
         //   arg0.append(5);
         //   throw MyException(123);
         // }
         // lbl:

         RootCallTarget root = parse("finallyTryOuterHandlerNotGuarded", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 5);
                     emitThrow(b, 123);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     b.beginFinallyTry(b.createLocal(), () -> emitAppend(b, 4));
                         b.beginBlock();
                             b.beginIfThen();
                                 b.emitLoadArgument(1);
                                 b.beginReturn();
                                     b.emitLoadConstant(0L);
                                 b.endReturn();
                             b.endIfThen();
                             emitAppend(b, 2);
                             b.beginIfThen();
                                 b.emitLoadArgument(2);
                                 b.emitBranch(lbl);
                             b.endIfThen();
                             emitAppend(b, 3);
                         b.endBlock();
                     b.endFinallyTry();
                 b.endBlock();
             b.endFinallyTry();
             b.emitLabel(lbl);

             b.endRoot();
         });

         testOrderingWithArguments(true, root, new Object[] {false, false}, 1L, 2L, 3L, 4L, 5L);
         testOrderingWithArguments(true, root, new Object[] {true, false}, 1L, 4L, 5L);
         testOrderingWithArguments(true, root, new Object[] {false, true}, 1L, 2L, 4L, 5L);
     }

     @Test
     public void testFinallyTryOuterHandlerNotGuardedByTryCatch() {
         /**
          * The try-catch should not guard the outer finally handler.
          */
         // try {
         //   arg0.append(1);
         //   try {
         //      if (arg1) return 0;
         //      arg0.append(2);
         //      if (arg2) goto lbl;
         //      arg0.append(3);
         //   } catch ex {
         //      arg0.append(4);
         //   }
         // } finally {
         //   arg0.append(5);
         //   throw MyException(123);
         // }
         // lbl:

         RootCallTarget root = parse("finallyTryOuterHandlerNotGuardedByTryCatch", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 5);
                     emitThrow(b, 123);
                 b.endBlock();
             });
                 b.beginBlock(); // begin outer try
                     emitAppend(b, 1);
                     b.beginTryCatch(b.createLocal());
                         b.beginBlock(); // begin inner try
                             b.beginIfThen();
                                 b.emitLoadArgument(1);
                                 b.beginReturn();
                                     b.emitLoadConstant(0L);
                                 b.endReturn();
                             b.endIfThen();
                             emitAppend(b, 2);
                             b.beginIfThen();
                                 b.emitLoadArgument(2);
                                 b.emitBranch(lbl);
                             b.endIfThen();
                             emitAppend(b, 3);
                         b.endBlock(); // end inner try

                         emitAppend(b, 4); // inner catch
                     b.endTryCatch();
                 b.endBlock(); // end outer try

             b.endFinallyTry();

             b.emitLabel(lbl);

             b.endRoot();
         });

         testOrderingWithArguments(true, root, new Object[] {false, false}, 1L, 2L, 3L, 5L);
         testOrderingWithArguments(true, root, new Object[] {true, false}, 1L, 5L);
         testOrderingWithArguments(true, root, new Object[] {false, true}, 1L, 2L, 5L);
     }

     @Test
     public void testFinallyTryCatchBasic() {
         // try {
         //   arg0.append(1);
         // } finally {
         //   arg0.append(2);
         // } catch ex {
         //   arg0.append(3);
         // }

         RootCallTarget root = parse("finallyTryCatchBasic", b -> {
             b.beginRoot(LANGUAGE);
             b.beginFinallyTryCatch(b.createLocal(), () -> emitAppend(b, 2));
             emitAppend(b, 1);
             emitAppend(b, 3);
             b.endFinallyTryCatch();
             b.endRoot();
         });

         testOrdering(false, root, 1L, 2L);
     }

     @Test
     public void testFinallyTryCatchException() {
         // try {
         //   arg0.append(1);
         //   throw 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         // } catch ex {
         //   arg0.append(4);
         // }

         RootCallTarget root = parse("finallyTryCatchException", b -> {
             b.beginRoot(LANGUAGE);
             b.beginFinallyTryCatch(b.createLocal(), () -> emitAppend(b, 3));
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitThrow(b, 0);
                     emitAppend(b, 2);
                 b.endBlock();

                 emitAppend(b, 4);
             b.endFinallyTryCatch();
             b.endRoot();
         });

         testOrdering(false, root, 1L, 4L);
     }

     @Test
     public void testFinallyTryCatchReturn() {
         // try {
         //   arg0.append(1);
         //   return 0;
         // } finally {
         //   arg0.append(2);
         // } catch ex {
         //   arg0.append(3);
         // }
         // arg0.append(4);

         RootCallTarget root = parse("finallyTryCatchReturn", b -> {
             b.beginRoot(LANGUAGE);
             b.beginFinallyTryCatch(b.createLocal(), () -> emitAppend(b, 2));
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturn(b, 0);
                 b.endBlock();

                 emitAppend(b, 3);
             b.endFinallyTryCatch();

             emitAppend(b, 4);

             b.endRoot();
         });

         testOrdering(false, root, 1L, 2L);
     }

     @Test
     public void testFinallyTryCatchBindException() {
         // try {
         //   arg0.append(1);
         //   if (arg1) throw arg2
         // } finally {
         //   arg0.append(2);
         // } catch ex {
         //   arg0.append(ex.value);
         // }

         RootCallTarget root = parse("finallyTryCatchBindBasic", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLocal ex = b.createLocal();
             b.beginFinallyTryCatch(ex, () -> emitAppend(b, 2));
                 b.beginBlock();
                     emitAppend(b, 1);
                     b.beginIfThen();
                         b.emitLoadArgument(1);
                         b.beginThrowOperation();
                             b.emitLoadArgument(2);
                         b.endThrowOperation();
                     b.endIfThen();
                 b.endBlock();

                 b.beginAppenderOperation();
                     b.emitLoadArgument(0);
                     b.beginReadExceptionOperation();
                     b.emitLoadLocal(ex);
                     b.endReadExceptionOperation();
                 b.endAppenderOperation();
             b.endFinallyTryCatch();

             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {false, 42L}, 1L, 2L);
         testOrderingWithArguments(false, root, new Object[] {true, 42L}, 1L, 42L);
         testOrderingWithArguments(false, root, new Object[] {false, 33L}, 1L, 2L);
         testOrderingWithArguments(false, root, new Object[] {true, 33L}, 1L, 33L);
     }

     @Test
     public void testFinallyTryCatchBranchOut() {
         // try {
         //   arg0.append(1);
         //   goto lbl;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         // } catch ex {
         //   arg0.append(4);
         // }
         // arg0.append(5)
         // lbl:
         // arg0.append(6);

         RootCallTarget root = parse("finallyTryCatchBranchOut", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();

             b.beginFinallyTryCatch(b.createLocal(), () -> emitAppend(b, 3));
                 b.beginBlock();
                     emitAppend(b, 1);
                     b.emitBranch(lbl);
                     emitAppend(b, 2);
                 b.endBlock();

                 emitAppend(b, 4);
             b.endFinallyTryCatch();

             emitAppend(b, 5);
             b.emitLabel(lbl);
             emitAppend(b, 6);

             b.endRoot();
         });

         testOrdering(false, root, 1L, 3L, 6L);
     }

     @Test
     public void testFinallyTryCatchBranchOutOfCatch() {
         // try {
         //   arg0.append(1);
         //   if (arg1) throw 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         // } catch ex {
         //   arg0.append(4);
         //   goto lbl
         //   arg0.append(5);
         // }
         // arg0.append(6)
         // lbl:
         // arg0.append(7);

         RootCallTarget root = parse("finallyTryCatchBranchOutOfCatch", b -> {
             b.beginRoot(LANGUAGE);
             BytecodeLabel lbl = b.createLabel();

             b.beginFinallyTryCatch(b.createLocal(), () -> emitAppend(b, 3));
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitThrowIf(b, 1, 0);
                     emitAppend(b, 2);
                 b.endBlock();

                 b.beginBlock();
                     emitAppend(b, 4);
                     b.emitBranch(lbl);
                     emitAppend(b, 5);
                 b.endBlock();
             b.endFinallyTryCatch();

             emitAppend(b, 6);
             b.emitLabel(lbl);
             emitAppend(b, 7);
             emitReturn(b, 0);

             b.endRoot();
         });

         testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 6L, 7L);
         testOrderingWithArguments(false, root, new Object[] {true}, 1L, 4L, 7L);
     }

     @Test
     public void testFinallyTryCatchBranchWithinHandler() {
         // try {
         //   arg0.append(1);
         //   return 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         //   goto lbl;
         //   arg0.append(4);
         //   lbl:
         //   arg0.append(5);
         // } catch ex {
         //   arg0.append(6);
         // }
         // arg0.append(7);

         RootCallTarget root = parse("finallyTryCatchBranchWithinHandler", b -> {
             b.beginRoot(LANGUAGE);

             b.beginFinallyTryCatch(b.createLocal(), () -> {
                 b.beginBlock();
                     BytecodeLabel lbl = b.createLabel();
                     emitAppend(b, 3);
                     b.emitBranch(lbl);
                     emitAppend(b, 4);
                     b.emitLabel(lbl);
                     emitAppend(b, 5);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturn(b, 0);
                     emitAppend(b, 2);
                 b.endBlock();

                 emitAppend(b, 6);
             b.endFinallyTryCatch();

             emitAppend(b, 7);

             b.endRoot();
         });

         testOrdering(false, root, 1L, 3L, 5L);
     }

     @Test
     public void testFinallyTryCatchBranchWithinCatchHandler() {
         // try {
         //   arg0.append(1);
         //   throw 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         // } catch ex {
         //   arg0.append(4);
         //   goto lbl;
         //   arg0.append(5);
         //   lbl:
         //   arg0.append(6);
         // }
         // arg0.append(7);

         RootCallTarget root = parse("finallyTryCatchBranchWithinCatchHandler", b -> {
             b.beginRoot(LANGUAGE);

             b.beginFinallyTryCatch(b.createLocal(), () -> emitAppend(b, 3));
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitThrow(b, 0);
                     emitAppend(b, 2);
                 b.endBlock();

                 b.beginBlock();
                     BytecodeLabel lbl = b.createLabel();
                     emitAppend(b, 4);
                     b.emitBranch(lbl);
                     emitAppend(b, 5);
                     b.emitLabel(lbl);
                     emitAppend(b, 6);
                 b.endBlock();
             b.endFinallyTryCatch();

             emitAppend(b, 7);

             b.endRoot();
         });

         testOrdering(false, root, 1L, 4L, 6L, 7L);
     }

     @Test
     public void testFinallyTryCatchExceptionInCatch() {
         // try {
         //   arg0.append(1);
         //   throw 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         // } catch ex {
         //   arg0.append(4);
         //   throw 1;
         //   arg0.append(5);
         // }

         RootCallTarget root = parse("finallyTryCatchException", b -> {
             b.beginRoot(LANGUAGE);
             b.beginFinallyTryCatch(b.createLocal(), () -> emitAppend(b, 3));
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitThrow(b, 0);
                     emitAppend(b, 2);
                 b.endBlock();

                 b.beginBlock();
                     emitAppend(b, 4);
                     emitThrow(b, 1);
                     emitAppend(b, 5);
                 b.endBlock();
             b.endFinallyTryCatch();

             emitReturn(b, 0);

             b.endRoot();
         });

         testOrdering(true, root, 1L, 4L);
     }

     @Test
     public void testFinallyTryCatchExceptionInFinally() {
         // try {
         //   arg0.append(1);
         //   return 0;
         //   arg0.append(2);
         // } finally {
         //   arg0.append(3);
         //   throw 0;
         //   arg0.append(4);
         // } catch ex {
         //   arg0.append(5);
         // }

         RootCallTarget root = parse("finallyTryCatchExceptionInFinally", b -> {
             b.beginRoot(LANGUAGE);
             b.beginFinallyTryCatch(b.createLocal(), () -> {
                 b.beginBlock();
                     emitAppend(b, 3);
                     emitThrow(b, 0);
                     emitAppend(b, 4);
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturn(b, 0);
                     emitAppend(b, 2);
                 b.endBlock();

                 emitAppend(b, 5);
             b.endFinallyTryCatch();

             emitReturn(b, 0);

             b.endRoot();
         });

         testOrdering(true, root, 1L, 3L);
     }

     @Test
     public void testFinallyTryNestedFunction() {
         // try {
         //   arg0.append(1);
         //   if (arg1) return 0
         //   arg0.append(2);
         //   if (arg2) goto lbl
         //   arg0.append(3);
         //   if (arg3) throw 123
         //   arg0.append(4);
         // } finally {
         //   def f() { arg0.append(5) }
         //   def g() { arg0.append(6) }
         //   if (arg4) f() else g()
         // }
         // arg0.append(7)
         // lbl:
         // arg0.append(8);
         RootCallTarget root = parse("finallyTryNestedFunction", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel lbl = b.createLabel();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();

                     for (int i = 0; i < 10; i++) {
                         // Create extra root nodes to detect any serialization mismatches
                         b.beginRoot(LANGUAGE);
                             emitThrow(b, -123);
                         b.endRoot();
                     }

                     b.beginRoot(LANGUAGE);
                         emitAppend(b, 5);
                     BasicInterpreter f = b.endRoot();

                     b.beginRoot(LANGUAGE);
                         emitAppend(b, 6);
                     BasicInterpreter g = b.endRoot();

                     b.beginInvoke();
                         b.beginConditional();
                             b.emitLoadArgument(4);
                             b.emitLoadConstant(f);
                             b.emitLoadConstant(g);
                         b.endConditional();

                         b.emitLoadArgument(0);
                     b.endInvoke();
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitReturnIf(b, 1, 0);
                     emitAppend(b, 2);
                     emitBranchIf(b, 2, lbl);
                     emitAppend(b, 3);
                     emitThrowIf(b, 3, 123);
                     emitAppend(b, 4);
                 b.endBlock();
             b.endFinallyTry();
             emitAppend(b, 7);
             b.emitLabel(lbl);
             emitAppend(b, 8);
             b.endBlock();
             b.endRoot();

             for (int i = 0; i < 20; i++) {
                 // Create extra root nodes to detect any serialization mismatches
                 b.beginRoot(LANGUAGE);
                     emitThrow(b, -456);
                 b.endRoot();
             }
         });

         testOrderingWithArguments(false, root, new Object[] {false, false, false, false}, 1L, 2L, 3L, 4L, 6L, 7L, 8L);
         testOrderingWithArguments(false, root, new Object[] {false, false, false, true}, 1L, 2L, 3L, 4L, 5L, 7L, 8L);
         testOrderingWithArguments(false, root, new Object[] {true, false, false, false}, 1L, 6L);
         testOrderingWithArguments(false, root, new Object[] {true, false, false, true}, 1L, 5L);
         testOrderingWithArguments(false, root, new Object[] {false, true, false, false}, 1L, 2L, 6L,  8L);
         testOrderingWithArguments(false, root, new Object[] {false, true, false, true}, 1L, 2L, 5L, 8L);
         testOrderingWithArguments(true, root, new Object[] {false, false, true, false}, 1L, 2L, 3L, 6L);
         testOrderingWithArguments(true, root, new Object[] {false, false, true, true}, 1L, 2L, 3L, 5L);
     }

     @Test
     public void testFinallyTryNestedFunctionEscapes() {
         // try {
         //   arg0.append(1);
         //   if (arg1) goto lbl
         //   arg0.append(2);
         // } finally {
         //   def f() { arg0.append(4) }
         //   def g() { arg0.append(5) }
         //   x = if (arg2) f() else g()
         // }
         // arg0.append(3)
         // lbl:
         // x()
         RootCallTarget root = parse("finallyTryNestedFunction", b -> {
             b.beginRoot(LANGUAGE);
             b.beginBlock();
             BytecodeLabel lbl = b.createLabel();
             BytecodeLocal x = b.createLocal();
             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     for (int i = 0; i < 10; i++) {
                         // Create extra root nodes to detect any serialization mismatches
                         b.beginRoot(LANGUAGE);
                             emitThrow(b, -123);
                         b.endRoot();
                     }

                     b.beginRoot(LANGUAGE);
                         emitAppend(b, 4);
                     BasicInterpreter f = b.endRoot();

                     b.beginRoot(LANGUAGE);
                         emitAppend(b, 5);
                     BasicInterpreter g = b.endRoot();

                     b.beginStoreLocal(x);
                         b.beginConditional();
                             b.emitLoadArgument(2);
                             b.emitLoadConstant(f);
                             b.emitLoadConstant(g);
                         b.endConditional();
                     b.endStoreLocal();
                 b.endBlock();
             });
                 b.beginBlock();
                     emitAppend(b, 1);
                     emitBranchIf(b, 1, lbl);
                     emitAppend(b, 2);
                 b.endBlock();
             b.endFinallyTry();
             emitAppend(b, 3);
             b.emitLabel(lbl);
             b.beginInvoke();
             b.emitLoadLocal(x);
             b.emitLoadArgument(0);
             b.endInvoke();
             b.endBlock();
             b.endRoot();

             for (int i = 0; i < 20; i++) {
                 // Create extra root nodes to detect any serialization mismatches
                 b.beginRoot(LANGUAGE);
                     emitThrow(b, -456);
                 b.endRoot();
             }
         });

         testOrderingWithArguments(false, root, new Object[] {false, false}, 1L, 2L, 3L, 5L);
         testOrderingWithArguments(false, root, new Object[] {false, true}, 1L, 2L, 3L, 4L);
         testOrderingWithArguments(false, root, new Object[] {true, false}, 1L, 5L);
         testOrderingWithArguments(false, root, new Object[] {true, true}, 1L, 4L);
     }

     @Test
     public void testFinallyTryCallOuterFunction() {
         // def f() { arg0.append(2); }
         // def g() { arg0.append(3); }
         // try {
         //   arg0.append(1);
         // } finally {
         //   if (arg1) f() else g()
         // }
         RootCallTarget root = parse("finallyTryCallOuterFunction", b -> {
             b.beginRoot(LANGUAGE);

             b.beginRoot(LANGUAGE);
             emitAppend(b, 2);
             BasicInterpreter f = b.endRoot();

             b.beginRoot(LANGUAGE);
             emitAppend(b, 3);
             BasicInterpreter g = b.endRoot();

             b.beginFinallyTry(b.createLocal(), () -> {
                 b.beginBlock();
                     for (int i = 0; i < 10; i++) {
                         // Create extra root nodes to detect any serialization mismatches
                         b.beginRoot(LANGUAGE);
                             emitThrow(b, -123);
                         b.endRoot();
                     }

                     b.beginInvoke();
                         b.beginConditional();
                             b.emitLoadArgument(1);
                             b.emitLoadConstant(f);
                             b.emitLoadConstant(g);
                         b.endConditional();

                         b.emitLoadArgument(0);
                     b.endInvoke();
                 b.endBlock();
             });
                 emitAppend(b, 1);
             b.endFinallyTry();

             b.endRoot();

             for (int i = 0; i < 20; i++) {
                 // Create extra root nodes to detect any serialization mismatches
                 b.beginRoot(LANGUAGE);
                     emitThrow(b, -456);
                 b.endRoot();
             }
         });

         testOrderingWithArguments(false, root, new Object[] {false}, 1L, 3L);
         testOrderingWithArguments(false, root, new Object[] {true}, 1L, 2L);
     }
}
