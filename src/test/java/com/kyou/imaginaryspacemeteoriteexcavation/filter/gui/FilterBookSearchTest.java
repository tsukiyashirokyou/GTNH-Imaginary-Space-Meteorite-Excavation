package com.kyou.imaginaryspacemeteoriteexcavation.filter.gui;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StringTranslate;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;

import io.netty.buffer.Unpooled;

/** Real fixed-version AE2 language resources and ItemStack names; no World, player or client is constructed. */
public final class FilterBookSearchTest {

    private static Item chest;

    @BeforeClass
    public static void registerTestFixtures() throws IOException {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        // Only the native translation keys are supplied by this fixture; all names come from AE2's own resources.
        chest = new Item() {

            @Override
            public String getUnlocalizedName(ItemStack stack) {
                return stack.getItemDamage() == 1 ? "tile.appliedenergistics2.BlockSkyChest.Block"
                    : "tile.appliedenergistics2.BlockSkyChest";
            }
        }.setHasSubtypes(true);
        Item.itemRegistry.addObject(30010, "isme_search_test:chest", chest);
        language("zh_CN");
    }

    private boolean delegated(String method) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        HeadlessForgeFixture.run(getClass().getName(), method);
        return true;
    }

    private static void language(String code) throws IOException {
        try (InputStream input = FilterBookSearchTest.class
            .getResourceAsStream("/assets/appliedenergistics2/lang/" + code + ".lang")) {
            assertNotNull("Missing fixed-version native AE2 language resource", input);
            StringTranslate.inject(input);
        }
    }

    private static FilterBookSearch.Entry entry(String id, int metadata) {
        return new FilterBookSearch.Entry(
            id,
            "appliedenergistics2:tile.BlockSkyChest:" + metadata,
            new ItemStack(chest, 1, metadata),
            3);
    }

    private static FilterBookSearch search() {
        FilterBookSearch search = new FilterBookSearch();
        search.directory(List.of(entry("pack:ritual/stone", 0), entry("pack:ritual/block", 1)));
        return search;
    }

    @Test
    public void nativeChineseNamesAndPartialQueriesSelectTheCorrectMetadata() throws Exception {
        if (delegated("nativeChineseNamesAndPartialQueriesSelectTheCorrectMetadata")) return;
        FilterBookSearch search = search();
        assertEquals(
            "陨石箱子",
            search.result(0)
                .name());
        assertEquals(
            "陨石块箱子",
            search.result(1)
                .name());
        search.query("陨石块箱子");
        assertEquals(1, search.count());
        assertEquals(
            1,
            search.result(0)
                .index());
        assertEquals(
            1,
            search.result(0)
                .entry()
                .focus()
                .getItemDamage());
        search.query("陨石");
        assertEquals(2, search.count());
        search.query("天石箱");
        assertEquals(0, search.count());
        assertNull(search.result(0));
    }

    @Test
    public void serverDirectoryCarriesNoTranslatedNameAndClientUsesItsOwnLanguage() throws Exception {
        if (delegated("serverDirectoryCarriesNoTranslatedNameAndClientUsesItsOwnLanguage")) return;
        language("en_US");
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        FilterBookSearch.writeDirectory(packet, List.of(entry("pack:ritual/block", 1)));
        language("zh_CN");
        FilterBookSearch search = new FilterBookSearch();
        search.directory(FilterBookSearch.readDirectory(packet));
        search.query("陨石块");
        assertEquals(1, search.count());
        assertEquals(
            "陨石块箱子",
            search.result(0)
                .name());
        assertEquals(
            "pack:ritual/block",
            search.result(0)
                .entry()
                .id());
        assertEquals(0, packet.readableBytes());
    }

    @Test
    public void languageReloadRefreshesNamesWithoutChangingTheServerDirectory() throws Exception {
        if (delegated("languageReloadRefreshesNamesWithoutChangingTheServerDirectory")) return;
        FilterBookSearch search = search();
        search.query("陨石");
        assertEquals(2, search.count());
        Thread.sleep(2); // StringTranslate's public resource version is a millisecond timestamp.
        language("en_US");
        assertEquals(0, search.count());
        search.query("SKY STONE");
        assertEquals(2, search.count());
        assertEquals(
            "Sky Stone Chest",
            search.result(0)
                .name());
    }

    @Test
    public void duplicateNamesRemainSeparateAndPagingPreservesServerIndices() throws Exception {
        if (delegated("duplicateNamesRemainSeparateAndPagingPreservesServerIndices")) return;
        FilterBookSearch search = new FilterBookSearch();
        List<FilterBookSearch.Entry> entries = new ArrayList<>();
        for (int index = 0; index < 8; index++) entries.add(entry("pack:ritual/" + index, 0));
        search.directory(entries);
        search.query("陨石箱子");
        assertEquals(8, search.count());
        assertEquals(2, search.pages());
        search.move(1);
        assertEquals(
            6,
            search.result(0)
                .index());
        assertEquals(
            7,
            search.result(1)
                .index());
        assertNull(search.result(2));
        search.move(1);
        assertEquals(1, search.page());
        search.query("ritual/7");
        assertEquals(0, search.page());
        assertEquals(
            7,
            search.result(0)
                .index());
        search.query(" ");
        assertEquals(8, search.count());
        assertNull(search.result(-1));
        assertNull(search.result(FilterBookSearch.ROWS));
        search.directory(List.of());
        assertEquals(0, search.count());
        assertNull(search.result(0));
    }

    @Test
    public void registrySearchIgnoresCaseAndFullWidthFormsEvenInTurkishLocale() throws Exception {
        if (delegated("registrySearchIgnoresCaseAndFullWidthFormsEvenInTurkishLocale")) return;
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            FilterBookSearch search = search();
            search.query(" ＡＰＰＬＩＥＤＥＮＥＲＧＩＳＴＩＣＳ２ ");
            assertEquals(2, search.count());
            search.query("BLOCKSKYCHEST:1");
            assertEquals(1, search.count());
            assertEquals(
                1,
                search.result(0)
                    .index());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void headersCopyItemStacksAndRejectBookDataOrInvalidQuantities() throws Exception {
        if (delegated("headersCopyItemStacksAndRejectBookDataOrInvalidQuantities")) return;
        ItemStack original = new ItemStack(chest, 1, 1);
        FilterBookSearch.Entry entry = new FilterBookSearch.Entry("pack:block", "ae:chest:1", original, 3);
        original.setItemDamage(0);
        ItemStack illustration = entry.focus();
        illustration.stackSize = 64;
        illustration.setItemDamage(0);
        assertEquals(1, entry.focus().stackSize);
        assertEquals(
            1,
            entry.focus()
                .getItemDamage());
        ItemStack nbt = new ItemStack(chest, 1, 1);
        nbt.setTagCompound(new NBTTagCompound());
        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterBookSearch.Entry("pack:bad", "ae:chest:1", nbt, 3));
        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterBookSearch.Entry("pack:bad", "ae:chest:1", new ItemStack(chest, 2, 1), 3));
    }

    @Test
    public void oversizedQueriesDirectoriesAndDuplicateRitualIdsAreRejected() throws Exception {
        if (delegated("oversizedQueriesDirectoriesAndDuplicateRitualIdsAreRejected")) return;
        FilterBookSearch search = search();
        assertThrows(IllegalArgumentException.class, () -> search.query("中".repeat(FilterBookSearch.MAX_QUERY + 1)));
        search.query("中".repeat(FilterBookSearch.MAX_QUERY));
        assertEquals(0, search.count());
        assertThrows(
            IllegalArgumentException.class,
            () -> search.directory(List.of(entry("pack:same", 0), entry("pack:same", 1))));
        List<FilterBookSearch.Entry> maximum = new ArrayList<>();
        for (int index = 0; index < FilterBookSearch.MAX_RITUALS; index++) maximum.add(entry("pack:" + index, 0));
        search.directory(maximum);
        search.query("");
        assertEquals(FilterBookSearch.MAX_RITUALS, search.count());
        maximum.add(entry("pack:overflow", 0));
        assertThrows(IllegalArgumentException.class, () -> search.directory(maximum));
    }

    @Test
    public void malformedDirectoryPacketsAreRejectedBeforeReplacingResults() throws Exception {
        if (delegated("malformedDirectoryPacketsAreRejectedBeforeReplacingResults")) return;
        for (int count : new int[] { -1, FilterBookSearch.MAX_RITUALS + 1 }) {
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            packet.writeInt(count);
            assertThrows(IOException.class, () -> FilterBookSearch.readDirectory(packet));
        }
        PacketBuffer duplicate = new PacketBuffer(Unpooled.buffer());
        duplicate.writeInt(2);
        for (int index = 0; index < 2; index++) {
            duplicate.writeStringToBuffer("pack:duplicate");
            duplicate.writeStringToBuffer("ae:chest:1");
            duplicate.writeItemStackToBuffer(new ItemStack(chest, 1, 1));
            duplicate.writeInt(3);
        }
        assertThrows(IOException.class, () -> FilterBookSearch.readDirectory(duplicate));
    }
}
