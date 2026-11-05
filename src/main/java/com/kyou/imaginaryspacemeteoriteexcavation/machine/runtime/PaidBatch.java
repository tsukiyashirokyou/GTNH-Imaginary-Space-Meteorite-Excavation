package com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.output.runtime.PendingItemOutput;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.PreparedProcess;

/** Frozen output plan, allocated/validated before charging, owned by the cycle only after successful start. */
public final class PaidBatch {

    public final String recipe;
    public final int batch, duration, lp, radius;
    public final long eut, drilling, kept;
    private final PendingItemOutput outputs;

    public PaidBatch(String recipe, int batch, int duration, long eut, int lp, long drilling,
        Map<ItemIdentity, Long> retained) {
        this(recipe, batch, duration, eut, lp, drilling, capture(retained), -1);
    }

    public PaidBatch(String recipe, int batch, int duration, long eut, int lp, long drilling,
        Map<ItemIdentity, Long> retained, int radius) {
        this(recipe, batch, duration, eut, lp, drilling, capture(retained), radius);
    }

    private PaidBatch(String recipe, int batch, int duration, long eut, int lp, long drilling,
        PendingItemOutput outputs, int radius) {
        if (recipe == null || recipe.isEmpty()
            || recipe.length() > 256
            || batch <= 0
            || duration <= 0
            || eut <= 0
            || eut > Long.MAX_VALUE / 10
            || lp < 0
            || drilling < 0
            || radius < -1
            || !outputs.fault()
                .isEmpty()) {
            throw new IllegalArgumentException("Invalid paid batch");
        }
        long total = 0;
        for (long count : outputs.snapshot()
            .values()) total = Math.addExact(total, count);
        if (total > drilling) throw new IllegalArgumentException("Retained yield exceeds pre-filter yield");
        this.recipe = recipe;
        this.batch = batch;
        this.duration = duration;
        this.eut = eut;
        this.lp = lp;
        this.drilling = drilling;
        this.radius = radius;
        kept = total;
        this.outputs = outputs;
    }

    public static PaidBatch prepare(PreparedProcess<ItemIdentity> process) {
        return new PaidBatch(
            process.recipe()
                .id(),
            process.schedule().batch,
            process.schedule().ticks,
            process.schedule().eut,
            process.lpCost(),
            process.prefilterYield()
                .prefilterTotal(),
            process.retainedItems(),
            process.recipe()
                .recipe()
                .radius());
    }

    private static PendingItemOutput capture(Map<ItemIdentity, Long> items) {
        PendingItemOutput snapshot = new PendingItemOutput();
        snapshot.complete(items);
        return snapshot;
    }

    public Map<ItemIdentity, Long> items() {
        return outputs.snapshot();
    }

    public NBTTagCompound save() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("recipe", recipe);
        tag.setInteger("batch", batch);
        tag.setInteger("duration", duration);
        tag.setLong("eut", eut);
        tag.setInteger("lp", lp);
        tag.setLong("drilling", drilling);
        if (radius >= 0) tag.setInteger("radius", radius);
        tag.setTag("outputs", outputs.save());
        return tag;
    }

    static PaidBatch load(NBTTagCompound tag) {
        if (!tag.hasKey("recipe", 8) || !tag.hasKey("batch", 3)
            || !tag.hasKey("duration", 3)
            || !tag.hasKey("eut", 4)
            || !tag.hasKey("lp", 3)
            || !tag.hasKey("drilling", 4)
            || !tag.hasKey("outputs", 10)) throw new IllegalArgumentException("Invalid paid batch fields");
        return new PaidBatch(
            tag.getString("recipe"),
            tag.getInteger("batch"),
            tag.getInteger("duration"),
            tag.getLong("eut"),
            tag.getInteger("lp"),
            tag.getLong("drilling"),
            PendingItemOutput.load(tag.getCompoundTag("outputs")),
            tag.hasKey("radius", 3) && tag.getInteger("radius") >= 0 ? tag.getInteger("radius") : -1);
    }
}
