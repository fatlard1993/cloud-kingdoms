package justfatlard.cloud_kingdoms.mixin;

import justfatlard.cloud_kingdoms.entity.GoldenGoose;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes a golden goose lay gold instead of eggs.
 *
 * <p><b>It works by getting there first.</b> Vanilla lays an egg inside {@code aiStep} when
 * {@code --eggTime} reaches zero. Rather than try to intercept that drop, this runs at the head of
 * the same method, and when a goose's timer is one tick from firing it drops the nugget itself and
 * winds the timer back up. Vanilla then decrements a number that is nowhere near zero and lays
 * nothing. No egg is ever created and cancelled; the branch simply never becomes true.
 *
 * <p>That means this has to mirror vanilla's own guards - alive, grown, not a jockey's mount -
 * because it is standing in front of them rather than behind them. Miss one and a baby chicken
 * strapped to a zombie starts producing gold.
 *
 * <p>Every other chicken in the world reaches this method, fails the tag test on the first line, and
 * carries on. It is the cheapest check available and it is the only thing separating a goose from
 * the flock.
 */
@Mixin(Chicken.class)
public abstract class GoldenGooseMixin extends Animal {

	@Shadow
	public int eggTime;

	@Shadow
	public boolean isChickenJockey;

	protected GoldenGooseMixin(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void cloudkingdoms$layGold(CallbackInfo ci) {
		if (!entityTags().contains(GoldenGoose.TAG)) return;
		if (!(level() instanceof ServerLevel serverLevel)) return;
		if (!isAlive() || isBaby() || isChickenJockey) return;
		if (eggTime > 1) return;

		spawnAtLocation(serverLevel, new ItemStack(Items.GOLD_NUGGET, 1 + random.nextInt(3)));
		playSound(SoundEvents.CHICKEN_EGG, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);

		// Vanilla's own interval, so a goose is no faster or slower than the chicken it is.
		eggTime = random.nextInt(6000) + 6000;
	}
}
