package com.boardgamegeek.service

import android.content.Context
import android.content.SyncResult
import com.boardgamegeek.R
import com.boardgamegeek.io.BggService
import com.boardgamegeek.model.persister.GamePersister
import com.boardgamegeek.provider.BggContract.Games
import com.boardgamegeek.service.model.GameList
import timber.log.Timber
import java.io.IOException
import java.util.*

abstract class SyncGames(context: Context, service: BggService, syncResult: SyncResult) : SyncTask(context, service, syncResult) {

    protected open val maxFetchCount: Int
        get() = 1

    protected abstract val exitLogMessage: String

    protected open val selection: String?
        get() = null

    override fun execute() {
        Timber.i(getIntroLogMessage(GAMES_PER_FETCH))
        try {
            var numberOfFetches = 0
            do {
                if (isCancelled) break

                if (numberOfFetches > 0) if (wasSleepInterrupted(5000)) return

                numberOfFetches++
                val gameList = getGames(GAMES_PER_FETCH)
                if (gameList.size > 0) {
                    Timber.i("...found ${gameList.size} games to update [${gameList.description}]")
                    var detail = context.resources.getQuantityString(R.plurals.sync_notification_games, gameList.size, gameList.size, gameList.description)
                    if (numberOfFetches > 1) {
                        detail = context.getString(R.string.sync_notification_page_suffix, detail, numberOfFetches)
                    }
                    updateProgressNotification(detail)

                    val call = service.thing(gameList.ids, 1)
                    try {
                        val response = call.execute()
                        if (response.isSuccessful) {
                            val body = response.body()
                            val games = body?.games ?: emptyList()
                            if (games.isNotEmpty()) {
                                val count = GamePersister(context).save(games, detail)
                                syncResult.stats.numUpdates += games.size.toLong()
                                Timber.i("...saved %,d rows for %,d games", count, games.size)
                            } else {
                                Timber.i("...no games returned")
                                break
                            }
                        } else {
                            showError(detail, response.code())
                            syncResult.stats.numIoExceptions++
                            cancel()
                            return
                        }
                    } catch (e: IOException) {
                        showError(detail, e)
                        syncResult.stats.numIoExceptions++
                        break
                    } catch (e: RuntimeException) {
                        val cause = e.cause
                        if (cause is ClassNotFoundException) {
                            val message = cause.message ?: ""
                            if (message.startsWith("Didn't find class \"messagebox error\" on path")) {
                                Timber.i("Invalid list of game IDs: %s", gameList.ids)
                                for (i in 0 until gameList.size) {
                                    val shouldBreak = syncGame(gameList.getId(i), gameList.getName(i))
                                    if (shouldBreak) break
                                }
                            } else {
                                showError(detail, e)
                                syncResult.stats.numParseExceptions++
                                break
                            }
                        } else {
                            showError(detail, e)
                            syncResult.stats.numParseExceptions++
                            break
                        }
                    }
                } else {
                    Timber.i(exitLogMessage)
                    break
                }
            } while (numberOfFetches < maxFetchCount)
        } finally {
            Timber.i("...complete!")
        }
    }

    private fun syncGame(id: Int, gameName: String): Boolean {
        var detail = ""
        val call = service.thing(id, 1)
        try {
            val response = call.execute()
            if (response.isSuccessful) {
                val games = if (response.body() == null) ArrayList(0) else response.body()!!.games
                detail = context.resources.getQuantityString(R.plurals.sync_notification_games, 1, 1, gameName)
                val count = GamePersister(context).save(games, detail)
                syncResult.stats.numUpdates += games.size.toLong()
                Timber.i("...saved %,d rows for %,d games", count, games.size)
            } else {
                showError(detail, response.code())
                syncResult.stats.numIoExceptions++
                cancel()
                return true
            }
        } catch (e: IOException) {
            showError(detail, e)
            syncResult.stats.numIoExceptions++
            return true
        } catch (e: RuntimeException) {
            val cause = e.cause
            if (cause is ClassNotFoundException) {
                val message = cause.message ?: ""
                if (message.startsWith("Didn't find class \"messagebox error\" on path")) {
                    Timber.i("Invalid game $gameName ($id)")
                    showError(detail, e)
                    // otherwise just ignore this error
                } else {
                    showError(detail, e)
                    syncResult.stats.numParseExceptions++
                }
            } else {
                showError(detail, e)
                syncResult.stats.numParseExceptions++
            }
            return false
        }

        return false
    }

    protected abstract fun getIntroLogMessage(gamesPerFetch: Int): String

    private fun getGames(gamesPerFetch: Int): GameList {
        val list = GameList(gamesPerFetch)
        val cursor = context.contentResolver.query(Games.CONTENT_URI,
                arrayOf(Games.GAME_ID, Games.GAME_NAME),
                selection,
                null,
                "games.${Games.UPDATED_LIST} LIMIT $gamesPerFetch")
        cursor?.use { c ->
            while (c.moveToNext()) {
                list.addGame(c.getInt(0), c.getString(1))
            }
        }
        return list
    }

    companion object {
        private const val GAMES_PER_FETCH = 10
    }
}
