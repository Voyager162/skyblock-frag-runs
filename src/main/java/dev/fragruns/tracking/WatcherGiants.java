package dev.fragruns.tracking;

import dev.fragruns.core.Giant;
import dev.fragruns.core.GiantOutfit;
import dev.fragruns.core.Session;
import dev.fragruns.core.Signals;
import dev.fragruns.mixin.TextDisplayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class WatcherGiants {
    private static final Pattern DEAD=Pattern.compile("(?:^|\\s)0(?:/[^\\s❤♥]+)?\\s*[❤♥]");
    private final Session session;
    private final Map<Integer,Body> bodies=new HashMap<>();
    private final Set<Integer> seenDeathLabels=new HashSet<>();
    private long combatUntil;
    private static final class Body {
        final GiantOutfit outfit=new GiantOutfit();
        Giant type;
        boolean named,deathEvaluated;
        long hurtUntil,localHitUntil,otherPlayerHitUntil;
    }
    WatcherGiants(Session session) { this.session=session; }
    private boolean confirmedDeath;
    boolean confirmedDeath() { return confirmedDeath; }
    void reset() { bodies.clear();seenDeathLabels.clear();combatUntil=0;confirmedDeath=false; }
    void combat(long now) { combatUntil=now+2_000; }
    private static boolean isBody(Entity e) {
        return e instanceof net.minecraft.world.entity.monster.Giant || e instanceof Zombie z && z.getScale()>=3.0f;
    }
    private static Giant named(String text) {
        if (text.contains("Handle") || text.contains("Eye") || text.contains("Rock") || text.contains("Bola")) return null;
        return Giant.fromName(text);
    }
    void damaged(Minecraft mc,Entity entity,long now,Entity attacker) {
        if (!isBody(entity)) return;
        var body=bodies.computeIfAbsent(entity.getId(),id->new Body());
        body.hurtUntil=now+1_500;
        if (attacker instanceof Player) {
            body.localHitUntil=attacker==mc.player?body.hurtUntil:0;
            body.otherPlayerHitUntil=attacker==mc.player?0:body.hurtUntil;
        }
        observe(mc,entity,now,false);
    }
    private static boolean personalOrSolo(Minecraft mc,Entity target,Body body,long now) {
        if (mc.player.distanceToSqr(target)>32*32 || body!=null && now<body.otherPlayerHitUntil) return false;
        if (body!=null && now<body.localHitUntil) return true;
        return mc.level.players().stream().noneMatch(p->p!=mc.player && !p.isSpectator() && p.distanceToSqr(target)<32*32);
    }
    void observe(Minecraft mc,Entity e,long now,boolean death) {
        if (e==null) return;
        if (isBody(e)) {
            var living=(LivingEntity)e;
            var body=bodies.computeIfAbsent(e.getId(),id->new Body());
            if (living.hurtTime>0) body.hurtUntil=now+1_500;
            boolean dead=death || living.isDeadOrDying();
            Giant name=e.hasCustomName()?named(Signals.clean(e.getCustomName().getString())):null;
            if (name!=null) { body.type=name; body.named=true; session.observeGiant(name); }
            else if (!body.named) {
                var chest=living.getItemBySlot(EquipmentSlot.CHEST);
                var legs=living.getItemBySlot(EquipmentSlot.LEGS);
                var feet=living.getItemBySlot(EquipmentSlot.FEET);
                var gear=GiantOutfit.Gear.UNKNOWN;
                if (chest.is(Items.DIAMOND_CHESTPLATE) || legs.is(Items.DIAMOND_LEGGINGS)) gear=GiantOutfit.Gear.DIAMOND;
                else if (chest.is(Items.LEATHER_CHESTPLATE) && legs.is(Items.LEATHER_LEGGINGS)) gear=GiantOutfit.Gear.LEATHER;
                else if (chest.isEmpty() && legs.isEmpty() && living.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && feet.is(Items.LEATHER_BOOTS)) gear=GiantOutfit.Gear.BOOTS_ONLY;
                var color=chest.get(DataComponents.DYED_COLOR);
                var type=body.outfit.observe(gear,color==null?-1:color.rgb(),now,dead);
                if (type!=null) { body.type=type; session.observeOutfit(type); }
            }
            if (dead && body.type!=null) {
                session.giantDefeated(body.type);
                if (!body.deathEvaluated) {
                    body.deathEvaluated=true;
                    confirmedDeath|=session.currentGiant()==body.type && personalOrSolo(mc,e,body,now);
                }
            }
            return;
        }
        String text;
        if (e instanceof Display.TextDisplay display) text=((TextDisplayAccessor)display).fragruns$text().getString();
        else if (e instanceof ArmorStand && e.hasCustomName()) text=e.getCustomName().getString();
        else return;
        text=Signals.clean(text);
        Giant type=named(text);
        boolean zero=DEAD.matcher(text).find();
        if (type==null && !(zero && text.contains("Giant"))) return;
        if (type!=null) session.observeGiant(type);
        LivingEntity nearest=null;
        double distance=14*14;
        for (Entity candidate:mc.level.entitiesForRendering()) {
            if (isBody(candidate) && candidate.distanceToSqr(e)<distance) {
                nearest=(LivingEntity)candidate; distance=nearest.distanceToSqr(e);
            }
        }
        if (nearest!=null) {
            var body=bodies.computeIfAbsent(nearest.getId(),id->new Body());
            if (type!=null) { body.named=true; body.type=type; }
            if (zero) observe(mc,nearest,now,true);
        } else if (zero && type!=null) {
            session.giantDefeated(type);
            if (seenDeathLabels.add(e.getId())) confirmedDeath|=session.currentGiant()==type && personalOrSolo(mc,e,null,now);
        }
    }
    void removed(Minecraft mc,Entity entity,long now,boolean bloodOpen) {
        if (entity==null || !isBody(entity)) return;
        Body body=bodies.get(entity.getId());
        // A nearby giant removed during combat can die without sending vanilla's death animation.
        boolean killed=bloodOpen && body!=null && mc.player.distanceToSqr(entity)<32*32
                && (now<body.hurtUntil || now<combatUntil);
        // A generic despawn can support statistics. Travel needs a death signal or a hit attributed to this player.
        boolean previouslyConfirmed=confirmedDeath;
        observe(mc,entity,now,killed);
        if (killed && entity instanceof LivingEntity living && !living.isDeadOrDying() && now>=body.localHitUntil)
            confirmedDeath=previouslyConfirmed;
        bodies.remove(entity.getId());
    }
}
