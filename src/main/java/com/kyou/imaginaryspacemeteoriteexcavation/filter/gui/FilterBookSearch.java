package com.kyou.imaginaryspacemeteoriteexcavation.filter.gui;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/** Client-local name search over the server's bounded, read-only ritual directory. */
public final class FilterBookSearch {

    public static final int MAX_RITUALS = 128;
    public static final int MAX_QUERY = 128;
    public static final int ROWS = 6;
    private static final int MAX_ID = 256;
    private List<Entry> directory = List.of();
    private List<Result> matches = List.of();
    private String query = "";
    private int page;
    private long languageVersion = Long.MIN_VALUE;
    private boolean dirty = true;

    public void directory(List<Entry> entries) {
        validate(entries);
        directory = List.copyOf(entries);
        page = 0;
        dirty = true;
    }

    public String query() {
        return query;
    }

    public void query(String text) {
        if (text.length() > MAX_QUERY) throw new IllegalArgumentException("Filtering book query too long");
        if (query.equals(text)) return;
        query = text;
        page = 0;
        dirty = true;
    }

    private void refresh() {
        long currentLanguage = StatCollector.getLastTranslationUpdateTimeInMilliseconds();
        if (!dirty && languageVersion == currentLanguage) return;
        String needle = normalize(query);
        List<Result> found = new ArrayList<>();
        for (int index = 0; index < directory.size(); index++) {
            Entry entry = directory.get(index);
            // Resolve on the client, through the same API as the item tooltip and existing book heading.
            String name = entry.focus.getDisplayName();
            if (normalize(name).contains(needle) || normalize(entry.signature).contains(needle)
                || normalize(entry.id).contains(needle)) found.add(new Result(index, entry, name));
        }
        matches = List.copyOf(found);
        languageVersion = currentLanguage;
        dirty = false;
        page = Math.min(page, Math.max(0, (matches.size() - 1) / ROWS));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(EnumChatFormatting.getTextWithoutFormattingCodes(text), Normalizer.Form.NFKC)
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    public Result result(int row) {
        refresh();
        int index = page * ROWS + row;
        return row < 0 || row >= ROWS || index >= matches.size() ? null : matches.get(index);
    }

    public int count() {
        refresh();
        return matches.size();
    }

    public int page() {
        refresh();
        return page;
    }

    public int pages() {
        refresh();
        return Math.max(1, (matches.size() + ROWS - 1) / ROWS);
    }

    public void move(int direction) {
        refresh();
        if (direction != -1 && direction != 1) return;
        page = Math.max(0, Math.min(pages() - 1, page + direction));
    }

    public static void writeDirectory(PacketBuffer buffer, List<Entry> entries) throws IOException {
        validate(entries);
        buffer.writeInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeStringToBuffer(entry.id);
            buffer.writeStringToBuffer(entry.signature);
            buffer.writeItemStackToBuffer(entry.focus);
            buffer.writeInt(entry.radius);
        }
    }

    public static List<Entry> readDirectory(PacketBuffer buffer) throws IOException {
        int count = buffer.readInt();
        if (count < 0 || count > MAX_RITUALS) throw new IOException("Invalid filtering book directory size");
        List<Entry> entries = new ArrayList<>();
        try {
            for (int index = 0; index < count; index++) {
                entries.add(
                    new Entry(
                        buffer.readStringFromBuffer(MAX_ID),
                        buffer.readStringFromBuffer(MAX_ID),
                        buffer.readItemStackFromBuffer(),
                        buffer.readInt()));
            }
            validate(entries);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid filtering book directory entry", invalid);
        }
        return List.copyOf(entries);
    }

    private static void validate(List<Entry> entries) {
        if (entries.size() > MAX_RITUALS) throw new IllegalArgumentException("Filtering book directory too large");
        Set<String> ids = new HashSet<>();
        for (Entry entry : entries) {
            if (!ids.add(entry.id)) throw new IllegalArgumentException("Duplicate filtering book ritual");
        }
    }

    public record Entry(String id, String signature, ItemStack focus, int radius) {

        public Entry {
            if (id == null || id.isEmpty()
                || id.length() > MAX_ID
                || signature == null
                || signature.isEmpty()
                || signature.length() > MAX_ID
                || focus == null
                || focus.getItem() == null
                || focus.stackSize != 1
                || focus.hasTagCompound()
                || radius <= 0) throw new IllegalArgumentException("Invalid filtering book ritual header");
            focus = focus.copy();
        }

        @Override
        public ItemStack focus() {
            return focus.copy();
        }
    }

    public record Result(int index, Entry entry, String name) {}
}
