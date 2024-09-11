package org.haruka.scanner

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.entity.Entity
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
					.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 10))
						.executes { context -> scanEntitiesInChunksAsync(context, IntegerArgumentType.getInteger(context, "radius"), null) })
					.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 10))
						.then(CommandManager.argument("entity", StringArgumentType.string())
							.executes { context -> scanEntitiesInChunksAsync(context, IntegerArgumentType.getInteger(context, "radius"), StringArgumentType.getString(context, "entity")) })
					)
			)

			dispatcher.register(
				CommandManager.literal("delete")
					.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 10))
						.then(CommandManager.argument("entity", StringArgumentType.string())
							.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 100)) // Add count argument
								.executes { context -> deleteEntitiesInChunksAsync(context, IntegerArgumentType.getInteger(context, "radius"), StringArgumentType.getString(context, "entity"), IntegerArgumentType.getInteger(context, "count")) })
							.executes { context -> deleteEntitiesInChunksAsync(context, IntegerArgumentType.getInteger(context, "radius"), StringArgumentType.getString(context, "entity"), null) }) // Handle cases where count is not provided
					)
			)
		}

	}

	// Method to scan and count entities asynchronously (with optional filter for specific entity types)
	private fun scanEntitiesInChunksAsync(context: CommandContext<ServerCommandSource>, radius: Int, filterEntity: String?): Int {
		val player = context.source.player
		if (player != null) {
			val world = player.world
			val playerChunkPos = ChunkPos(player.blockPos)

			runBlocking(Dispatchers.Default) {
				val entityCountMap: MutableMap<String, Int> = mutableMapOf()

				val tasks = (playerChunkPos.x - radius..playerChunkPos.x + radius).flatMap { chunkXOffset ->
					(playerChunkPos.z - radius..playerChunkPos.z + radius).map { chunkZOffset ->
						async {
							val chunkPos = ChunkPos(chunkXOffset, chunkZOffset)
							val box = Box(chunkPos.startX.toDouble(), world.bottomY.toDouble(), chunkPos.startZ.toDouble(), chunkPos.endX.toDouble() + 1, world.topY.toDouble(), chunkPos.endZ.toDouble() + 1)
							val entities = world.getOtherEntities(player, box)

							for (entity in entities) {
								val entityName = entity.type.toString()
								if (filterEntity == null || entityName.contains(filterEntity, ignoreCase = true)) {
									synchronized(entityCountMap) {
										entityCountMap[entityName] = entityCountMap.getOrDefault(entityName, 0) + 1
									}
								}
							}
						}
					}
				}

				tasks.forEach { it.await() }

				val sortedEntityCountList = mergeSort(entityCountMap.toList())

				val output = StringBuilder("Entity count in your current and nearby chunks (radius: $radius), sorted by type:\n")
				for ((entityName, count) in sortedEntityCountList) {
					output.append("$entityName: $count\n")
				}
				player.sendMessage(Text.literal(output.toString()), false)
			}
		}
		return 1
	}

	private fun deleteEntitiesInChunksAsync(context: CommandContext<ServerCommandSource>, radius: Int, entityName: String, maxCount: Int?): Int {
		val player = context.source.player
		if (player != null) {
			val world = player.world
			val playerChunkPos = ChunkPos(player.blockPos)

			runBlocking(Dispatchers.Default) {
				var entitiesKilled = 0 // Counter for the number of entities killed
				val maxKillCount = maxCount ?: Int.MAX_VALUE // Use maxCount if provided, otherwise set to a large number

				val tasks = (playerChunkPos.x - radius..playerChunkPos.x + radius).flatMap { chunkXOffset ->
					(playerChunkPos.z - radius..playerChunkPos.z + radius).map { chunkZOffset ->
						async {
							val chunkPos = ChunkPos(chunkXOffset, chunkZOffset)
							val box = Box(chunkPos.startX.toDouble(), world.bottomY.toDouble(), chunkPos.startZ.toDouble(), chunkPos.endX.toDouble() + 1, world.topY.toDouble(), chunkPos.endZ.toDouble() + 1)
							val entities = world.getOtherEntities(player, box)

							for (entity in entities) {
								if (entitiesKilled >= maxKillCount) {
									return@async
								}

								val name = entity.type.toString()
								if (name.contains(entityName, ignoreCase = true)) {
									entity.remove(Entity.RemovalReason.KILLED)
									synchronized(this) {
										entitiesKilled++
									}
								}
							}
						}
					}
				}

				tasks.forEach { it.await() }
				player.sendMessage(Text.literal("Entities of type $entityName have been deleted. Total killed: $entitiesKilled."), false)
			}
		}
		return 1
	}



	// Merge sort implementation
	private fun mergeSort(list: List<Pair<String, Int>>): List<Pair<String, Int>> {
		if (list.size <= 1) {
			return list
		}
		val middle = list.size / 2
		val left = list.subList(0, middle)
		val right = list.subList(middle, list.size)
		val sortedLeft = mergeSort(left)
		val sortedRight = mergeSort(right)
		return merge(sortedLeft, sortedRight)
	}

	private fun merge(left: List<Pair<String, Int>>, right: List<Pair<String, Int>>): List<Pair<String, Int>> {
		var i = 0
		var j = 0
		val result = mutableListOf<Pair<String, Int>>()
		while (i < left.size && j < right.size) {
			if (left[i].first <= right[j].first) {
				result.add(left[i])
				i++
			} else {
				result.add(right[j])
				j++
			}
		}
		while (i < left.size) {
			result.add(left[i])
			i++
		}
		while (j < right.size) {
			result.add(right[j])
			j++
		}
		return result
	}
}
