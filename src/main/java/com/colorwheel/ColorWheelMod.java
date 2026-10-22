package com.colorwheel;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.Commands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class ColorWheelMod implements ModInitializer {
	public static final String MOD_ID = "colorwheel";

	@Override
	public void onInitialize() {
		Config.load();

		ServerLifecycleEvents.SERVER_STARTED.register(Game::onServerStart);
		ServerLifecycleEvents.SERVER_STOPPING.register(Game::onServerStop);
		ServerTickEvents.END_SERVER_TICK.register(Game::tick);

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			PlayerBlockHistory.capture(world, pos);
			return true;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClientSide() && player.getItemInHand(hand).getItem() instanceof BlockItem) {
				var pos = hit.getBlockPos();
				PlayerBlockHistory.capture(world, pos);
				PlayerBlockHistory.capture(world, pos.relative(hit.getDirection()));
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer) {
				Game.onPlayerDeath();
			} else if (source.getEntity() instanceof ServerPlayer) {
				Game.onKill(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("colorwheel")
				.then(Commands.literal("start").executes(ctx -> {
					Game.requestStart();
					ctx.getSource().sendSuccess(() -> Component.literal("Color Wheel démarré."), false);
					return 1;
				}))
				.then(Commands.literal("stop").executes(ctx -> {
					Game.requestStop();
					ctx.getSource().sendSuccess(() -> Component.literal("Color Wheel arrêté."), false);
					return 1;
				}))
				.then(Commands.literal("reset").executes(ctx -> {
					Game.requestReset();
					ctx.getSource().sendSuccess(() -> Component.literal("Restauration du monde..."), false);
					return 1;
				}))
				.then(Commands.literal("skip").executes(ctx -> {
					Game.requestSkipObjective();
					ctx.getSource().sendSuccess(() -> Component.literal("Objectif suivant."), false);
					return 1;
				}))
				.then(Commands.literal("mode")
					.then(Commands.literal("wheel").executes(ctx -> {
						Config.blockMode = false;
						Config.save();
						ctx.getSource().sendSuccess(() -> Component.literal("Mode : Roue"), false);
						return 1;
					}))
					.then(Commands.literal("block").executes(ctx -> {
						Config.blockMode = true;
						Config.save();
						ctx.getSource().sendSuccess(() -> Component.literal("Mode : Bloc"), false);
						return 1;
					})))
				.then(Commands.literal("radius")
					.then(Commands.argument("chunks", IntegerArgumentType.integer(Config.MIN_RADIUS_CHUNKS, Config.MAX_RADIUS_CHUNKS))
						.executes(ctx -> {
							Config.radiusChunks = IntegerArgumentType.getInteger(ctx, "chunks");
							Config.save();
							ctx.getSource().sendSuccess(() -> Component.literal("Rayon : " + Config.radiusChunks + " chunks (" + (Config.radiusChunks * 16) + " blocs)"), false);
							return 1;
						})))
				.then(Commands.literal("interval")
					.then(Commands.argument("secondes", IntegerArgumentType.integer(Config.MIN_INTERVAL, Config.MAX_INTERVAL))
						.executes(ctx -> {
							Config.intervalSeconds = IntegerArgumentType.getInteger(ctx, "secondes");
							Config.save();
							ctx.getSource().sendSuccess(() -> Component.literal("Intervalle : " + Config.intervalSeconds + " s"), false);
							return 1;
						}))));
		});
	}
}
