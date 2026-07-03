package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.util.ArrayList;
import java.util.List;

public class KernelConstantBuilder {

    private final String type, name;
    private final List<Object> lines = new ArrayList<>();

    public KernelConstantBuilder(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public void annotate(String comment) {
        lines.add(comment);
    }

    public void add(float... values) {
        lines.add(values);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        int len = 0;

        for (var line : lines) {
            if (line instanceof float[] values) {
                len += values.length;
            }
        }

        builder.append("const ").append(type).append(" ").append(name).append("[").append(len).append("] = ").append(type).append("[").append(len).append("](");

        boolean needsComma = false;

        for (var line : lines) {
            if (line instanceof String comment) {
                builder.append("  // ").append(comment).append("\n");
            }

            if (line instanceof float[] values) {
                builder.append("  ");

                for (var value : values) {
                    if (needsComma) builder.append(",");

                    builder.append(value).append("f");
                    needsComma = true;
                }

                builder.append("\n");
            }
        }

        builder.append(");");

        return builder.toString();
    }
}
