package justfatlard.cloud_kingdoms.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the giant the behaviour it has never had.
 *
 * <p>Vanilla registers {@code minecraft:giant} with a model, a hundred hearts, fifty attack damage
 * and no goals whatsoever. It cannot see a player, cannot walk and cannot swing; it is a statue
 * that happens to be alive. A citadel wanted a colossus guarding it, and standing scenery is not
 * that, so here it gets eyes and legs.
 *
 * <p><b>This applies to every giant, not only the ones this mod places.</b> That is deliberate:
 * goals added at spawn time would not survive the chunk unloading, because a reloaded entity is
 * rebuilt from NBT through the constructor and gets whatever goals the constructor installs. There
 * is no way to give one giant durable AI without giving all of them AI. Vanilla spawns none, so in
 * practice this reaches this mod's citadels and anything a player summons by hand.
 *
 * <p>The speed is turned down at the same time, and for the same reason the goals go in: vanilla's
 * base movement speed for a giant is 0.5, five times a walking player. Left alone, a mob that fast
 * hitting for fifty is not a fight, it is a cutscene. At 0.24 it moves like the zombie it is drawn
 * as, and outrunning it becomes a real option.
 */
@Mixin(Giant.class)
public abstract class GiantAiMixin extends Monster {

	protected GiantAiMixin(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void cloudkingdoms$installGoals(EntityType<? extends Giant> type, Level level, CallbackInfo ci) {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
		this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));

		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.setBaseValue(0.24D);
	}
}
