package dev.skycore.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.skycore.SkyCore;
import dev.skycore.api.event.TeamMilestoneGrantedEvent;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;

public final class TeamProgressData extends SavedData {
    private static final Factory<TeamProgressData> FACTORY = new Factory<>(TeamProgressData::new, TeamProgressData::load, null);
    private final Map<UUID, EnumSet<Milestone>> teams = new HashMap<>();
    private final Map<UUID, EnumSet<JourneyMilestone>> journey = new HashMap<>();

    public static TeamProgressData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Sky Core state requires the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skycore_teams");
    }

    public boolean has(UUID team, String id) {
        return Milestone.find(id).map(m -> teams.getOrDefault(team, EnumSet.noneOf(Milestone.class)).contains(m))
                .orElseGet(() -> JourneyMilestone.find(id).map(m -> journey.getOrDefault(team, EnumSet.noneOf(JourneyMilestone.class)).contains(m)).orElse(false));
    }

    public boolean grant(UUID team, Milestone milestone) {
        if (team == null || has(team, milestone.id())) return false;
        if (milestone.previous().filter(m -> !has(team, m.id())).isPresent()) return false;
        teams.computeIfAbsent(team, key -> EnumSet.noneOf(Milestone.class)).add(milestone);
        setDirty();
        try {
            NeoForge.EVENT_BUS.post(new TeamMilestoneGrantedEvent(team.toString(), milestone.id(), unlockedChapter(team)));
        } catch (RuntimeException exception) {
            SkyCore.LOGGER.error("A milestone listener failed after committing {} for {}", milestone.id(), team, exception);
        }
        return true;
    }

    public void processingCommitted(UUID team, String recipe) {
        Milestone.forRecipe(recipe).filter(milestone -> milestone != Milestone.FIRST_TREE).ifPresent(milestone -> grant(team, milestone));
    }

    public void germinationCommitted(UUID team, String recipe) {
        if (recipe.equals("skycore:ch01/first_tree")) grant(team, Milestone.FIRST_TREE);
    }

    public boolean grantJourney(UUID team, JourneyMilestone milestone) {
        if (team == null || has(team, milestone.id()) || !has(team, Milestone.FIRST_TREE.id())
                || milestone.prerequisites().stream().anyMatch(required -> !has(team, required.id()))) return false;
        journey.computeIfAbsent(team, ignored -> EnumSet.noneOf(JourneyMilestone.class)).add(milestone);
        setDirty();
        try { NeoForge.EVENT_BUS.post(new TeamMilestoneGrantedEvent(team.toString(), milestone.id(), unlockedChapter(team))); }
        catch (RuntimeException failure) { SkyCore.LOGGER.error("Journey listener failed after commit {}", milestone.id(), failure); }
        return true;
    }

    public int unlockedChapter(UUID team) {
        int chapter = has(team, Milestone.FIRST_TREE.id()) ? 2 : 1;
        for (JourneyMilestone milestone : journey.getOrDefault(team, EnumSet.noneOf(JourneyMilestone.class))) chapter = Math.max(chapter, milestone.chapter());
        return chapter;
    }

    public String snapshot(UUID team) {
        JsonObject result = new JsonObject();
        result.addProperty("schema_version", 1);
        result.addProperty("team_id", team.toString());
        result.addProperty("unlocked_chapter", unlockedChapter(team));
        JsonArray milestones = new JsonArray();
        for (Milestone milestone : Milestone.values()) if (has(team, milestone.id())) milestones.add(milestone.id());
        for (JourneyMilestone milestone : JourneyMilestone.values()) if (has(team, milestone.id())) milestones.add(milestone.id());
        result.add("milestones", milestones);
        return result.toString();
    }

    public static TeamProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("SchemaVersion") > 1) throw new IllegalStateException("Sky Core data is newer than this JAR");
        TeamProgressData result = new TeamProgressData();
        ListTag list = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            EnumSet<Milestone> progress = EnumSet.noneOf(Milestone.class);
            for (Tag value : entry.getList("Milestones", Tag.TAG_STRING)) Milestone.find(value.getAsString()).ifPresent(progress::add);
            result.teams.put(entry.getUUID("Id"), progress);
            EnumSet<JourneyMilestone> later = EnumSet.noneOf(JourneyMilestone.class);
            for (Tag value : entry.getList("JourneyMilestones", Tag.TAG_STRING)) JourneyMilestone.find(value.getAsString()).ifPresent(later::add);
            result.journey.put(entry.getUUID("Id"), later);
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        ListTag list = new ListTag();
        teams.forEach((id, progress) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            ListTag milestones = new ListTag();
            progress.forEach(m -> milestones.add(StringTag.valueOf(m.id())));
            entry.put("Milestones", milestones);
            ListTag later = new ListTag();
            journey.getOrDefault(id, EnumSet.noneOf(JourneyMilestone.class)).forEach(value -> later.add(StringTag.valueOf(value.id())));
            entry.put("JourneyMilestones", later);
            list.add(entry);
        });
        tag.put("Teams", list);
        return tag;
    }
}
