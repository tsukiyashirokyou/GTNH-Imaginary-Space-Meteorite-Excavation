package com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;

/** Stable typed NBT encoding, with sorted compound keys and full array content. */
public final class CanonicalNbt {

    private CanonicalNbt() {}

    public static String encode(NBTBase value) {
        if (value == null) return "absent";
        StringBuilder result = new StringBuilder();
        // Public list removal operates on this detached copy, never on a registry or caller's NBT.
        append(result, value.copy(), 0);
        return result.toString();
    }

    private static void append(StringBuilder result, NBTBase value, int depth) {
        if (depth > 512) throw new IllegalArgumentException("NBT exceeds native depth limit");
        result.append(value.getId())
            .append(':');
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) value;
            List<String> keys = new ArrayList<>(compound.func_150296_c());
            Collections.sort(keys);
            result.append(keys.size())
                .append('{');
            for (String key : keys) {
                text(result, key);
                append(result, compound.getTag(key), depth + 1);
            }
            result.append('}');
        } else if (value instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) value;
            result.append(list.func_150303_d())
                .append(':')
                .append(list.tagCount())
                .append('[');
            // Reverse traversal is still order-sensitive, and removal from the tail is O(1).
            while (list.tagCount() > 0) append(result, list.removeTag(list.tagCount() - 1), depth + 1);
            result.append(']');
        } else if (value instanceof NBTTagByteArray) {
            text(
                result,
                Base64.getEncoder()
                    .encodeToString(((NBTTagByteArray) value).func_150292_c()));
        } else if (value instanceof NBTTagIntArray) {
            text(result, Arrays.toString(((NBTTagIntArray) value).func_150302_c()));
        } else {
            text(result, value.toString());
        }
    }

    private static void text(StringBuilder result, String value) {
        result.append(value.length())
            .append(':')
            .append(value);
    }
}
