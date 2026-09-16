package justfatlard.cloud_kingdoms.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;

/**
 * The pictures for the readme and the mod page: a citadel standing in the cloud layer, and the
 * same one from the ground, which is how anybody meets it first.
 *
 * <p>Built with the mod's own /cloudkingdom, which runs the worldgen path rather than a copy of
 * it, in a generated world because a kingdom over a flat one has nothing underneath it to be
 * above. Run it under xvfb-run; the frames land in build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	/** Where the mod puts them, and so where the command has to be standing. */
	private static final int CLOUD_LAYER = 192;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(settings -> {
					settings.setWorldType(settings.getNormalPresetList().get(0));
					settings.setSeed("cloud kingdoms");
					settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				})
				.create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			context.getInput().pressKey(options -> options.keyToggleGui);
			// A kingdom is a hundred blocks across and sits alone in the sky: at the default view
			// distance most of it is never meshed, and the picture comes out as floating fragments.
			context.runOnClient(client -> client.options.renderDistance().set(16));
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("weather clear");
			server.runCommand("time set noon");
			server.runCommand("gamemode spectator @a");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX();
			int z = origin.getZ();

			// The command builds where the source stands, so the source goes up to the layer first.
			server.runCommand("tp @a %d %d %d".formatted(x, CLOUD_LAYER, z));
			context.waitTicks(20);
			server.runCommand("cloudkingdom citadel 20260916");
			context.waitTicks(200);

			look(server, x + 34.0, CLOUD_LAYER + 12.0, z + 34.0, x, CLOUD_LAYER + 3.0, z);
			context.waitTicks(160);
			shoot(context, "citadel");

			// A second shot from inside the kingdom is not worth taking here: the command places a
			// hundred thousand blocks into chunks the client already has, and the section updates
			// arrive in pieces, so the picture comes out as fragments in the air. Worldgen places
			// a kingdom before its chunks are ever sent, so a player never sees that.
		}
	}

	/**
	 * Stand the camera at one place and point it at another. The camera's y is the feet, so it
	 * looks from 1.62 above where it stands.
	 */
	private void look(TestServerContext server, double x, double y, double z,
			double atX, double atY, double atZ) {
		double dx = atX - x;
		double dy = atY - (y + 1.62);
		double dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
