/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xenei.bloomfilter.hasher;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.function.ToLongBiFunction;

import org.xenei.bloomfilter.BloomFilter.Shape;
import org.xenei.bloomfilter.HasherFactory.Hasher;

/**
 * The class that performs hashing.
 * <p>
 * Hashers are known by their implementation of the Hasher.Func interface.
 * <ul>
 * <li>Each Hasher.Func has a unique name, and is registered with the Hasher
 * class via the static method {@code Hasher.register( Func func )}.</li>
 * <li>Hashers are retrieved via the static method
 * {@code Hasher.getHasher( String name )}, where name is the well known name of
 * the Hasher.Func.</li>
 * <li>The name of all known Funcs can be listed by calling the static method
 * {@code Hasher.listFuncs()}.</li>
 * <p>
 * The Hasher is guaranteed to have the Funcs defiend in the the
 * {@code org.xenei.bloomfilter.hasher} package registered.
 *
 */
public class DynamicHasher implements Hasher {

    /**
     * Registers a Func implementation. After registration the Func name can be used
     * to retrieve a Hasher.
     * <p>
     *
     * The func calculates the long value that is used to turn on a bit in the Bloom
     * filter. The first argument is a {@code ByteBuffer} containing the bytes to be
     * indexed, the second argument is a seed index.
     * </p>
     * <p>
     * On the first call to {@code applyAsLong} the seed index will be 0 and the
     * func should start the hash sequence.
     * </p>
     * <p>
     * On subsequent calls the hash function using the same buffer the seed index
     * will be incremented. The func should return a different calculated value on
     * each call. The func may use the seed as part of the calculation or simply use
     * it to detect when the buffer has changed.
     * </p>
     * 
     * @see #getHasher(String)
     * @param func the Func to register.
     * @throws SecurityException     if the no argument constructor can not be
     *                               accessed.
     * @throws NoSuchMethodException if func does not have a no argument
     *                               constructor.
     */

    /**
     * The list of ByteBuffers that are to be hashed.
     */
    private final List<ByteBuffer> buffers;

    /**
     * The function to hash the buffers.
     */
    private final ToLongBiFunction<ByteBuffer, Integer> function;

    /**
     * The name of the func.
     */
    private final String name;

    /**
     * True if the hasher is locked.
     */
    private boolean locked;

    /**
     * The constructor
     * 
     * @param function the function to use.
     */
    public DynamicHasher(String name, ToLongBiFunction<ByteBuffer, Integer> function) {
        this.buffers = new ArrayList<ByteBuffer>();
        this.function = function;
        this.name = name;
        this.locked = false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PrimitiveIterator.OfInt getBits(Shape shape) {
        locked = true;
        if (!getName().equals(shape.getHasherName())) {
            throw new IllegalArgumentException(
                    String.format("Shape hasher %s is not %s", shape.getHasherName(), getName()));
        }
        return new Iter(shape);
    }

    /**
     * Adds a ByteBuffer to the hasher.
     * 
     * @param property the ByteBuffer to add.
     * @returns {@code this} for chaining.
     * @throws IllegalStateException if the Hasher is locked.
     * @see #getBits(Shape)
     */
    public final DynamicHasher with(ByteBuffer property) {
        if (locked) {
            throw new IllegalStateException("Attempted to add to a locked Hasher");
        }
        buffers.add(property);
        return this;
    }

    /**
     * Adds a byte to the hasher.
     * 
     * @param property the byte to add
     * @returns {@code this} for chaining.
     * @throws IllegalStateException if the Hasher is locked.
     * @see #getBits(Shape)
     */
    public final DynamicHasher with(byte property) {
        return with(ByteBuffer.wrap(new byte[] { property }));
    }

    /**
     * Adds an array of bytes to the hasher.
     * 
     * @param property the array of bytes to add.
     * @returns {@code this} for chaining.
     * @throws IllegalStateException if the Hasher is locked.
     * @see #getBits(Shape)
     */
    public final DynamicHasher with(byte[] property) {
        return with(ByteBuffer.wrap(property));
    }

    /**
     * Adds a string to the hasher. The string is converted to a byte array using
     * the UTF-8 Character set.
     * 
     * @param property the string to add.
     * @throws IllegalStateException if the Hasher is locked.
     * @see #getBits(Shape)
     */
    public final DynamicHasher with(String property) {
        return with(property.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The iterator of integers.
     */
    private class Iter implements PrimitiveIterator.OfInt {
        private int buffer = 0;
        private int funcCount = 0;
        private final Shape shape;

        /**
         * Creates iterator with the specified shape.
         * 
         * @param shape
         */
        private Iter(Shape shape) {
            this.shape = shape;
        }

        @Override
        public boolean hasNext() {
            return buffer < buffers.size() - 1 || funcCount < shape.getNumberOfHashFunctions();
        }

        @Override
        public int nextInt() {
            if (hasNext()) {
                if (funcCount >= shape.getNumberOfHashFunctions()) {
                    funcCount = 0;
                    buffer++;
                }
                return Math.floorMod(function.applyAsLong(buffers.get(buffer), funcCount++), shape.getNumberOfBits());
            }
            throw new NoSuchElementException();
        }
    }

}