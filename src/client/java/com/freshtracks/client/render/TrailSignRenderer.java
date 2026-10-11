package com.freshtracks.client.render;

import com.freshtracks.resort.ResortSignBlock;
import com.freshtracks.resort.TrailSignBlock;
import com.freshtracks.resort.TrailSignBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The run name on a trail sign, to the right of the difficulty symbol (which is part of the block's texture).
 * Long names wrap onto a second line and shrink to fit the board.
 */
public class TrailSignRenderer implements BlockEntityRenderer<TrailSignBlockEntity, TrailSignRenderer.State> {
	/** Text area on the board, in model pixels across the front (the symbol takes the other end). */
	private static final float TEXT_LEFT_PX = 0.6f;
	private static final float TEXT_RIGHT_PX = 9.6f;
	private static final float BASE_SCALE = 1f / 72f;
	private final Font font;

	public static class State extends BlockEntityRenderState {
		String name = "";
		Direction facing = Direction.NORTH;
		boolean wall;
	}

	public TrailSignRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TrailSignBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		state.name = be.name();
		if (be.getBlockState().getBlock() instanceof TrailSignBlock) {
			state.facing = be.getBlockState().getValue(TrailSignBlock.FACING);
			state.wall = be.getBlockState().getValue(ResortSignBlock.WALL);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.name.isEmpty()) {
			return;
		}
		float maxWidth = (TEXT_RIGHT_PX - TEXT_LEFT_PX) / 16f;
		List<String> lines = layout(state.name, maxWidth);
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, this.font.width(line));
		}
		float scale = Math.min(BASE_SCALE, maxWidth / Math.max(1, widest));
		float frontZ = (state.wall ? 14.5f : 7f) / 16f - 0.5f;
		float centreY = (state.wall ? 8f : 11f) / 16f;
		float centreX = (TEXT_LEFT_PX + TEXT_RIGHT_PX) / 2f / 16f - 0.5f;

		poseStack.pushPose();
		poseStack.translate(0.5f, 0f, 0.5f);
		poseStack.rotateDegrees(Axis.YP, -modelRotation(state.facing));
		// turn round so the text faces out of the front (-Z in the model) and reads left to right for the viewer
		poseStack.rotateDegrees(Axis.YP, 180f);
		poseStack.translate(-centreX, centreY, -frontZ + 0.004f);
		poseStack.scale(scale, -scale, scale);
		float lineHeight = 9f;
		float top = -lines.size() * lineHeight / 2f + 0.5f;
		for (int i = 0; i < lines.size(); i++) {
			FormattedCharSequence text = Component.literal(lines.get(i)).getVisualOrderText();
			float x = -this.font.width(text) / 2f;
			collector.submitText(poseStack, x, top + i * lineHeight, text, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, 0xFFF4F1EA, 0, 0);
		}
		poseStack.popPose();
	}

	/** One line if it fits at full size, else the two-line split with the shortest longest line. */
	private List<String> layout(String name, float maxWidth) {
		List<String> one = List.of(name);
		if (this.font.width(name) * BASE_SCALE <= maxWidth || !name.contains(" ")) {
			return one;
		}
		String[] words = name.split(" ");
		List<String> best = one;
		int bestWidth = Integer.MAX_VALUE;
		for (int cut = 1; cut < words.length; cut++) {
			String a = String.join(" ", java.util.Arrays.copyOfRange(words, 0, cut));
			String b = String.join(" ", java.util.Arrays.copyOfRange(words, cut, words.length));
			int width = Math.max(this.font.width(a), this.font.width(b));
			if (width < bestWidth) {
				bestWidth = width;
				best = new ArrayList<>(List.of(a, b));
			}
		}
		return best;
	}

	/** The blockstate's y rotation for this facing (models are drawn facing north). */
	private static float modelRotation(Direction facing) {
		return switch (facing) {
			case EAST -> 90f;
			case SOUTH -> 180f;
			case WEST -> 270f;
			default -> 0f;
		};
	}
}
