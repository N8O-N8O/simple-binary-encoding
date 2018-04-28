/*
 * Copyright 2013-2018 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.sbe.generation.java;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.generation.CompilerUtil;
import org.agrona.generation.StringWriterOutputManager;
import org.junit.Before;
import org.junit.Test;
import uk.co.real_logic.sbe.TestUtil;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.xml.IrGenerator;
import uk.co.real_logic.sbe.xml.MessageSchema;
import uk.co.real_logic.sbe.xml.ParserOptions;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static uk.co.real_logic.sbe.generation.java.ReflectionUtil.get;
import static uk.co.real_logic.sbe.generation.java.ReflectionUtil.set;
import static uk.co.real_logic.sbe.xml.XmlSchemaParser.parse;

public class SchemaExtensionTest
{
    private static final Class<?> BUFFER_CLASS = MutableDirectBuffer.class;
    private static final String BUFFER_NAME = BUFFER_CLASS.getName();
    private static final Class<DirectBuffer> READ_ONLY_BUFFER_CLASS = DirectBuffer.class;
    private static final String READ_ONLY_BUFFER_NAME = READ_ONLY_BUFFER_CLASS.getName();

    private final StringWriterOutputManager outputManager = new StringWriterOutputManager();

    private Ir ir;

    @Before
    public void setup() throws Exception
    {
        final ParserOptions options = ParserOptions.builder().stopOnError(true).build();
        final MessageSchema schema = parse(TestUtil.getLocalResource("extension-schema.xml"), options);
        final IrGenerator irg = new IrGenerator();
        ir = irg.generate(schema);

        outputManager.clear();
        outputManager.setPackageName(ir.applicableNamespace());

        generator().generate();
    }

    @Test
    public void testMessage1() throws Exception
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[4096]);

        { // Encode
            final Object encoder = wrap(buffer, compile("TestMessage1Encoder").getConstructor().newInstance());

            set(encoder, "tag1", int.class, 100);
            set(encoder, "tag2", int.class, 200);

            final Object compositeEncoder = encoder.getClass().getMethod("tag3").invoke(encoder);
            set(compositeEncoder, "value", int.class, 300);

            final Object enumConstant = getAEnumConstant(encoder, "AEnum", 1);
            set(encoder, "tag4", enumConstant.getClass(), enumConstant);

            final Object setEncoder = encoder.getClass().getMethod("tag5").invoke(encoder);
            set(setEncoder, "firstChoice", boolean.class, false);
            set(setEncoder, "secondChoice", boolean.class, true);
        }

        { // Decode version 0
            final Object decoderVersion0 = getMessage1Decoder(buffer, 4, 0);
            assertEquals(100, get(decoderVersion0, "tag1"));
            assertEquals(Integer.MIN_VALUE, get(decoderVersion0, "tag2"));
            assertNull(get(decoderVersion0, "tag3"));
            assertThat(get(decoderVersion0, "tag4").toString(), is("NULL_VAL"));
            assertNull(get(decoderVersion0, "tag5"));

            assertEquals(0, decoderVersion0.getClass().getMethod("tag1SinceVersion").invoke(null));
            assertEquals(1, decoderVersion0.getClass().getMethod("tag2SinceVersion").invoke(null));
            assertEquals(2, decoderVersion0.getClass().getMethod("tag3SinceVersion").invoke(null));
            assertEquals(3, decoderVersion0.getClass().getMethod("tag4SinceVersion").invoke(null));
            assertEquals(4, decoderVersion0.getClass().getMethod("tag5SinceVersion").invoke(null));
        }

        { // Decode version 1
            final Object decoderVersion1 = getMessage1Decoder(buffer, 8, 1);
            assertEquals(100, get(decoderVersion1, "tag1"));
            assertEquals(200, get(decoderVersion1, "tag2"));
            assertNull(get(decoderVersion1, "tag3"));
            assertThat(get(decoderVersion1, "tag4").toString(), is("NULL_VAL"));
            assertNull(get(decoderVersion1, "tag5"));
        }

        { // Decode version 2
            final Object decoderVersion2 = getMessage1Decoder(buffer, 8, 2);
            assertEquals(100, get(decoderVersion2, "tag1"));
            assertEquals(200, get(decoderVersion2, "tag2"));
            final Object compositeDecoder2 = get(decoderVersion2, "tag3");
            assertNotNull(compositeDecoder2);
            assertEquals(300, get(compositeDecoder2, "value"));
            assertThat(get(decoderVersion2, "tag4").toString(), is("NULL_VAL"));
            assertNull(get(decoderVersion2, "tag5"));
        }

        { // Decode version 3
            final Object decoderVersion3 = getMessage1Decoder(buffer, 12, 3);
            assertEquals(100, get(decoderVersion3, "tag1"));
            assertEquals(200, get(decoderVersion3, "tag2"));
            final Object compositeDecoder3 = get(decoderVersion3, "tag3");
            assertNotNull(compositeDecoder3);
            assertEquals(300, get(compositeDecoder3, "value"));
            final Object enumConstant = getAEnumConstant(decoderVersion3, "AEnum", 1);
            assertEquals(enumConstant, get(decoderVersion3, "tag4"));
            assertNull(get(decoderVersion3, "tag5"));
        }

        { // Decode version 4
            final Object decoderVersion4 = getMessage1Decoder(buffer, 12, 4);
            assertEquals(100, get(decoderVersion4, "tag1"));
            assertEquals(200, get(decoderVersion4, "tag2"));
            final Object compositeDecoder4 = get(decoderVersion4, "tag3");
            assertNotNull(compositeDecoder4);
            assertEquals(300, get(compositeDecoder4, "value"));
            final Object enumConstant = getAEnumConstant(decoderVersion4, "AEnum", 1);
            assertEquals(enumConstant, get(decoderVersion4, "tag4"));
            final Object setDecoder = get(decoderVersion4, "tag5");
            assertNotNull(setDecoder);
            assertEquals(false, get(setDecoder, "firstChoice"));
            assertEquals(true, get(setDecoder, "secondChoice"));
        }
    }

    @Test
    public void testMessage2() throws Exception
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[4096]);

        { // Encode
            final Object encoder = wrap(buffer, compile("TestMessage2Encoder").getConstructor().newInstance());

            set(encoder, "tag1", int.class, 100);
            set(encoder, "tag2", int.class, 200);

            final Object compositeEncoder = encoder.getClass().getMethod("tag3").invoke(encoder);
            set(compositeEncoder, "value", int.class, 300);

            final Object enumConstant = getAEnumConstant(encoder, "AEnum", 1);
            set(encoder, "tag4", enumConstant.getClass(), enumConstant);

            final Object setEncoder = encoder.getClass().getMethod("tag5").invoke(encoder);
            set(setEncoder, "firstChoice", boolean.class, false);
            set(setEncoder, "secondChoice", boolean.class, true);
        }

        { // Decode version 0
            final Object decoderVersion0 = getMessage2Decoder(buffer, 4, 0);
            assertEquals(100, get(decoderVersion0, "tag1"));
            assertEquals(Integer.MIN_VALUE, get(decoderVersion0, "tag2"));
            assertNull(get(decoderVersion0, "tag3"));
            assertThat(get(decoderVersion0, "tag4").toString(), is("NULL_VAL"));
            assertNull(get(decoderVersion0, "tag5"));

            assertEquals(0, decoderVersion0.getClass().getMethod("tag1SinceVersion").invoke(null));
            assertEquals(2, decoderVersion0.getClass().getMethod("tag2SinceVersion").invoke(null));
            assertEquals(1, decoderVersion0.getClass().getMethod("tag3SinceVersion").invoke(null));
            assertEquals(4, decoderVersion0.getClass().getMethod("tag4SinceVersion").invoke(null));
            assertEquals(3, decoderVersion0.getClass().getMethod("tag5SinceVersion").invoke(null));
        }

        { // Decode version 1
            final Object decoderVersion1 = getMessage2Decoder(buffer, 8, 1);
            assertEquals(100, get(decoderVersion1, "tag1"));
            assertEquals(Integer.MIN_VALUE, get(decoderVersion1, "tag2"));
            final Object compositeDecoder2 = get(decoderVersion1, "tag3");
            assertNotNull(compositeDecoder2);
            assertEquals(300, get(compositeDecoder2, "value"));
            assertThat(get(decoderVersion1, "tag4").toString(), is("NULL_VAL"));
            assertNull(get(decoderVersion1, "tag5"));
        }

        { // Decode version 2
            final Object decoderVersion2 = getMessage2Decoder(buffer, 8, 2);
            assertEquals(100, get(decoderVersion2, "tag1"));
            assertEquals(200, get(decoderVersion2, "tag2"));
            final Object compositeDecoder2 = get(decoderVersion2, "tag3");
            assertNotNull(compositeDecoder2);
            assertEquals(300, get(compositeDecoder2, "value"));
            assertThat(get(decoderVersion2, "tag4").toString(), is("NULL_VAL"));
            assertNull(get(decoderVersion2, "tag5"));
        }

        { // Decode version 3
            final Object decoderVersion3 = getMessage2Decoder(buffer, 12, 3);
            assertEquals(100, get(decoderVersion3, "tag1"));
            assertEquals(200, get(decoderVersion3, "tag2"));
            final Object compositeDecoder3 = get(decoderVersion3, "tag3");
            assertNotNull(compositeDecoder3);
            assertEquals(300, get(compositeDecoder3, "value"));
            assertThat(get(decoderVersion3, "tag4").toString(), is("NULL_VAL"));
            final Object setDecoder = get(decoderVersion3, "tag5");
            assertNotNull(setDecoder);
            assertEquals(false, get(setDecoder, "firstChoice"));
            assertEquals(true, get(setDecoder, "secondChoice"));
        }

        { // Decode version 4
            final Object decoderVersion4 = getMessage2Decoder(buffer, 12, 4);
            assertEquals(100, get(decoderVersion4, "tag1"));
            assertEquals(200, get(decoderVersion4, "tag2"));
            final Object compositeDecoder4 = get(decoderVersion4, "tag3");
            assertNotNull(compositeDecoder4);
            assertEquals(300, get(compositeDecoder4, "value"));
            final Object enumConstant = getAEnumConstant(decoderVersion4, "AEnum", 1);
            assertEquals(enumConstant, get(decoderVersion4, "tag4"));
            final Object setDecoder = get(decoderVersion4, "tag5");
            assertNotNull(setDecoder);
            assertEquals(false, get(setDecoder, "firstChoice"));
            assertEquals(true, get(setDecoder, "secondChoice"));
        }
    }

    private JavaGenerator generator()
    {
        return new JavaGenerator(ir, BUFFER_NAME, READ_ONLY_BUFFER_NAME, false, false, false, outputManager);
    }

    private Object getMessage1Decoder(final UnsafeBuffer buffer, final int blockLength, final int version)
        throws Exception
    {
        final Object decoder = compile("TestMessage1Decoder").getConstructor().newInstance();
        return wrap(buffer, decoder, blockLength, version);
    }

    private Object getMessage2Decoder(final UnsafeBuffer buffer, final int blockLength, final int version)
        throws Exception
    {
        final Object decoder = compile("TestMessage2Decoder").getConstructor().newInstance();
        return wrap(buffer, decoder, blockLength, version);
    }

    private Object getAEnumConstant(
        final Object flyweight, final String enumClassName, final int constantIndex) throws Exception
    {
        final String fqClassName = ir.applicableNamespace() + "." + enumClassName;
        return flyweight.getClass().getClassLoader().loadClass(fqClassName).getEnumConstants()[constantIndex];
    }

    private Class<?> compile(final String className) throws Exception
    {
        final String fqClassName = ir.applicableNamespace() + "." + className;
        final Map<String, CharSequence> sources = outputManager.getSources();
        final Class<?> aClass = CompilerUtil.compileInMemory(fqClassName, sources);
        if (aClass == null)
        {
            System.out.println(sources);
        }
        assertNotNull(aClass);

        return aClass;
    }

    private static Object wrap(
        final UnsafeBuffer buffer, final Object decoder, final int blockLength, final int version) throws Exception
    {
        return wrap(buffer, decoder, blockLength, version, READ_ONLY_BUFFER_CLASS);
    }

    private static Object wrap(
        final UnsafeBuffer buffer,
        final Object decoder,
        final int blockLength,
        final int version,
        final Class<?> bufferClass) throws Exception
    {
        decoder
            .getClass()
            .getMethod("wrap", bufferClass, int.class, int.class, int.class)
            .invoke(decoder, buffer, 0, blockLength, version);

        return decoder;
    }

    private static void wrap(
        final int bufferOffset, final Object flyweight, final MutableDirectBuffer buffer, final Class<?> bufferClass)
        throws Exception
    {
        flyweight
            .getClass()
            .getDeclaredMethod("wrap", bufferClass, int.class)
            .invoke(flyweight, buffer, bufferOffset);
    }

    private static Object wrap(final UnsafeBuffer buffer, final Object encoder) throws Exception
    {
        wrap(0, encoder, buffer, BUFFER_CLASS);

        return encoder;
    }
}
