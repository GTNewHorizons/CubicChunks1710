package com.cardinalstar.cubicchunks.codegen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Struct {

    public final String pkg, name;

    public final List<StructField> fields = new ArrayList<>();
    /** Total word count across all fields (Java IntBuffer stride). */
    public int stride = 0;
    /** Total byte size with std430 alignment padding. */
    public int glByteStride = 0;

    public Struct(String pkg, String name) {
        this.pkg = pkg;
        this.name = name;
    }

    public Struct addField(FieldType type, String name) {
        int glByteOffset = ((glByteStride + type.glAlign() - 1) / type.glAlign()) * type.glAlign();
        fields.add(new StructField(type, name, stride, glByteOffset));
        stride += type.wordWidth();
        glByteStride = glByteOffset + type.byteSize();
        return this;
    }

    public void writeAllGL() throws IOException {
        writePrimitiveBuffer();
        writePrimitiveView();
        writeGLStruct();
    }

    public void writePrimitiveBuffer() throws IOException {
        StringBuilder code = new StringBuilder();

        String viewName = this.name + "PrimitiveView";
        String bufferName = this.name + "PrimitiveBuffer";

        code.append("package ")
            .append(pkg)
            .append(";\n");
        code.append("\n");
        code.append("import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.PrimitiveBuffer;\n");
        code.append("import com.cardinalstar.cubicchunks.util.ObjectPooler;\n");
        code.append("import gnu.trove.procedure.TIntObjectProcedure;\n");
        code.append("\n");
        code.append("import java.nio.ByteBuffer;\n");
        code.append("import java.nio.ByteOrder;\n");
        code.append("import java.nio.IntBuffer;\n");
        code.append("import java.util.Arrays;\n");
        code.append("import java.util.Iterator;\n");
        code.append("import java.util.NoSuchElementException;\n");
        code.append("\n");
        code.append("public class ")
            .append(bufferName)
            .append(" implements PrimitiveBuffer<")
            .append(viewName)
            .append("> {\n");
        code.append("\n");
        code.append("    public static final int INT_STRIDE = ")
            .append(this.stride)
            .append(";\n");
        code.append("\n");
        code.append("    private final byte[] rawData;\n");
        code.append("    private final ByteBuffer bytes;\n");
        code.append("    private final IntBuffer data;\n");
        code.append("    private final int size;\n");
        code.append("    private final ObjectPooler<")
            .append(viewName)
            .append("> pool;\n");
        code.append("\n");
        code.append("    public ")
            .append(bufferName)
            .append("(int size) {\n");
        code.append("        this.size = size;\n");
        code.append("        this.rawData = new byte[size * INT_STRIDE * 4];\n");
        code.append("        this.bytes = ByteBuffer.wrap(this.rawData).order(ByteOrder.nativeOrder());\n");
        code.append("        this.data = this.bytes.asIntBuffer();\n");
        code.append("        this.pool = new ObjectPooler<>(this::newView);\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    private ")
            .append(viewName)
            .append(" newView() {\n");
        code.append("        return new ")
            .append(viewName)
            .append("(pool, this.data);\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public void clear() {\n");
        code.append("        Arrays.fill(this.rawData, (byte) 0);\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public int size() {\n");
        code.append("        return this.size;\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public int getByteLength() {\n");
        code.append("        return this.size * INT_STRIDE * 4;\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public ")
            .append(viewName)
            .append(" get(int index) {\n");
        code.append("        ")
            .append(viewName)
            .append(" view = this.pool.getInstance();\n");
        code.append("        view.initialize(index);\n");
        code.append("        return view;\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public void forEachSlow(TIntObjectProcedure<")
            .append(viewName)
            .append("> fn) {\n");
        code.append("        for (int i = 0; i < this.size; i++) {\n");
        code.append("            ")
            .append(viewName)
            .append(" view = new ")
            .append(viewName)
            .append("(null, this.data);\n");
        code.append("            view.initialize(i);\n");
        code.append("            fn.execute(i, view);\n");
        code.append("        }\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public void forEachFast(TIntObjectProcedure<")
            .append(viewName)
            .append("> fn) {\n");
        code.append("        ")
            .append(viewName)
            .append(" view = new ")
            .append(viewName)
            .append("(null, this.data);\n");
        code.append("        for (int i = 0; i < this.size; i++) {\n");
        code.append("            view.initialize(i);\n");
        code.append("            fn.execute(i, view);\n");
        code.append("        }\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public Iterator<")
            .append(viewName)
            .append("> iteratorSlow() {\n");
        code.append("        return new Iterator<")
            .append(viewName)
            .append(">() {\n");
        code.append("            private int i = 0;\n");
        code.append("\n");
        code.append("            @Override\n");
        code.append("            public boolean hasNext() {\n");
        code.append("                return i < size;\n");
        code.append("            }\n");
        code.append("\n");
        code.append("            @Override\n");
        code.append("            public ")
            .append(viewName)
            .append(" next() {\n");
        code.append("                if (!hasNext()) throw new NoSuchElementException();\n");
        code.append("                ")
            .append(viewName)
            .append(" view = new ")
            .append(viewName)
            .append("(null, data);\n");
        code.append("                view.initialize(i++);\n");
        code.append("                return view;\n");
        code.append("            }\n");
        code.append("        };\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public Iterator<")
            .append(viewName)
            .append("> iteratorFast() {\n");
        code.append("        return new Iterator<")
            .append(viewName)
            .append(">() {\n");
        code.append("            private int i = 0;\n");
        code.append("            private final ")
            .append(viewName)
            .append(" view = new ")
            .append(viewName)
            .append("(null, data);\n");
        code.append("\n");
        code.append("            @Override\n");
        code.append("            public boolean hasNext() {\n");
        code.append("                return i < size;\n");
        code.append("            }\n");
        code.append("\n");
        code.append("            @Override\n");
        code.append("            public ")
            .append(viewName)
            .append(" next() {\n");
        code.append("                if (!hasNext()) throw new NoSuchElementException();\n");
        code.append("                view.initialize(i++);\n");
        code.append("                return view;\n");
        code.append("            }\n");
        code.append("        };\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public void upload(ByteBuffer dest) {\n");
        code.append("        if (dest.remaining() != this.rawData.length) {\n");
        code.append(
            "            throw new IllegalArgumentException(\"Byte length mismatch: expected \" + this.rawData.length + \" but got \" + dest.remaining());\n");
        code.append("        }\n");
        code.append("        dest.put(this.rawData, 0, this.rawData.length);\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public void download(ByteBuffer source) {\n");
        code.append("        if (source.remaining() != this.rawData.length) {\n");
        code.append(
            "            throw new IllegalArgumentException(\"Byte length mismatch: expected \" + this.rawData.length + \" but got \" + source.remaining());\n");
        code.append("        }\n");
        code.append("        source.get(this.rawData, 0, this.rawData.length);\n");
        code.append("    }\n");
        code.append("\n");
        code.append("}\n");

        Path src = Paths.get("build", "generated", "sources", "structs")
            .toAbsolutePath();
        String[] pkg = this.pkg.split("\\.");

        Path next = Paths.get(pkg[0], Arrays.copyOfRange(pkg, 1, pkg.length));

        Files.createDirectories(src.resolve(next));

        Path file = Paths.get(name + "PrimitiveBuffer.java");

        Files.write(
            src.resolve(next)
                .resolve(file),
            code.toString()
                .getBytes(StandardCharsets.UTF_8));
    }

    public void writePrimitiveView() throws IOException {
        StringBuilder code = new StringBuilder();

        String name = this.name + "PrimitiveView";

        code.append("package ")
            .append(pkg)
            .append(";\n");
        code.append("\n");
        code.append("import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.PrimitiveView;\n");
        code.append("import com.cardinalstar.cubicchunks.util.ObjectPooler;\n");
        code.append("\n");
        code.append("import java.nio.IntBuffer;\n");
        code.append("@SuppressWarnings({ \"UnnecessaryLocalVariable\", \"PointlessArithmeticExpression\" })\n");
        code.append("public final class ")
            .append(name)
            .append(" implements PrimitiveView {\n");
        code.append("    private int index;\n");
        code.append("    private final ObjectPooler<")
            .append(name)
            .append("> pool;\n");
        code.append("    private boolean alive;\n");
        code.append("    private final IntBuffer data;\n");
        code.append("\n");
        code.append("    ")
            .append(name)
            .append("(ObjectPooler<")
            .append(name)
            .append("> pool, IntBuffer data) {\n");
        code.append("        this.pool = pool;\n");
        code.append("        this.data = data;\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public void close() {\n");
        code.append("        if (this.pool != null) {\n");
        code.append("            this.pool.releaseInstance(this);\n");
        code.append("        }\n");
        code.append("        \n");
        code.append("        this.alive = false;\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    @Override\n");
        code.append("    public int getIndex() {\n");
        code.append("        return this.index;\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    private void assertAlive() {\n");
        code.append("        if (!this.alive) throw new IllegalStateException(this + \" is not alive\");\n");
        code.append("    }\n");
        code.append("\n");
        code.append("    void initialize(int index) {\n");
        code.append("        this.alive = true;\n");
        code.append("        this.index = index;\n");
        code.append("    }\n");
        code.append("\n");

        for (StructField field : this.fields) {
            code.append("    public ")
                .append(field.type.javaType())
                .append(" get")
                .append(field.getPascalCase())
                .append("() {\n");
            code.append("        assertAlive();\n");

            if (field.type.wordWidth() == 2) {
                code.append("        long lower = this.data.get(index * ")
                    .append(stride)
                    .append(" + ")
                    .append(field.wordOffset)
                    .append(");\n");
                code.append("        long upper = this.data.get(index * ")
                    .append(stride)
                    .append(" + ")
                    .append(field.wordOffset + 1)
                    .append(");\n");
                code.append("        long bits = lower | (upper << 32);\n");

                if (field.type == FieldType.f64) {
                    code.append("        return Double.longBitsToDouble(bits);\n");
                } else {
                    code.append("        return bits;\n");
                }
            } else {
                code.append("        int bits = this.data.get(index * ")
                    .append(stride)
                    .append(" + ")
                    .append(field.wordOffset)
                    .append(");\n");

                if (field.type == FieldType.f32) {
                    code.append("        return Float.intBitsToFloat(bits);\n");
                } else {
                    code.append("        return bits;\n");
                }
            }

            code.append("    }\n");
            code.append("\n");

            code.append("    public ")
                .append(name)
                .append(" set")
                .append(field.getPascalCase())
                .append("(")
                .append(field.type.javaType())
                .append(" value) {\n");
            code.append("        assertAlive();\n");

            if (field.type.wordWidth() == 2) {
                if (field.type == FieldType.f64) {
                    code.append("        long bits = Double.doubleToLongBits(value);\n");
                } else {
                    code.append("        long bits = value;\n");
                }

                code.append("        this.data.put(index * ")
                    .append(stride)
                    .append(" + ")
                    .append(field.wordOffset)
                    .append(", (int) bits);\n");
                code.append("        this.data.put(index * ")
                    .append(stride)
                    .append(" + ")
                    .append(field.wordOffset + 1)
                    .append(", (int) (bits >> 32));\n");
            } else {
                if (field.type == FieldType.f32) {
                    code.append("        int bits = Float.floatToIntBits(value);\n");
                } else {
                    code.append("        int bits = value;\n");
                }

                code.append("        this.data.put(index * ")
                    .append(stride)
                    .append(" + ")
                    .append(field.wordOffset)
                    .append(", bits);\n");
            }

            code.append("        return this;\n");
            code.append("    }\n");
            code.append("\n");
        }

        code.append("}\n");

        Path src = Paths.get("build", "generated", "sources", "structs")
            .toAbsolutePath();
        String[] pkg = this.pkg.split("\\.");

        Path next = Paths.get(pkg[0], Arrays.copyOfRange(pkg, 1, pkg.length));

        Files.createDirectories(src.resolve(next));

        Path file = Paths.get(name + ".java");

        Files.write(
            src.resolve(next)
                .resolve(file),
            code.toString()
                .getBytes(StandardCharsets.UTF_8));
    }

    public void writeGLStruct() throws IOException {
        String glStructName = this.name + "GLStruct";

        // Build GLSL struct, inserting uint padding fields where std430 alignment
        // would create a gap vs the Java sequential (word-packed) layout.
        StringBuilder glsl = new StringBuilder();
        glsl.append("struct ")
            .append(this.name)
            .append(" {\n");

        int cursor = 0;
        int padCount = 0;
        for (StructField field : this.fields) {
            int padBytes = field.glByteOffset - cursor;
            for (int i = 0; i < padBytes / 4; i++) {
                glsl.append("    uint _pad")
                    .append(padCount++)
                    .append(";\n");
            }
            glsl.append("    ")
                .append(field.type.glslType())
                .append(" ")
                .append(field.name)
                .append(";\n");
            cursor = field.glByteOffset + field.type.byteSize();
        }

        glsl.append("};\n");

        // Wrap in a Java class with a text block SOURCE field.
        // Indent content by 12 spaces so the closing """ at 12 spaces strips it cleanly.
        StringBuilder code = new StringBuilder();
        code.append("package ")
            .append(pkg)
            .append(";\n");
        code.append("\n");
        code.append("public final class ")
            .append(glStructName)
            .append(" {\n");
        code.append("    public static final String SOURCE = \"\"\"\n");

        for (String line : glsl.toString()
            .split("\n")) {
            code.append("            ")
                .append(line)
                .append("\n");
        }

        code.append("            \"\"\";\n");
        code.append("}\n");

        Path src = Paths.get("build", "generated", "sources", "structs")
            .toAbsolutePath();
        String[] pkg = this.pkg.split("\\.");
        Path next = Paths.get(pkg[0], Arrays.copyOfRange(pkg, 1, pkg.length));
        Files.createDirectories(src.resolve(next));
        Files.write(
            src.resolve(next)
                .resolve(Paths.get(glStructName + ".java")),
            code.toString()
                .getBytes(StandardCharsets.UTF_8));
    }
}
