/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.frame.FrameExtensions;
import com.oracle.truffle.api.memory.ByteArraySupport;

/**
 * Implementation of BytecodeDSLAccess that does not use Unsafe.
 */
final class BytecodeDSLCheckedAccess extends BytecodeDSLAccess {

    BytecodeDSLCheckedAccess() {
    }

    @Override
    public ByteArraySupport getByteArraySupport() {
        return BytecodeAccessor.MEMORY.getNativeChecked();
    }

    @Override
    public FrameExtensions getFrameExtensions() {
        return BytecodeAccessor.RUNTIME.getFrameExtensionsSafe();
    }

    @Override
    public <T> T readObject(T[] arr, int index) {
        return arr[index];
    }

    @Override
    public <T> void writeObject(T[] arr, int index, T value) {
        arr[index] = value;
    }

    @Override
    public <T> T uncheckedCast(Object obj, Class<T> clazz) {
        return clazz.cast(obj);
    }

    // Exposed for testing.

    public static short readShortBigEndian(byte[] arr, int index) {
        return (short) (((arr[index] & 0xFF) << 8) | (arr[index + 1] & 0xFF));
    }

    public static short readShortLittleEndian(byte[] arr, int index) {
        return (short) ((arr[index] & 0xFF) | ((arr[index + 1] & 0xFF) << 8));
    }

    public static int readIntBigEndian(byte[] arr, int index) {
        return ((arr[index] & 0xFF) << 24) | ((arr[index + 1] & 0xFF) << 16) | ((arr[index + 2] & 0xFF) << 8) | (arr[index + 3] & 0xFF);
    }

    public static int readIntLittleEndian(byte[] arr, int index) {
        return (arr[index] & 0xFF) | ((arr[index + 1] & 0xFF) << 8) | ((arr[index + 2] & 0xFF) << 16) | ((arr[index + 3] & 0xFF) << 24);
    }

    public static void writeShortBigEndian(byte[] arr, int index, short value) {
        arr[index] = (byte) (value >> 8);
        arr[index + 1] = (byte) value;
    }

    public static void writeShortLittleEndian(byte[] arr, int index, short value) {
        arr[index] = (byte) value;
        arr[index + 1] = (byte) (value >> 8);
    }

    public static void writeIntBigEndian(byte[] arr, int index, int value) {
        arr[index] = (byte) (value >> 24);
        arr[index + 1] = (byte) (value >> 16);
        arr[index + 2] = (byte) (value >> 8);
        arr[index + 3] = (byte) value;
    }

    public static void writeIntLittleEndian(byte[] arr, int index, int value) {
        arr[index] = (byte) (value);
        arr[index + 1] = (byte) (value >> 8);
        arr[index + 2] = (byte) (value >>> 16);
        arr[index + 3] = (byte) (value >> 24);
    }

}
