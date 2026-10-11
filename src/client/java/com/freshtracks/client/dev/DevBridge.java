package com.freshtracks.client.dev;

import com.freshtracks.FreshTracks;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigMotion;
import com.mojang.blaze3d.platform.NativeImage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development-only remote control so the mod can be play-tested by script: a tiny HTTP server on
 * 127.0.0.1:25599 that can create/load worlds, run commands, hold keys, read the rider's telemetry and take
 * screenshots. It never starts in a normal (non-development) install unless -Dfreshtracks.bridge=true.
 */
public final class DevBridge {
	private static final int PORT = Integer.getInteger("freshtracks.bridgePort", 25599);
	private static final ScriptedKeys KEYS = new ScriptedKeys();

	private DevBridge() {
	}

	public static boolean enabled() {
		// dev runs, or the production test run (gradlew prodClient passes this flag; normal installs never do)
		return FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("freshtracks.devbridge");
	}

	public static void start() {
		if (!enabled()) {
			return;
		}
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0);
			server.createContext("/", DevBridge::handle);
			server.setExecutor(Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "FreshTracks DevBridge");
				t.setDaemon(true);
				return t;
			}));
			server.start();
			// the JDK's HTTP dispatcher thread isn't a daemon: stop it or the game can't exit
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> server.stop(0));
			FreshTracks.LOG.info("Dev bridge listening on 127.0.0.1:{}", PORT);
		} catch (IOException e) {
			FreshTracks.LOG.warn("Dev bridge failed to start", e);
		}
	}

	public static void startTick(Minecraft mc) {
		if (enabled()) {
			KEYS.tick(mc);
		}
	}

	public static void endTick(Minecraft mc) {
		if (enabled()) {
			Telemetry.tick(mc);
		}
	}

	private static void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
		String response;
		int code = 200;
		try {
			response = switch (path) {
				case "/state" -> onMain(DevBridge::state);
				case "/cmd" -> onMain(() -> command(q.getOrDefault("c", "")));
				case "/chat" -> onMain(() -> chat(q.getOrDefault("m", "")));
				case "/keys" -> onMain(() -> KEYS.set(q));
				case "/timeline" -> onMain(() -> KEYS.timeline(q.getOrDefault("spec", "")));
				case "/camera" -> onMain(() -> {
					Minecraft mc = Minecraft.getInstance();
					mc.options.setCameraType(switch (q.getOrDefault("mode", "back")) {
						case "front" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
						case "first" -> net.minecraft.client.CameraType.FIRST_PERSON;
						default -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
					});
					return "{\"ok\":true}";
				});
				case "/look" -> onMain(() -> look(q));
				case "/hud" -> onMain(() -> {
					// hide / show the HUD like F1 (for clean screenshots); ?hidden=true|false sets it
					var hud = Minecraft.getInstance().gui.hud;
					boolean want = Boolean.parseBoolean(q.getOrDefault("hidden", String.valueOf(!hud.isHidden())));
					if (hud.isHidden() != want) {
						hud.toggle();
					}
					return "{\"hidden\":" + hud.isHidden() + "}";
				});
				case "/clean" -> onMain(() -> {
					// clear pop-ups (advancement and tutorial toasts) and the chat, for clean screenshots
					Minecraft mc = Minecraft.getInstance();
					mc.gui.toastManager().clear();
					mc.gui.hud.getChat().clearMessages(false);
					return "{\"ok\":true}";
				});
				case "/screen" -> onMain(DevBridge::screen);
				case "/click" -> onMain(() -> click(q.getOrDefault("text", "")));
				case "/world" -> onMain(() -> world(q));
				case "/shot" -> shot(q.getOrDefault("name", "shot"), Integer.parseInt(q.getOrDefault("scale", "1")));
				case "/telemetry" -> Telemetry.handle(q);
				case "/frames" -> FrameProbe.handle(q);
				case "/use" -> onMain(() -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null || mc.gameMode == null) {
						return "{\"error\":\"no player\"}";
					}
					return "{\"result\":" + json(mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND).toString()) + "}";
				});
				case "/useon" -> onMain(() -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null || mc.gameMode == null) {
						return "{\"error\":\"no player\"}";
					}
					net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(q.get("x")), Integer.parseInt(q.get("y")), Integer.parseInt(q.get("z")));
					net.minecraft.core.Direction face = net.minecraft.core.Direction.byName(q.getOrDefault("face", "up"));
					net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
						net.minecraft.world.phys.Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
					return "{\"result\":" + json(mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit).toString()) + "}";
				});
				case "/interact" -> onMain(() -> {
					// right-click the entity under the crosshair (or the nearest one within 6 blocks)
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null || mc.gameMode == null || mc.level == null) {
						return "{\"error\":\"no player\"}";
					}
					net.minecraft.world.entity.Entity target = mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr ? ehr.getEntity() : null;
					if (target == null) {
						double best = 36;
						for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
							if (e != mc.player && e instanceof net.minecraft.world.entity.LivingEntity && e.distanceToSqr(mc.player) < best) {
								best = e.distanceToSqr(mc.player);
								target = e;
							}
						}
					}
					if (target == null) {
						return "{\"error\":\"nothing to interact with\"}";
					}
					net.minecraft.world.phys.EntityHitResult hit = new net.minecraft.world.phys.EntityHitResult(target, target.getBoundingBox().getCenter());
					return "{\"result\":" + json(mc.gameMode.interact(mc.player, target, hit, net.minecraft.world.InteractionHand.MAIN_HAND).toString())
						+ ",\"entity\":" + json(target.getType().toShortString()) + "}";
				});
				case "/advancements" -> onMain(() -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null) {
						return "{\"error\":\"no player\"}";
					}
					var adv = mc.player.connection.getAdvancements();
					var tab = adv.get(net.minecraft.resources.Identifier.parse(q.getOrDefault("tab", "freshtracks:root")));
					if (tab != null) {
						adv.setSelectedTab(tab, true);
					}
					mc.gui.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(adv));
					return "{\"ok\":true}";
				});
				case "/lan" -> onMain(() -> {
					// open this single-player world to LAN (creative, cheats on) for a second test client
					Minecraft mc = Minecraft.getInstance();
					var server = mc.getSingleplayerServer();
					if (server == null) {
						return "{\"error\":\"not in single player\"}";
					}
					int port = Integer.parseInt(q.getOrDefault("port", "25570"));
					server.setUsesAuthentication(false); // dev accounts can't log in to Mojang's session servers
					boolean ok = server.isPublished() || server.publishServer(net.minecraft.server.MinecraftServer.MultiplayerScope.LAN, true, port);
					return "{\"ok\":" + ok + ",\"port\":" + port + "}";
				});
				case "/connect" -> onMain(() -> {
					Minecraft mc = Minecraft.getInstance();
					String host = q.getOrDefault("host", "localhost") + ":" + q.getOrDefault("port", "25570");
					net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc,
						net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(host),
						new net.minecraft.client.multiplayer.ServerData("Test", host, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
					return "{\"ok\":true}";
				});
				case "/aurora" -> onMain(() -> {
					com.freshtracks.client.weather.Aurora.force(Float.parseFloat(q.getOrDefault("v", "-1")));
					return "{\"ok\":true}";
				});
				case "/reloadres" -> onMain(() -> {
					Minecraft.getInstance().reloadResourcePacks();
					return "{\"ok\":true}";
				});
				case "/close" -> onMain(() -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player != null && mc.gui.screen() != null) {
						mc.player.closeContainer();
					}
					return "{\"ok\":true}";
				});
				case "/slot" -> onMain(() -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null) {
						return "{\"error\":\"no player\"}";
					}
					mc.player.getInventory().setSelectedSlot(Integer.parseInt(q.getOrDefault("n", "0")));
					return "{\"ok\":true}";
				});
				case "/quit" -> onMain(() -> {
					Minecraft.getInstance().stop();
					return "{\"ok\":true}";
				});
				default -> "{\"error\":\"unknown path\"}";
			};
		} catch (Exception e) {
			code = 500;
			response = "{\"error\":" + json(e.toString()) + "}";
		}
		byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private interface MainTask {
		String run() throws Exception;
	}

	private static String onMain(MainTask task) throws Exception {
		CompletableFuture<String> future = new CompletableFuture<>();
		Minecraft.getInstance().execute(() -> {
			try {
				future.complete(task.run());
			} catch (Throwable t) {
				future.completeExceptionally(t);
			}
		});
		return future.get(20, TimeUnit.SECONDS);
	}

	// ----------------------------------------------------------------------------------------- commands

	private static String state() {
		Minecraft mc = Minecraft.getInstance();
		StringBuilder sb = new StringBuilder("{");
		Screen screen = mc.gui.screen();
		sb.append("\"screen\":").append(json(screen == null ? null : screen.getClass().getSimpleName()));
		sb.append(",\"fps\":").append(mc.getFps());
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		if (p != null && level != null) {
			sb.append(",\"time\":").append(level.getGameTime());
			sb.append(",\"dayTime\":").append(level.getOverworldClockTime());
			sb.append(",\"pos\":[").append(f(p.getX())).append(',').append(f(p.getY())).append(',').append(f(p.getZ())).append(']');
			sb.append(",\"yaw\":").append(f(p.getYRot())).append(",\"pitch\":").append(f(p.getXRot()));
			sb.append(",\"health\":").append(f(p.getHealth()));
			sb.append(",\"xp\":").append(p.totalExperience);
			sb.append(",\"biome\":").append(json(level.getBiome(BlockPos.containing(p.position())).unwrapKey().map(k -> k.identifier().toString()).orElse("?")));
			sb.append(",\"raining\":").append(level.isRaining());
			sb.append(",\"vehicle\":").append(json(p.getVehicle() == null ? null : p.getVehicle().getType().toShortString()));
			if (p.getVehicle() instanceof RigEntity rig) {
				RigMotion m = rig.motion;
				sb.append(",\"rig\":{");
				sb.append("\"kind\":").append(json(rig.kind().name()));
				sb.append(",\"speed\":").append(f(m.speed()));
				sb.append(",\"kmh\":").append(f(m.speed3() * 72));
				sb.append(",\"heading\":").append(f(m.heading));
				sb.append(",\"edge\":").append(f(m.edge));
				sb.append(",\"lean\":").append(f(m.lean));
				sb.append(",\"skid\":").append(f(m.skid));
				sb.append(",\"grounded\":").append(m.grounded);
				sb.append(",\"switch\":").append(m.switchStance);
				sb.append(",\"surface\":").append(json(m.surface.name()));
				sb.append(",\"airTicks\":").append(m.tricks.airTicks);
				sb.append(",\"spin\":").append(f(m.tricks.spin));
				sb.append(",\"flip\":").append(f(m.tricks.flip));
				sb.append(",\"runScore\":").append(m.tricks.runScore);
				sb.append('}');
			}
		}
		sb.append(",\"keys\":").append(json(KEYS.describe()));
		return sb.append('}').toString();
	}

	private static String command(String command) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return "{\"error\":\"no player\"}";
		}
		String c = command.startsWith("/") ? command.substring(1) : command;
		p.connection.sendCommand(c);
		return "{\"ok\":true}";
	}

	private static String chat(String message) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return "{\"error\":\"no player\"}";
		}
		p.connection.sendChat(message);
		return "{\"ok\":true}";
	}

	private static String look(Map<String, String> q) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return "{\"error\":\"no player\"}";
		}
		if (q.containsKey("yaw")) {
			float yaw = Float.parseFloat(q.get("yaw"));
			p.setYRot(yaw);
			p.yRotO = yaw;
			p.setYHeadRot(yaw);
		}
		if (q.containsKey("pitch")) {
			float pitch = Float.parseFloat(q.get("pitch"));
			p.setXRot(pitch);
			p.xRotO = pitch;
		}
		return "{\"ok\":true}";
	}

	private static String screen() {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.gui.screen();
		if (screen == null) {
			return "{\"screen\":null}";
		}
		StringBuilder sb = new StringBuilder("{\"screen\":").append(json(screen.getClass().getSimpleName())).append(",\"title\":")
			.append(json(screen.getTitle().getString())).append(",\"widgets\":[");
		boolean first = true;
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget w) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				sb.append(json(w.getMessage().getString() + (w.active ? "" : " (disabled)")));
			}
		}
		return sb.append("]}").toString();
	}

	private static String click(String text) {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen == null) {
			return "{\"error\":\"no screen\"}";
		}
		for (GuiEventListener child : screen.children()) {
			if (child instanceof Button b && b.getMessage().getString().equalsIgnoreCase(text) && b.active) {
				b.onPress(null);
				return "{\"ok\":true}";
			}
			// cycle buttons and the like: match on the start of the label ("Type" for "Type: Gondola")
			if (child instanceof net.minecraft.client.gui.components.AbstractButton b && !(child instanceof Button) && b.active
				&& b.getMessage().getString().toLowerCase(java.util.Locale.ROOT).startsWith(text.toLowerCase(java.util.Locale.ROOT))) {
				b.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
				return "{\"ok\":true,\"now\":" + json(b.getMessage().getString()) + "}";
			}
		}
		return "{\"error\":\"no such button\"}";
	}

	private static String world(Map<String, String> q) throws IOException {
		Minecraft mc = Minecraft.getInstance();
		String action = q.getOrDefault("action", "");
		switch (action) {
			case "create" -> {
				if (mc.level != null) {
					return "{\"error\":\"leave the current world first\"}";
				}
				String name = q.getOrDefault("name", "FT Test");
				long seed = Long.parseLong(q.getOrDefault("seed", "0"));
				String preset = q.getOrDefault("preset", "normal");
				boolean flat = "flat".equals(preset);
				LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
					new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
				WorldOptions options = new WorldOptions(seed, true, false);
				mc.createWorldOpenFlows().createFreshLevel(name, settings, options,
					flat ? registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
						: "normal".equals(preset) ? WorldPresets::createNormalWorldDimensions
						: registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
							.getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.WORLD_PRESET,
								net.minecraft.resources.Identifier.parse(preset))).value().createWorldDimensions(), new TitleScreen());
				return "{\"ok\":true}";
			}
			case "load" -> {
				if (mc.level != null) {
					return "{\"error\":\"leave the current world first\"}";
				}
				mc.createWorldOpenFlows().openWorld(q.getOrDefault("name", "FT Test"), () -> mc.gui.setScreen(new TitleScreen()));
				return "{\"ok\":true}";
			}
			case "leave" -> {
				if (mc.level != null) {
					mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
				}
				return "{\"ok\":true}";
			}
			case "exists" -> {
				return "{\"exists\":" + mc.getLevelSource().levelExists(q.getOrDefault("name", "FT Test")) + "}";
			}
			default -> {
				return "{\"error\":\"actions: create, load, leave, exists\"}";
			}
		}
	}

	private static String shot(String name, int downscale) throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		Minecraft mc = Minecraft.getInstance();
		Path dir = mc.gameDirectory.toPath().resolve("screenshots");
		Files.createDirectories(dir);
		Path file = dir.resolve(name.replaceAll("[^A-Za-z0-9_\\-]", "_") + ".png");
		mc.execute(() -> {
			try {
				Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), Math.max(1, downscale), (NativeImage image) -> {
					try (image) {
						image.writeToFile(file);
						done.complete("{\"file\":" + json(file.toAbsolutePath().toString()) + "}");
					} catch (Exception e) {
						done.completeExceptionally(e);
					}
				});
			} catch (Throwable t) {
				done.completeExceptionally(t);
			}
		});
		return done.get(20, TimeUnit.SECONDS);
	}

	// ----------------------------------------------------------------------------------------- util

	static Map<String, String> query(String raw) {
		Map<String, String> map = new HashMap<>();
		if (raw == null || raw.isEmpty()) {
			return map;
		}
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			map.put(k, v);
		}
		return map;
	}

	static String f(double value) {
		return String.format(Locale.ROOT, "%.4f", value);
	}

	static String json(String s) {
		if (s == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append('"').toString();
	}
}
