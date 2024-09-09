package org.haruka.scanner

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkPos

object EntityScanner : ModInitializer {

	override fun onInitialize() {
		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			dispatcher.register(
				CommandManager.literal("scan")
					.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 5))
						.executes { context -> scanEntitiesInChunks(context, IntegerArgumentType.getInteger(context, "radius")) })
					.executes { context -> scanEntitiesInChunks(context, 1) }
			)
		}
	}

	private fun scanEntitiesInChunks(context: CommandContext<ServerCommandSource>, radius: Int): Int {
		val player = context.source.player
		if (player != null) {
			val world = player.world
			val playerChunkPos = ChunkPos(player.blockPos)

			val entityCountMap: MutableMap<String, Int> = mutableMapOf()

			for (chunkXOffset in -radius..radius) {
				for (chunkZOffset in -radius..radius) {
					val chunkPos = ChunkPos(playerChunkPos.x + chunkXOffset, playerChunkPos.z + chunkZOffset)

					val minX = chunkPos.startX.toDouble()
					val minY = world.bottomY.toDouble()
					val minZ = chunkPos.startZ.toDouble()
					val maxX = chunkPos.endX.toDouble() + 1
					val maxY = world.topY.toDouble()
					val maxZ = chunkPos.endZ.toDouble() + 1

					val box = Box(minX, minY, minZ, maxX, maxY, maxZ)

					val entities = world.getOtherEntities(player, box)

					for (entity in entities) {
						val entityName = entity.type.toString()
						entityCountMap[entityName] = entityCountMap.getOrDefault(entityName, 0) + 1
					}
				}
			}

			val output = StringBuilder("Entity count in your current and nearby chunks (radius: $radius):\n")

			for ((entityName, count) in entityCountMap) {
				output.append("$entityName: $count\n")
			}

			player.sendMessage(Text.literal(output.toString()), false)
		}
		return 1
	}
}
