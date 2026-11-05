package com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime;

import net.minecraft.nbt.NBTTagCompound;

import com.kyou.imaginaryspacemeteoriteexcavation.output.runtime.PendingItemOutput;

/** Single paid batch OR completed pending. Native controller owns progress and EU, not a second output ledger. */
public final class ExcavationCycle {

    private PendingItemOutput pending = new PendingItemOutput();
    private PaidBatch running;
    private NBTTagCompound invalid;
    private String fault = "";
    private String lastAbort = "";

    public boolean canStart() {
        return invalid == null && fault.isEmpty() && running == null && !pending.blocksStart();
    }

    /** The caller has already validated/allocated batch and successfully charged the PreparedProcess. */
    public void begin(PaidBatch batch) {
        if (batch == null || !canStart()) throw new IllegalStateException("Cycle is already occupied");
        running = batch;
        lastAbort = "";
    }

    /** The native completion callback transfers ownership once; retries never invoke this method. */
    public boolean finish() {
        if (running == null) return false;
        if (!fault.isEmpty() || invalid != null) return false;
        try {
            pending.complete(running.items());
            running = null;
            return true;
        } catch (RuntimeException inconsistent) {
            fault = "Completion capture failed";
            return false;
        }
    }

    /** Native running failure discards unfinished yield only; completed pending remains owned. */
    public void abort(String reason) {
        if (running != null && invalid == null && fault.isEmpty()) {
            running = null;
            lastAbort = reason == null ? "stopped" : reason.substring(0, Math.min(128, reason.length()));
        }
    }

    public PaidBatch running() {
        return running;
    }

    public PendingItemOutput pending() {
        return pending;
    }

    public String lastAbort() {
        return lastAbort;
    }

    public String fault() {
        return invalid != null ? "Invalid saved machine cycle" : !fault.isEmpty() ? fault : pending.fault();
    }

    public NBTTagCompound save() {
        if (invalid != null) return (NBTTagCompound) invalid.copy();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", 1);
        tag.setTag("pending", pending.save());
        if (running != null) tag.setTag("running", running.save());
        tag.setString("fault", fault);
        tag.setString("lastAbort", lastAbort);
        return tag;
    }

    /** Absent legacy state is accepted only for an idle machine. Invalid present state is retained verbatim. */
    public static ExcavationCycle load(NBTTagCompound tag, int progress, int maximum, long nativeEut) {
        ExcavationCycle cycle = new ExcavationCycle();
        try {
            if (tag == null) {
                if (maximum != 0 || progress != 0) throw new IllegalArgumentException("Unowned native progress");
                return cycle;
            }
            if (!tag.hasKey("schema", 3) || tag.getInteger("schema") != 1
                || !tag.hasKey("pending", 10)
                || !tag.hasKey("fault", 8)
                || !tag.hasKey("lastAbort", 8)
                || tag.getString("fault")
                    .length() > 256
                || tag.getString("lastAbort")
                    .length() > 128
                || (tag.hasKey("running") && !tag.hasKey("running", 10))) {
                throw new IllegalArgumentException("Invalid cycle fields");
            }
            cycle.pending = PendingItemOutput.load(tag.getCompoundTag("pending"));
            cycle.fault = tag.getString("fault");
            cycle.lastAbort = tag.getString("lastAbort");
            if (tag.hasKey("running", 10)) {
                cycle.running = PaidBatch.load(tag.getCompoundTag("running"));
                if (cycle.pending.blocksStart() || progress < 0
                    || progress >= cycle.running.duration
                    || maximum != cycle.running.duration
                    || nativeEut != -cycle.running.eut) {
                    throw new IllegalArgumentException("Paid/native progress mismatch");
                }
            } else if (maximum != 0 || progress != 0) {
                throw new IllegalArgumentException("Unowned native progress");
            }
        } catch (RuntimeException bad) {
            cycle.invalid = tag == null ? new NBTTagCompound() : (NBTTagCompound) tag.copy();
            cycle.running = null;
        }
        return cycle;
    }
}
