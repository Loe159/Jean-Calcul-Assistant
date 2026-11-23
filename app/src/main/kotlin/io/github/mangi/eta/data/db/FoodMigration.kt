package io.github.mangi.eta.data.db

import androidx.room.migration.Migration

internal val FOOD_MIGRATION_26_27 = Migration(26, 27) { db ->
    db.execSQL("""
        CREATE TABLE food_recipes (
            owner TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL,
            json TEXT NOT NULL, cachedAt INTEGER NOT NULL, PRIMARY KEY(owner, id, version)
        )
    """)
    db.execSQL("""
        CREATE TABLE food_recipe_library (
            owner TEXT NOT NULL, recipeId TEXT NOT NULL, json TEXT NOT NULL,
            cachedAt INTEGER NOT NULL, PRIMARY KEY(owner, recipeId)
        )
    """)
    db.execSQL("""
        CREATE TABLE food_recipe_index (
            owner TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, cachedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("""
        CREATE TABLE food_availability (
            owner TEXT NOT NULL, recipeId TEXT NOT NULL, version INTEGER NOT NULL, servings INTEGER NOT NULL,
            json TEXT NOT NULL, stockAsOf TEXT NOT NULL, cachedAt INTEGER NOT NULL,
            PRIMARY KEY(owner, recipeId, version, servings)
        )
    """)
    db.execSQL("""
        CREATE TABLE food_pantry (
            owner TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, fetchedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("""
        CREATE TABLE cooking_sessions (
            id TEXT NOT NULL PRIMARY KEY, owner TEXT, recipeSnapshotJson TEXT NOT NULL,
            servings INTEGER NOT NULL, currentStepId TEXT, completedStepIdsJson TEXT NOT NULL,
            skippedStepIdsJson TEXT NOT NULL, overridesJson TEXT NOT NULL, status TEXT NOT NULL,
            startedAt INTEGER NOT NULL, completedAt INTEGER, stockConsumptionStatus TEXT NOT NULL,
            revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("""
        CREATE TABLE cooking_timers (
            id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, stepId TEXT, label TEXT NOT NULL,
            durationSeconds INTEGER NOT NULL, state TEXT NOT NULL, endsAtEpochMs INTEGER,
            remainingMs INTEGER, createdAt INTEGER NOT NULL,
            FOREIGN KEY(sessionId) REFERENCES cooking_sessions(id) ON DELETE CASCADE
        )
    """)
    db.execSQL("CREATE INDEX index_cooking_timers_sessionId ON cooking_timers(sessionId)")
    db.execSQL("""
        CREATE TABLE food_outbox (
            operationId TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, kind TEXT NOT NULL,
            endpoint TEXT NOT NULL, bodyJson TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL,
            lastError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
        )
    """)
    db.execSQL("CREATE UNIQUE INDEX index_food_outbox_sessionId ON food_outbox(sessionId)")
    db.execSQL("""
        CREATE TABLE food_brief_cards (
            owner TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, cachedAt INTEGER NOT NULL,
            validUntilEpochMs INTEGER NOT NULL
        )
    """)
}
