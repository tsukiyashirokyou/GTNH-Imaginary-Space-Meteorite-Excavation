package com.kyou.imaginaryspacemeteoriteexcavation.gui;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StringTranslate;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.item.FilteringBook;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationWaila;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;

/** Client text from real language resources and bounded display snapshots; no game or GL context. */
public final class PlayerTextTest {

    private static Item chest;
    private static FilteringBook book;

    @BeforeClass
    public static void registerTestFixtures() throws IOException {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        chest = new Item() {

            @Override
            public String getUnlocalizedName(ItemStack stack) {
                return stack.getItemDamage() == 1 ? "tile.appliedenergistics2.BlockSkyChest.Block"
                    : "tile.appliedenergistics2.BlockSkyChest";
            }
        }.setHasSubtypes(true);
        Item.itemRegistry.addObject(30011, "minecraft:isme_ui_chest", chest);
        book = new FilteringBook();
        Item.itemRegistry.addObject(30012, "minecraft:isme_ui_book", book);
        language("zh_CN");
    }

    private boolean delegated(String method) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        HeadlessForgeFixture.run(getClass().getName(), method);
        return true;
    }

    private static void language(String language) throws IOException {
        for (String mod : new String[] { "appliedenergistics2", "imaginaryspacemeteoriteexcavation" }) {
            try (InputStream input = PlayerTextTest.class
                .getResourceAsStream("/assets/" + mod + "/lang/" + language + ".lang")) {
                assertNotNull(input);
                StringTranslate.inject(input);
            }
        }
    }

    @Test
    public void localizedPresentationUsesOneIsolatedForgeSession() throws Exception {
        if (delegated("localizedPresentationUsesOneIsolatedForgeSession")) return;
        machineFocusUsesNativeChineseAndEnglishNamesRatherThanRecipeIds();
        language("zh_CN");
        boundedOutputPreviewUsesLocalNamesAndPreservesLongQuantities();
        missingDisplayItemsDoNotExposeRegistryOrFallbackDiagnostics();
        failureCategoriesRemainVisibleWithoutLeakingRawExceptions();
        bookTooltipShowsStateAndSingleBookRequirementWithoutEditingNbt();
    }

    private void machineFocusUsesNativeChineseAndEnglishNamesRatherThanRecipeIds() throws Exception {
        assertEquals("陨石块箱子", PlayerText.focusName("minecraft:isme_ui_chest:1"));
        assertEquals("无配方", PlayerText.focusName("imaginaryspacemeteoriteexcavation:meteor/skystonemeteor"));
        assertEquals("无配方", PlayerText.focusName("minecraft:missing:0"));
        assertEquals("无配方", PlayerText.focusName("minecraft:isme_ui_chest:invalid"));
        language("en_US");
        assertEquals("Sky Stone Block Chest", PlayerText.focusName("minecraft:isme_ui_chest:1"));
    }

    private void boundedOutputPreviewUsesLocalNamesAndPreservesLongQuantities() throws Exception {
        Map<ItemIdentity, Long> outputs = new LinkedHashMap<>();
        long large = Integer.MAX_VALUE + 12L;
        for (int i = 0; i < 5; i++) outputs.put(new ItemIdentity(new ItemStack(chest, 1, i)), large + i);
        NBTTagCompound snapshot = ExcavationWaila.preview(outputs);
        List<String> lines = PlayerText.outputLines(snapshot);
        assertEquals(4, lines.size());
        assertTrue(
            lines.get(0)
                .startsWith("陨石箱子 × "));
        assertTrue(
            lines.get(1)
                .startsWith("陨石块箱子 × "));
        assertTrue(
            lines.get(0)
                .contains("2,147,483,659"));
        assertEquals("另有 2 种物品", lines.get(3));
        assertFalse(
            lines.toString()
                .contains("minecraft:"));
        assertEquals(
            large,
            snapshot.getTagList("items", 10)
                .getCompoundTagAt(0)
                .getLong("amount"));
    }

    private void missingDisplayItemsDoNotExposeRegistryOrFallbackDiagnostics() throws Exception {
        Map<ItemIdentity, Long> outputs = new LinkedHashMap<>();
        outputs.put(new ItemIdentity(new ItemStack(chest)), 15L);
        NBTTagCompound snapshot = ExcavationWaila.preview(outputs);
        snapshot.getTagList("items", 10)
            .getCompoundTagAt(0)
            .setString("registry", "removed:internal_item");
        snapshot.getTagList("items", 10)
            .getCompoundTagAt(0)
            .setString("fallback", "developer-class-and-metadata");
        assertEquals(List.of("未知物品 × 15"), PlayerText.outputLines(snapshot));
        assertEquals(List.of("无产物"), PlayerText.outputLines(new NBTTagCompound()));
        assertEquals(List.of("无产物"), PlayerText.outputLines(null));
    }

    private void failureCategoriesRemainVisibleWithoutLeakingRawExceptions() throws Exception {
        String raw = "java.lang.IllegalStateException at mod.internal.NativeAdapter";
        assertEquals("加工记录或输出异常，机器已停止", PlayerText.detail("fault", raw));
        assertEquals("输入仓不兼容", PlayerText.detail("unsupported_input", raw));
        assertEquals("过滤书配置无效", PlayerText.detail("unsupported_input", "Invalid filtering book: stale revision"));
        assertEquals("输出不可用或配置不兼容", PlayerText.detail("output_wait", raw));
        assertEquals("", PlayerText.detail("running", raw));
        assertEquals("", PlayerText.detail("output_wait", ""));
    }

    private void bookTooltipShowsStateAndSingleBookRequirementWithoutEditingNbt() throws Exception {
        ItemStack single = new ItemStack(book);
        List<String> tip = new ArrayList<>();
        book.addInformation(single, null, tip, false);
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(String.join("\n", tip));
        assertTrue(text.contains("默认：全部保留"));
        assertTrue(text.contains("被禁止的物品直接销毁"));
        assertTrue(text.contains("右键配置"));
        assertFalse(text.contains("仅单本可编辑"));
        assertFalse(text.contains("请"));
        assertFalse(single.hasTagCompound());
        single.stackSize = 2;
        tip.clear();
        book.addInformation(single, null, tip, false);
        assertTrue(
            String.join("\n", tip)
                .contains("仅单本可编辑"));
        assertFalse(single.hasTagCompound());
    }

    @Test
    public void progressIndicatorClampsInvalidOrOverflowingDisplayValues() {
        assertEquals(0, PlayerText.progress(12, 0), 0);
        assertEquals(0, PlayerText.progress(-1, 10), 0);
        assertEquals(0.5, PlayerText.progress(5, 10), 0);
        assertEquals(1, PlayerText.progress(Integer.MAX_VALUE, 1), 0);
        assertEquals(1, PlayerText.progress(Integer.MAX_VALUE, Integer.MAX_VALUE), 0);
        assertEquals(0, PlayerText.progress(Integer.MIN_VALUE, Integer.MAX_VALUE), 0);
        assertEquals(29, PlayerText.percent(29, 100));
        assertEquals(100, PlayerText.percent(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, PlayerText.percent(-1, 10));
    }
}
