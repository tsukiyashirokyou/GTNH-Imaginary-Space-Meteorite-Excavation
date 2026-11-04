package com.kyou.imaginaryspacemeteoriteexcavation.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/** Versioned owned tag only; reading does not modify an item or materialize default pages. */
public final class FilterBookCodec {

    public static final String KEY = "ismeFilterBook";
    public static final int SCHEMA = 1;
    public static final int MAX_PAGES = 128;
    public static final int MAX_ITEMS = 256;
    public static final int MAX_IDENTITY_LENGTH = 8192;
    public static final int MAX_TEXT = 262144;

    private FilterBookCodec() {}

    public static FilterBookState read(NBTTagCompound itemTag) {
        if (itemTag == null || !itemTag.hasKey(KEY)) return FilterBookState.defaults();
        if (!itemTag.hasKey(KEY, 10)) throw invalid();
        NBTTagCompound root = itemTag.getCompoundTag(KEY);
        if (!root.hasKey("schema", 3) || root.getInteger("schema") != SCHEMA || !root.hasKey("revision", 4))
            throw invalid();
        NBTTagList pages = list(root, "pages", 10, MAX_PAGES);
        Map<String, FilterPage> decoded = new LinkedHashMap<>();
        int text = 0;
        for (int index = 0; index < pages.tagCount(); index++) {
            NBTTagCompound page = pages.getCompoundTagAt(index);
            String id = string(page, "id", 160);
            String focus = string(page, "focus", 512);
            String hash = string(page, "hash", 128);
            String mode = string(page, "mode", 16);
            NBTTagList entries = list(page, "entries", 8, MAX_ITEMS);
            List<String> selected = new ArrayList<>();
            text = Math.addExact(text, id.length() + focus.length() + hash.length());
            for (int item = 0; item < entries.tagCount(); item++) {
                String identity = entries.getStringTagAt(item);
                if (identity.isEmpty() || identity.length() > MAX_IDENTITY_LENGTH) throw invalid();
                selected.add(identity);
                text = Math.addExact(text, identity.length());
            }
            if (text > MAX_TEXT
                || decoded.put(id, new FilterPage(focus, hash, FilterPage.Mode.valueOf(mode), selected)) != null)
                throw invalid();
        }
        return new FilterBookState(true, root.getLong("revision"), decoded);
    }

    public static NBTTagCompound write(FilterBookState state, NBTTagCompound oldItemTag) {
        NBTTagCompound tag = oldItemTag == null ? new NBTTagCompound() : (NBTTagCompound) oldItemTag.copy();
        if (!state.configured()) {
            tag.removeTag(KEY);
            return tag;
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("schema", SCHEMA);
        root.setLong("revision", state.revision());
        NBTTagList pages = new NBTTagList();
        for (Map.Entry<String, FilterPage> entry : state.pages()
            .entrySet()) {
            FilterPage value = entry.getValue();
            NBTTagCompound page = new NBTTagCompound();
            page.setString("id", entry.getKey());
            page.setString("focus", value.focus());
            page.setString("hash", value.hash());
            page.setString(
                "mode",
                value.mode()
                    .name());
            NBTTagList entries = new NBTTagList();
            for (String item : value.selected()) entries.appendTag(new NBTTagString(item));
            page.setTag("entries", entries);
            pages.appendTag(page);
        }
        root.setTag("pages", pages);
        tag.setTag(KEY, root);
        // Enforce exactly the same limits on GUI writes and on imported NBT.
        read(tag);
        return tag;
    }

    private static String string(NBTTagCompound tag, String key, int maximum) {
        if (!tag.hasKey(key, 8)) throw invalid();
        String value = tag.getString(key);
        if (value.isEmpty() || value.length() > maximum) throw invalid();
        return value;
    }

    private static NBTTagList list(NBTTagCompound tag, String key, int type, int maximum) {
        NBTBase value = tag.getTag(key);
        if (!(value instanceof NBTTagList)) throw invalid();
        NBTTagList list = (NBTTagList) value;
        if (list.tagCount() > maximum || (list.tagCount() > 0 && list.func_150303_d() != type)) throw invalid();
        return list;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid or unsupported filtering book data");
    }
}
